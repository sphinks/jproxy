import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.util.Iterator;

/**
 * @author Sphinks
 * 
 */
public class NioServer implements Runnable {
    // хост:порт для прослушивания сервером
    private InetAddress hostAddress;
    private int port;

    // хост:порт удаленного сервера для редиректа
    private InetAddress destinationAddress;
    private int destinationPort;

    // Канал сервера
    private ServerSocketChannel serverChannel;

    // Селектор
    private Selector selector;

    // Производить ли подмену значения поля Host в HTTP запросе с localhost на
    // имя целевого хоста
    private boolean replaceLocalHost;

    private boolean stopServer;

    public NioServer(InetAddress hostAddress, int port,
            InetAddress destinationAddress, int destinationPort,
            boolean replaceLocalHost)
            throws IOException {

        this.hostAddress = hostAddress;
        this.port = port;
        this.destinationAddress = destinationAddress;
        this.destinationPort = destinationPort;
        this.selector = initSelector();
        this.stopServer = false;
        this.replaceLocalHost = replaceLocalHost;
    }

    /**
     * Инициализируем селектор сервера
     * 
     * @return
     * @throws IOException
     */
    private Selector initSelector() throws IOException {

        Selector socketSelector = SelectorProvider.provider().openSelector();

        serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);

        // устанавлием лольный порт для прослушивания
        InetSocketAddress isa = new InetSocketAddress(hostAddress,
                port);
        serverChannel.socket().bind(isa);
        serverChannel.register(socketSelector, serverChannel.validOps());

