package com.amaze.filemanager.filesystem.ssh;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.SFTPClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;

/**
 * Template class for executing actions with {@link SFTPClient} while handling the complexities of
 * handling connection and session setup/teardown to {@link SshClientUtils}.
 */
public abstract class SFtpClientTemplate<T> extends SshClientTemplate<T> {

    private static final Logger LOG = LoggerFactory.getLogger(SFtpClientTemplate.class);

    public SFtpClientTemplate(String url, boolean closeClientOnFinish) {
        super(url, closeClientOnFinish);
    }

    @Override
    public T executeWithSSHClient(SSHClient sshClient) {
        SFTPClient sftpClient = null;
        T retval = null;
        try {
            sftpClient = sshClient.newSFTPClient();
            retval = execute(sftpClient);
        } catch (IOException e) {
            LOG.error("Error executing template method", e);
        } finally {
            if (sftpClient != null && closeClientOnFinish) {
                try {
                    sftpClient.close();
                } catch (IOException e) {
                    LOG.warn("Error closing SFTP client", e);
                }
            }
        }
        return retval;
    }

    /**
     * Implement logic here.
     *
     * @param client {@link SFTPClient} instance, with connection opened and authenticated, and SSH
     * session had been set up.
     * @return Result of the execution of the type requested
     * @throws IOException if an I/O error occurs
     */
    protected abstract T execute(SFTPClient client) throws IOException;
}

