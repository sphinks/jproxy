import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;

/**
 * @author Sphinks
 * 
 */
public class Attachment {

    SelectionKey peer;
    boolean isSource;
    ByteBuffer buffer;
    final static int bufferSize = 1024 * 16;

    public Attachment(SelectionKey peer, boolean isSource) {
        this.peer = peer;
        this.isSource = isSource;
        if (this.isSource) {
            buffer = ByteBuffer.allocateDirect(bufferSize);
        }
    }

}
