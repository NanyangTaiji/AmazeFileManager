package com.amaze.filemanager.filesystem.ftp;

import net.schmizz.sshj.SSHClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SSHClientImpl implements NetCopyClient<SSHClient> {

    private final SSHClient sshClient;
    private static final Logger logger = LoggerFactory.getLogger(SSHClientImpl.class);

    public SSHClientImpl(SSHClient sshClient) {
        this.sshClient = sshClient;
    }

    @Override
    public SSHClient getClientImpl() {
        return sshClient;
    }

    @Override
    public boolean isConnectionValid() {
        return sshClient.isConnected() && sshClient.isAuthenticated();
    }

    @Override
    public void expire() {
        if (sshClient.isConnected()) {
            try {
                sshClient.disconnect();
            } catch (Exception e) {
                logger.warn("Error closing SSHClient connection", e);
            }
        }
    }
}

