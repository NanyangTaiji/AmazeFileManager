package com.amaze.filemanager.filesystem.ssh;

import static com.amaze.filemanager.filesystem.ftp.NetCopyClientUtils.extractRemotePathFrom;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;
import com.amaze.filemanager.R;
import com.amaze.filemanager.fileoperations.filesystem.cloud.CloudStreamer;
import com.amaze.filemanager.filesystem.HybridFile;
import com.amaze.filemanager.filesystem.ftp.NetCopyClientUtils;
import com.amaze.filemanager.ui.activities.MainActivity;
import com.amaze.filemanager.ui.icons.MimeTypes;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.sftp.FileAttributes;
import net.schmizz.sshj.sftp.FileMode;
import net.schmizz.sshj.sftp.RemoteResourceInfo;
import net.schmizz.sshj.sftp.SFTPClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public class SshClientUtils {

    private static final Logger LOG = LoggerFactory.getLogger(SshClientUtils.class);

    private static final NetCopyClientUtils netCopyClientUtils = new NetCopyClientUtils();

    public static Long sftpGetSize(String path) {
            return netCopyClientUtils.execute(new SFtpClientTemplate<Long>(path, true) {
                @Override
                public Long execute(SFTPClient client) {
                    try {
                        return client.size(extractRemotePathFrom(path));
                    } catch (IOException e) {
                        LOG.warn("Error getting file size", e);
                    }
                    return null;
                }
            });
        }

    public static <T> T execute(SshClientSessionTemplate<T> template) {
        return netCopyClientUtils.execute(new SshClientTemplate<T>(template.url, false) {
            @Override
            protected T executeWithSSHClient(SSHClient sshClient) {
                Session session = null;
                T retval = null;
                try {
                    session = sshClient.startSession();
                    retval = template.execute(session);
                } catch (IOException e) {
                    LOG.error("Error executing template method", e);
                } finally {
                    if (session != null && session.isOpen()) {
                        try {
                            session.close();
                        } catch (IOException e) {
                            LOG.warn("Error closing SFTP client", e);
                        }
                    }
                }
                return retval;
            }
        });
    }

    public static <T> T execute(SFtpClientTemplate<T> template) {
        return netCopyClientUtils.execute(template);
    }

    public static String formatPlainServerPathToAuthorised(ArrayList<String[]> servers, String path) {
        for (String[] serverEntry : servers) {
            Uri inputUri = Uri.parse(path);
            Uri serverUri = Uri.parse(serverEntry[1]);
            if (inputUri.getScheme().equalsIgnoreCase(serverUri.getScheme())
                    && serverUri.getAuthority() != null
                    && serverUri.getAuthority().contains(inputUri.getAuthority())) {
                String output = inputUri.buildUpon()
                        .encodedAuthority(serverUri.getEncodedAuthority())
                        .build().toString();
                LOG.info("build authorised path {} from plain path {}", output, path);
                return output;
            }
        }
        return path;
    }

    public static void tryDisconnect(SSHClient client) {
        if (client != null && client.isConnected()) {
            try {
                client.disconnect();
            } catch (IOException e) {
                LOG.warn("Error closing SSHClient connection", e);
            }
        }
    }

    public static void launchFtp(HybridFile baseFile, MainActivity activity) {
        CloudStreamer streamer = CloudStreamer.getInstance();
        new Thread(() -> {
            try {
                boolean isDirectory = baseFile.isDirectory(activity);
                long fileLength = baseFile.length(activity);
                streamer.setStreamSrc(
                        baseFile.getInputStream(activity),
                        baseFile.getName(activity),
                        fileLength
                );
                activity.runOnUiThread(() -> {
                    try {
                        File file = new File(extractRemotePathFrom(baseFile.getPath()));
                        Uri uri = Uri.parse(CloudStreamer.URL + Uri.fromFile(file).getEncodedPath());
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setDataAndType(uri, MimeTypes.getMimeType(baseFile.getPath(), isDirectory));
                        if (intent.resolveActivity(activity.getPackageManager()) != null) {
                            activity.startActivity(intent);
                        } else {
                            Toast.makeText(
                                    activity,
                                    activity.getResources().getString(R.string.smb_launch_error),
                                    Toast.LENGTH_SHORT
                            ).show();
                        }
                    } catch (ActivityNotFoundException e) {
                        LOG.warn("failed to launch sftp file", e);
                    }
                });
            } catch (Exception e) {
                LOG.warn("failed to launch sftp file", e);
            }
        }).start();
    }

    public static boolean isDirectory(SFTPClient client, RemoteResourceInfo info) throws IOException {
        boolean isDirectory = info.isDirectory();
        if (info.getAttributes().getType() == FileMode.Type.SYMLINK) {
            try {
                FileAttributes symlinkAttrs = client.stat(info.getPath());
                isDirectory = symlinkAttrs.getType() == FileMode.Type.DIRECTORY;
            } catch (IOException ifSymlinkIsBroken) {
                LOG.warn("Symbolic link {} is broken, skipping", info.getPath());
                throw ifSymlinkIsBroken;
            }
        }
        return isDirectory;
    }
}

