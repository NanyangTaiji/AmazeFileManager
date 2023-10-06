package com.stealthcopter.networktools.portscanning;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

/*
The let function is not needed in Java,
and the code is structured to handle the socket connection, catch exceptions, and close the socket appropriately.

 */
public class PortScanTCP {

    /**
     * Check if a port is open with TCP
     *
     * @param ia            - address to scan
     * @param portNo        - port to scan
     * @param timeoutMillis - timeout
     * @return - true if port is open, false if not or unknown
     */
    public static boolean scanAddress(InetAddress ia, int portNo, int timeoutMillis) {
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(ia, portNo), timeoutMillis);
            return true;
        } catch (IOException e) {
            return false;
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                // Handle close exception if necessary
            }
        }
    }
}

