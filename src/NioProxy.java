import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * @author I.Zerin
 * 
 */
public class NioProxy {

    public static void main(String[] args) {

        List<NioServer> servers = new ArrayList<NioServer>();
        boolean replaceLocalHost = true;
        // Ключ для отключения замены localhost в поле HOST HTTP запроса
        if (args.length > 0 && args[0].equals("-n")) {
            replaceLocalHost = false;
        }
        // запускаем прокси сервер
        try {
            List<ProxyEntity> settingsList = SettingsFile.getSettingsList();
            if (settingsList.size() > 0) {
                for (ProxyEntity entity : settingsList) {
                    NioServer server = new NioServer(
                            InetAddress.getByName("localhost"),
                            entity.getLocalPort(), InetAddress.getByName(entity
                                    .getRemoteHost()), entity.getRemotePort(),
                            replaceLocalHost);
                    servers.add(server);
                    new Thread(server)
                            .start();

                }
            } else {
                System.out.println("File proxy.properties was not found");
            }
        } catch (FileNotFoundException e) {
            System.out.println("File proxy.properties was not found");
        } catch (NumberFormatException e) {
            System.out.println("There is an error in setting file: "
                    + e.getMessage());
        } catch (java.net.BindException e) {
            System.out
                    .println("Address already in use, may be another copy of programm is running.");
        } catch (IOException e) {
            e.printStackTrace();
        }

        Scanner scanner = new Scanner(System.in);
        // Обрабатываем ввод с консоли
        while (true) {
            String str = scanner.nextLine();
            if (str.equals("help")) {

                System.out.println("-= Help =-");
                System.out.println("help - show this screen");
                System.out.println("quit - stop proxy server and exit");
            }
            if (str.equals("quit")) {
                System.out.println("Stopping server");
                for (NioServer server : servers) {
                    server.stopServer();
                }
                System.out.println("Server stopped. Exit.");
                System.exit(0);
                /* Завершаем программу с кодом 0 (успешно) */
            }
        }
    }

}
