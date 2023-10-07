package com.amaze.filemanager.filesystem.ftp;

import static com.amaze.filemanager.fileoperations.filesystem.FolderState.DOESNT_EXIST;
import static com.amaze.filemanager.fileoperations.filesystem.FolderState.WRITABLE_ON_REMOTE;

import static java.sql.DriverManager.getConnection;

import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import com.amaze.filemanager.application.AppConfig;
import com.amaze.filemanager.fileoperations.filesystem.FolderState;
import com.amaze.filemanager.fileoperations.filesystem.FolderState.*;
import com.amaze.filemanager.filesystem.smb.CifsContexts;
import com.amaze.filemanager.filesystem.ssh.SFtpClientTemplate;
import com.amaze.filemanager.utils.smb.SmbUtil;
import io.reactivex.Maybe;
import io.reactivex.Scheduler;
import io.reactivex.schedulers.Schedulers;
import net.schmizz.sshj.sftp.SFTPClient;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class NetCopyClientUtils {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(NetCopyClientUtils.class);

    private static final char AT = '@';
    private static final char COLON = ':';
    private static final char SLASH = '/';

    @VisibleForTesting
    static SchedulerProvider schedulerProvider = new SchedulerProvider();

    public static void setSchedulerProvider(SchedulerProvider provider) {
        schedulerProvider = provider;
    }

    public static void resetSchedulerProvider() {
        schedulerProvider = new SchedulerProvider();
    }

    public static Scheduler getScheduler(NetCopyClient<?> client) {
        return client.isRequireThreadSafety() ? schedulerProvider.getSingleScheduler() : schedulerProvider.getIoScheduler();
    }

    public static <ClientType, T> T execute(NetCopyClientTemplate<ClientType, T> template) {
        NetCopyClient<?> client = null;
        try {
            client = (NetCopyClient<?>) getConnection(extractBaseUriFrom(template.url));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        if (client == null) {
            try {
                client = (NetCopyClient<?>) getConnection(template.url);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
        T retval = null;
        if (client != null) {
            try {
                NetCopyClient<?> finalClient = client;
                Maybe.fromCallable(() -> template.execute((NetCopyClient<ClientType>) finalClient))
                        .subscribeOn(getScheduler(client))
                        .blockingGet();
            } catch (Exception e) {
                LOG.error("Error executing template method", e);
            } finally {
                if (template.closeClientOnFinish) {
                    tryDisconnect(client);
                }
            }
        }
        return retval;
    }

    public static String encryptFtpPathAsNecessary(String fullUri) {
        String uriWithoutProtocol = fullUri.substring(fullUri.indexOf("://") + 3);
        if (uriWithoutProtocol.contains(String.valueOf(AT)) && uriWithoutProtocol.contains(String.valueOf(COLON))) {
            return SmbUtil.getSmbEncryptedPath(AppConfig.getInstance(), fullUri);
        } else {
            return fullUri;
        }
    }

    public static String decryptFtpPathAsNecessary(String fullUri) {
        try {
            String uriWithoutProtocol = fullUri.substring(fullUri.indexOf("://") + 3);
            if (uriWithoutProtocol.contains(String.valueOf(COLON))) {
                return SmbUtil.getSmbDecryptedPath(AppConfig.getInstance(), fullUri);
            } else {
                return fullUri;
            }
        } catch (Exception e) {
            LOG.error("Error decrypting path", e);
            return fullUri;
        }
    }

    public static String extractBaseUriFrom(String fullUri) {
        NetCopyConnectionInfo connInfo = new NetCopyConnectionInfo(fullUri);
        StringBuilder baseUri = new StringBuilder();
        baseUri.append(connInfo.getPrefix());
        if (!connInfo.getUsername().isEmpty()) {
            baseUri.append(connInfo.getUsername());
            if (!connInfo.getPassword().isEmpty()) {
                baseUri.append(COLON).append(connInfo.getPassword());
            }
            baseUri.append(AT);
        }
        baseUri.append(connInfo.getHost());
        if (connInfo.getPort() > 0) {
            baseUri.append(COLON).append(connInfo.getPort());
        }
        return baseUri.toString();
    }

    public static String extractRemotePathFrom(String fullUri) {
        NetCopyConnectionInfo connInfo = new NetCopyConnectionInfo(fullUri);
        if (connInfo.getDefaultPath() != null && !connInfo.getDefaultPath().isEmpty()) {
            StringBuilder remotePath = new StringBuilder();
            remotePath.append(connInfo.getDefaultPath());
            if (connInfo.getFilename() != null && !connInfo.getFilename().isEmpty()) {
                remotePath.append(SLASH).append(connInfo.getFilename());
            }
            return remotePath.toString();
        } else {
            return SLASH + "";
        }
    }

    private static void tryDisconnect(NetCopyClient<?> client) {
        if (client.isConnectionValid()) {
            client.expire();
        }
    }

    public static String deriveUriFrom(String prefix, String hostname, int port, String defaultPath, String username, String password, boolean edit) {
        String pathSuffix = (defaultPath == null) ? SLASH + "" : defaultPath;
        String thisPassword = (password == null || password.isEmpty()) ? "" : (COLON + (edit ? password : NetCopyClientUtils.urlEncoded(password)));
        if (username.isEmpty() && (password == null || password.isEmpty())) {
            return prefix + hostname + COLON + port + pathSuffix;
        } else {
            return prefix + username + thisPassword + AT + hostname + COLON + port + pathSuffix;
        }
    }

    public static int checkFolder(String path) {
        if (path.startsWith(NetCopyClientConnectionPool.SSH_URI_PREFIX)) {
            return checkSftpFolder(path);
        } else {
            return checkFtpFolder(path);
        }
    }

    private static int checkSftpFolder(String path) {
        SFtpClientTemplate<Integer> template = new SFtpClientTemplate<Integer>(extractBaseUriFrom(path), false) {
            @FolderState.State
            @Override
            public Integer execute(SFTPClient client) throws IOException {
                if (client.statExistence(extractRemotePathFrom(path)) == null) {
                    return WRITABLE_ON_REMOTE;
                } else {
                    return DOESNT_EXIST;
                }
            }
        };
        return execute(template) != null ? WRITABLE_ON_REMOTE : DOESNT_EXIST;
    }

    private static int checkFtpFolder(String path) {
        FtpClientTemplate<Integer> template = new FtpClientTemplate<Integer>(extractBaseUriFrom(path), false) {
            @Override
            public Integer execute(NetCopyClient<FTPClient> client) throws IOException {
                return null;
            }

            @Override
            public Integer executeWithFtpClient(FTPClient ftpClient) throws IOException {
                if (ftpClient.stat(extractRemotePathFrom(path)) == FTPReply.DIRECTORY_STATUS) {
                    return WRITABLE_ON_REMOTE;
                } else {
                    return DOESNT_EXIST;
                }
            }
        };
        return execute(template) != null ? WRITABLE_ON_REMOTE : DOESNT_EXIST;
    }

    public static int defaultPort(String prefix) {
        switch (prefix) {
            case NetCopyClientConnectionPool.SSH_URI_PREFIX:
                return NetCopyClientConnectionPool.SSH_DEFAULT_PORT;
            case NetCopyClientConnectionPool.FTPS_URI_PREFIX:
                return NetCopyClientConnectionPool.FTPS_DEFAULT_PORT;
            case NetCopyClientConnectionPool.FTP_URI_PREFIX:
                return NetCopyClientConnectionPool.FTP_DEFAULT_PORT;
            case CifsContexts.SMB_URI_PREFIX:
                return 0; // SMB never requires an explicit port number in the URL
            default:
                throw new IllegalArgumentException("Cannot derive default port");
        }
    }

    public static String getTimestampForTouch(long date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(date);
        DateFormat df = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US);
        df.setCalendar(calendar);
        return df.format(calendar.getTime());
    }

    public static class SchedulerProvider {
        public Scheduler getIoScheduler() {
            return Schedulers.io();
        }

        public Scheduler getSingleScheduler() {
            return Schedulers.single();
        }
    }

    public static String urlEncoded(String input) {
        try {
            return java.net.URLEncoder.encode(input, "UTF-8");
        } catch (Exception e) {
            LOG.error("Error encoding URL", e);
            return input;
        }
    }
}
