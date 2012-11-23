/**
 * @author I.Zerin
 * 
 */
public class ProxyEntity {

    private Integer localPort;
    private String remoteHost;
    private Integer remotePort;

    /**
     * @param localPort
     * @param remoteHost
     * @param remotePort
     */
    public ProxyEntity(Integer localPort, String remoteHost, Integer remotePort) {
        this.localPort = localPort;
        this.remoteHost = remoteHost;
        this.remotePort = remotePort;
    }

    public Integer getLocalPort() {
        return localPort;
    }

    public void setLocalPort(Integer localPort) {
        this.localPort = localPort;
    }

    public String getRemoteHost() {
        return remoteHost;
    }

    public void setRemoteHost(String remoteHost) {
        this.remoteHost = remoteHost;
    }

    public Integer getRemotePort() {
        return remotePort;
    }

    public void setRemotePort(Integer remotePort) {
        this.remotePort = remotePort;
    }

}
