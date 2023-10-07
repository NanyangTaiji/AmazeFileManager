package com.amaze.filemanager.filesystem.ssh;

import net.schmizz.sshj.connection.channel.direct.Session;
import java.io.IOException;

/**
 * Template class for executing actions with an SSH session ({@link Session}).
 */
public abstract class SshClientSessionTemplate<T> {

    /**
     * SSH connection URL, in the form of `ssh://<username>:<password>@<host>:<port>` or
     * `ssh://<username>@<host>:<port>`
     */
    protected final String url;

    /**
     * Constructor.
     *
     * @param url SSH connection URL, in the form of `ssh://<username>:<password>@<host>:<port>`
     *            or `ssh://<username>@<host>:<port>`
     */
    public SshClientSessionTemplate(String url) {
        this.url = url;
    }

    /**
     * Implement logic here.
     *
     * @param sshClientSession {@link Session} instance, with connection opened and authenticated
     * @return Result of the execution of the type requested
     * @throws IOException if an I/O error occurs
     */
    public abstract T execute(Session sshClientSession) throws IOException;
}

