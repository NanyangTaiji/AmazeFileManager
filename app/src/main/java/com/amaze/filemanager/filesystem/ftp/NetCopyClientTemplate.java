package com.amaze.filemanager.filesystem.ftp;

import com.amaze.filemanager.filesystem.ftp.NetCopyClient;

import java.io.IOException;

public abstract class NetCopyClientTemplate<ClientType, T> {

    /**
     * SSH connection URL, in the form of
     * ssh://<username>:<password>@<host>:<port> or
     * ssh://<username>@<host>:<port>
     */
    public final String url;

    /**
     * Flag indicating whether the connection should be closed after execution.
     */
    public final boolean closeClientOnFinish;

    /**
     * Constructor.
     *
     * @param url                 SSH connection URL
     * @param closeClientOnFinish Flag to indicate whether to close the connection after execution
     */
    public NetCopyClientTemplate(String url, boolean closeClientOnFinish) {
        this.url = url;
        this.closeClientOnFinish = closeClientOnFinish;
    }

    /**
     * Implement logic here.
     *
     * @param client NetCopyClient instance with connection opened and authenticated
     * @return Result of the execution of the requested type
     * @throws IOException If an I/O error occurs
     */
    public abstract T execute(NetCopyClient<ClientType> client) throws IOException;
}

