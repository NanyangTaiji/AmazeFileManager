package com.amaze.filemanager.filesystem.ssh;

import com.amaze.filemanager.filesystem.ftp.NetCopyClient;
import com.amaze.filemanager.filesystem.ftp.NetCopyClientTemplate;
import net.schmizz.sshj.SSHClient;
import java.io.IOException;

/**
 * Template class for executing actions with {@link SSHClient} while managing connection setup/teardown complexities.
 *
 * @param <T> Type of the result returned by the execution
 */
public abstract class SshClientTemplate<T> extends NetCopyClientTemplate<SSHClient, T> {

    /**
     * Constructor.
     *
     * @param url               SSH connection URL, in the form of
     *                          {@code ssh://<username>:<password>@<host>:<port>} or
     *                          {@code ssh://<username>@<host>:<port>}
     * @param closeClientOnFinish Whether to close the SSH client when the execution finishes
     */
    public SshClientTemplate(String url, boolean closeClientOnFinish) {
        super(url, closeClientOnFinish);
    }

    /**
     * Implement logic here.
     *
     * @param client {@link SSHClient} instance, with connection opened and authenticated
     * @return Result of the execution of the requested type
     * @throws IOException if an I/O error occurs
     */
    protected abstract T executeWithSSHClient(SSHClient client) throws IOException;

    /**
     * Executes the template action with the SSH client.
     *
     * @param client {@link NetCopyClient} instance wrapping the {@link SSHClient}
     * @return Result of the execution of the requested type
     * @throws IOException if an I/O error occurs
     */
    @Override
    public final T execute(NetCopyClient<SSHClient> client) throws IOException {
        SSHClient sshClient = client.getClientImpl();
        return executeWithSSHClient(sshClient);
    }
}


