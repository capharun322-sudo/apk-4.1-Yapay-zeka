package com.benim.codemate;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/** Android 4.1-4.4 uzerinde TLS 1.2'yi acar. */
public class Tls12SocketFactory extends SSLSocketFactory {
    private final SSLSocketFactory d;

    public Tls12SocketFactory(SSLSocketFactory delegate) { this.d = delegate; }

    private Socket patch(Socket s) {
        if (s instanceof SSLSocket) {
            ((SSLSocket) s).setEnabledProtocols(new String[]{"TLSv1.2", "TLSv1.1", "TLSv1"});
        }
        return s;
    }

    @Override public String[] getDefaultCipherSuites() { return d.getDefaultCipherSuites(); }
    @Override public String[] getSupportedCipherSuites() { return d.getSupportedCipherSuites(); }
    @Override public Socket createSocket(Socket s, String host, int port, boolean auto) throws IOException { return patch(d.createSocket(s, host, port, auto)); }
    @Override public Socket createSocket(String host, int port) throws IOException { return patch(d.createSocket(host, port)); }
    @Override public Socket createSocket(String host, int port, InetAddress la, int lp) throws IOException { return patch(d.createSocket(host, port, la, lp)); }
    @Override public Socket createSocket(InetAddress host, int port) throws IOException { return patch(d.createSocket(host, port)); }
    @Override public Socket createSocket(InetAddress a, int port, InetAddress la, int lp) throws IOException { return patch(d.createSocket(a, port, la, lp)); }
}
