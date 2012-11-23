import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * @author I.Zerin
 * 
 */
public class SettingsFile {

    private Properties content;
    private String fileName = "proxy.properties";
    private static SettingsFile settingsFile;
    private List<ProxyEntity> proxyEntities;

    private static final String LOCAL_PORT = "localPort";
    private static final String REMOTE_HOST = "remoteHost";
    private static final String REMOTE_PORT = "remotePort";

    private SettingsFile() throws FileNotFoundException, IOException,
            NumberFormatException {
        content = new Properties();
        proxyEntities = new ArrayList<ProxyEntity>();
        loadSettings();
        createSettings();
    }

    public static List<ProxyEntity> getSettingsList()
            throws FileNotFoundException, IOException {
        if (settingsFile == null) {
            settingsFile = new SettingsFile();
        }
        return settingsFile.proxyEntities;
    }

    private void loadSettings() throws IOException, FileNotFoundException {
        InputStream in = null;
        try {
            in = SettingsFile.class.getClassLoader().getResourceAsStream(
                    fileName);
            content.load(in);
        } finally {
            if (in != null)
                in.close();
        }
    }

    private void createSettings() throws NumberFormatException {
        Set s = content.keySet();
        Iterator<String> i = s.iterator();
        while (i.hasNext()) {
            String keyName = i.next();
            int pos = 0;
            if ((pos = keyName.indexOf(LOCAL_PORT)) != -1) {
                String prefix = keyName.substring(0, pos - 1);
                ProxyEntity entity = new ProxyEntity(
                        Integer.parseInt(content.getProperty(keyName)),
                        content.getProperty(prefix + '.'
                                + REMOTE_HOST), Integer.parseInt(content
                                .getProperty(prefix
                                        + '.'
                                        + REMOTE_PORT)));
                proxyEntities.add(entity);

            }
        }
    }
}
