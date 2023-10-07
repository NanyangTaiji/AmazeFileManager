package com.amaze.filemanager.filesystem.ftp;

import com.amaze.filemanager.filesystem.ftp.NetCopyClientTemplate;

import org.apache.commons.net.ftp.FTPClient;
import java.io.IOException;

public abstract class FtpClientTemplate<T> extends NetCopyClientTemplate<FTPClient, T> {

    /**
     * Constructor for FtpClientTemplate.
     *
     * @param url                 FTP connection URL
     * @param closeClientOnFinish Flag indicating whether to close the connection after execution
     */
    public FtpClientTemplate(String url, boolean closeClientOnFinish) {
        super(url, closeClientOnFinish);
    }

    /**
     * Constructor for FtpClientTemplate with default value for closeClientOnFinish.
     *
     * @param url FTP connection URL
     */
    public FtpClientTemplate(String url) {
        super(url, true);
    }

    /**
     * Execute FTP-related logic here.
     *
     * @param ftpClient FTPClient instance with connection opened and authenticated
     * @return Result of the execution of the requested type
     * @throws IOException If an I/O error occurs
     */
    public abstract T executeWithFtpClient(FTPClient ftpClient) throws IOException;
}

