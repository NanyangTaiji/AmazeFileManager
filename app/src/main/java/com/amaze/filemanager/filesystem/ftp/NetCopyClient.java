package com.amaze.filemanager.filesystem.ftp;

/**
 * Base interface for defining a client class that interacts with a remote server.
 */
public interface NetCopyClient<T> {

    /**
     * Returns the physical client implementation.
     */
    T getClientImpl();

    /**
     * Answers if the connection of the underlying client is still valid.
     */
    boolean isConnectionValid();

    /**
     * Answers if the client returned by [getClientImpl] requires thread safety.
     *
     * [NetCopyClientUtils.execute] will see this flag and enforce locking as necessary.
     */
    default boolean isRequireThreadSafety() {
        return false;
    }

    /**
     * Implement logic to expire the underlying connection if it went stale, timed out, etc.
     */
    void expire();
}

