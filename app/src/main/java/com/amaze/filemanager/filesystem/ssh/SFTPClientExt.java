package com.amaze.filemanager.filesystem.ssh;

import net.schmizz.sshj.sftp.FileAttributes;
import net.schmizz.sshj.sftp.OpenMode;
import net.schmizz.sshj.sftp.PacketType;
import net.schmizz.sshj.sftp.RemoteFile;
import net.schmizz.sshj.sftp.SFTPEngine;
import net.schmizz.sshj.sftp.SFTPClient;
import java.io.IOException;
import java.util.EnumSet;
import java.util.concurrent.TimeUnit;

public class SFTPClientExt {

    public static final int READ_AHEAD_MAX_UNCONFIRMED_READS = 16;

    /**
     * Monkey-patch SFTPEngine.open until sshj adds back read ahead support in RemoteFile.
     */
    public static RemoteFile openWithReadAheadSupport(
            SFTPEngine sftpEngine,
            String path,
            EnumSet<OpenMode> modes,
            FileAttributes fa
    ) throws IOException {
        byte[] handle = sftpEngine.request(
                        sftpEngine.newRequest(PacketType.OPEN)
                                .putString(path, sftpEngine.getSubsystem().getRemoteCharset())
                                .putUInt32(OpenMode.toMask(modes))
                                .putFileAttributes(fa)
                ).retrieve(sftpEngine.getTimeoutMs(), TimeUnit.MILLISECONDS)
                .ensurePacketTypeIs(PacketType.HANDLE)
                .readBytes();
        return new RemoteFile(sftpEngine, path, handle);
    }

    /**
     * Monkey-patch SFTPClient.open until sshj adds back read ahead support in RemoteFile.
     */
    public static RemoteFile openWithReadAheadSupport(SFTPClient sftpClient, String path) throws IOException {
        return SFTPClientExt.openWithReadAheadSupport(
                sftpClient.getSFTPEngine(),
                path,
                EnumSet.of(OpenMode.READ),
                FileAttributes.EMPTY
        );
    }


}