        return socketSelector;
    }

    /*
     * Основной цикл сервера
     * 
     * @see java.lang.Runnable#run()
     */
    @Override
    public void run() {
        try {
            while (!stopServer) {
                // Ждем события от зарегистированных каналов
                selector.select();
                // Просматриваем ключи всех каналов, которые жду обработки
                Iterator selectedKeys = selector.selectedKeys().iterator();
                while (selectedKeys.hasNext()) {
                    SelectionKey key = (SelectionKey) selectedKeys.next();
                    selectedKeys.remove();
                    try {
                        if (!key.isValid()) {
                            continue;
                        }
                        if (key.isConnectable()) {
                            connect(key);
                        } else if (key.isAcceptable()) {
                            accept(key);
                        } else if (key.isReadable()) {
                            read(key);
                        } else if (key.isWritable()) {
                            write(key);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        close(key);
                    }
                }
            }
            // Вышли из основного цикла, значит надо остановить сервер
            /*
             * Iterator keys = selector.keys().iterator(); while
             * (keys.hasNext()) { SelectionKey key = (SelectionKey) keys.next();
             * close(key); }
             */
            selector.close();
            serverChannel.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    /**
     * Принимаем входящее содинение к серверу
     * 
     * @param key
     * @throws IOException
     */
    private void accept(SelectionKey key) throws IOException {

        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key
                .channel();

        SocketChannel socketChannel = serverSocketChannel.accept();
        System.out.println("Accepting Connection from: "
                + socketChannel.socket());
        socketChannel.configureBlocking(false);

        // Регистируем новый канал на чтение, так как ему есть что нам переслать
        socketChannel.register(selector, SelectionKey.OP_READ);
    }

    /**
     * Чтение из канала в буффер
     * 
     * @param key
     * @throws IOException
     */
    private void read(SelectionKey key) throws IOException {
        // Если нет аттача, значит первый раз этот ключ готов к чтению
        if (key.attachment() == null) {
            SocketChannel sourceChannel = SocketChannel.open();
            sourceChannel.configureBlocking(false);
            // Начинаем устанавливать соединение
            sourceChannel.connect(new InetSocketAddress(destinationAddress,
                    destinationPort));

            SelectionKey sourceKey = sourceChannel.register(key.selector(),
                    SelectionKey.OP_CONNECT);
            Attachment destinationAttacment = new Attachment(sourceKey, false);
            Attachment sourceAttachment = new Attachment(key, true);
            destinationAttacment.buffer = sourceAttachment.buffer;
            key.attach(destinationAttacment);
            sourceKey.attach(sourceAttachment);

            // Читаем первый запрос от клиента к удаленному серверу в буфер
            System.out.println("Sending request to " + destinationAddress + ":"
                    + destinationPort);
            SocketChannel channel = ((SocketChannel) key.channel());
            if (channel.read(destinationAttacment.buffer) == -1) {
                close(key);
            } else {
                // Запрос записали, теперь здесь ничего не интересно, пока на
                // другом хвосте не запишут что-нибудь
                parseRequest(destinationAttacment.buffer);
                key.interestOps(0);
                destinationAttacment.buffer.flip();
            }
        } else {
            // Обычная ситуация чтения из канала
            SocketChannel channel = ((SocketChannel) key.channel());
            Attachment attachment = ((Attachment) key.attachment());
            if (attachment.isSource) {
                System.out.println("Reading from client channel");
            } else {
                System.out.println("Reading from server channel");
            }
            int byteCount = 0;
            int readAttempts = 0;

            // Пробуем прочесть из канала, если за 5 попыток ничего не
            // получилось, то закрываем канал
            try {
                while ((byteCount = channel.read(attachment.buffer)) > 0
                        && readAttempts <= 5) {
                    readAttempts++;
                }
                if (readAttempts > 5) {
                    System.out.println("Read attemps get to the limit: "
                            + readAttempts + ". Close channel");
                    close(key);
                }
            } catch (IOException e) {
                e.printStackTrace();
                close(key);
            }
            System.out.println("Read attemps: " + readAttempts);
            if (byteCount == -1) {
                // -1 - разрыв 0 - нет места в буфере
                close(key);
            } else {
                // Устанавливаем интересы
                attachment.peer.interestOps(attachment.peer.interestOps() |
                        SelectionKey.OP_WRITE);
                key.interestOps(key.interestOps() ^ SelectionKey.OP_READ);

                // готовим буфер для записи
                attachment.buffer.flip();
            }
        }
    }

    /**
     * Запись данных из буфера
     * 
     * @param key
     * @throws IOException
     */
    private void write(SelectionKey key) throws IOException {
        // Закрывать сокет надо только записав все данные
        SocketChannel channel = ((SocketChannel) key.channel());
        Attachment attachment = ((Attachment) key.attachment());
        if (attachment.isSource) {
            System.out.println("Writing to client channel");
        } else {
            System.out.println("Writing to server channel");
        }
        int byteCount = 0;
        while (attachment.buffer.hasRemaining()) {
            byteCount = channel.write(attachment.buffer);
            System.out.println("Has written: " + byteCount);
        }

        if (byteCount == -1) {
            close(key);
        } else if (attachment.buffer.remaining() == 0) {
            if (attachment.peer == null) {
                // Дописали что было в буфере и закрываем канал
                close(key);
            } else {
                // если всё записано, чистим буфер
                attachment.buffer.clear();
                // Переставляем интересы на операции
                attachment.peer.interestOps(attachment.peer.interestOps() |
                        SelectionKey.OP_READ);
                key.interestOps(key.interestOps() ^ SelectionKey.OP_WRITE);

            }
        }
    }

    /**
     * Отменяем регистраюцию ключа и закрываем парный канал
     * 
     * @param key
     * @throws IOException
     */

    private void close(SelectionKey key) throws IOException {
        if (((Attachment) key.attachment()).isSource) {
            System.out.println("Deregister key at client side");
        } else {
            System.out.println("Deregister key at server side");
        }
        key.cancel();
        SelectionKey peerKey = ((Attachment)
                key.attachment()).peer;
        if (peerKey != null) {
            ((Attachment) peerKey.attachment()).peer = null;
            if ((peerKey.interestOps() & SelectionKey.OP_WRITE) == 0) {
                ((Attachment) peerKey.attachment()).buffer.flip();
            }
            peerKey.interestOps(SelectionKey.OP_WRITE);
        }
    }

    /**
     * устанавливаем соединение с удаленным сервером и посылаем запрос
     * 
     * @param key
     * @throws IOException
     */
    private void connect(SelectionKey key) throws IOException {
        SocketChannel channel = ((SocketChannel) key.channel());
        Attachment attachment = (Attachment) key.attachment();
        // Финализируем соединение и выполняем запрос необходимого ресурса
        if (channel.finishConnect()) {
            // Пишем запрос к удаленному серверу
            System.out.println("Reading request at " + channel.socket());
            if (channel.write(attachment.buffer) == -1) {
                close(key);
            } else {
                attachment.buffer.clear();
                // Теперь нас интересует ответ удаленного сервера, т.е. чтение
                // из канала
                key.interestOps(key.interestOps() | SelectionKey.OP_READ);
            }

        }
    }

    /**
     * Парсим HTTP запрос и подменяем если надо поля connection (на close) и
     * host (с localhost на адрес реально запрашиваемого хоста)
     * 
     * @param buffer
     * @throws CharacterCodingException
     */
    private void parseRequest(ByteBuffer buffer)
            throws CharacterCodingException {

        Charset charset = Charset.forName("ISO-8859-1");
        CharsetEncoder encoder = charset.newEncoder();
        CharsetDecoder decoder = charset.newDecoder();
        CharBuffer cb = CharBuffer.allocate(Attachment.bufferSize);

        buffer.flip();
        decoder.decode(buffer, cb, false);
        cb.flip();
        String request = cb.toString();
        String result = request;
        // Проверяем парсим ли HTTP запрос
        if (request.indexOf("HTTP") > 0) {
            int i = request.indexOf("keep-alive");
            if (i != 0) {
                result = request.substring(0, i) + "close"
                        + request.substring(i + 10);
            }
            request = result;
            i = request.indexOf("localhost:" + port);
            // Если есть, что подменять и включена такая опция, то подменяем
            // localhost на настоящий хост
            if (i != 0 && replaceLocalHost) {
                result = request.substring(0, i)
                        + destinationAddress.getHostName()
                        + ":"
                        + destinationPort
                        + request.substring(i + 10
                                + Integer.toString(port).length());
            }
        }
        System.out.println("Request before sending: " + result);
        buffer.clear();
        buffer.put(encoder.encode(CharBuffer
                .wrap(result)));
    }

    public void stopServer() {
        stopServer = true;
    }

}
