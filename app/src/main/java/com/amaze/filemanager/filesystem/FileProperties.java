package com.amaze.filemanager.filesystem;

import static android.content.ContentResolver.SCHEME_CONTENT;
import static com.amaze.filemanager.filesystem.DeleteOperation.deleteFile;
import static com.amaze.filemanager.filesystem.ExternalSdCardOperation.isOnExtSdCard;

import android.app.usage.StorageStatsManager;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.provider.DocumentsContract;

import androidx.documentfile.provider.DocumentFile;

import com.amaze.filemanager.application.AppConfig;
import com.amaze.filemanager.database.CloudHandler;
import com.amaze.filemanager.filesystem.ftp.NetCopyClientConnectionPool;
import com.amaze.filemanager.filesystem.smb.CifsContexts;
import com.amaze.filemanager.utils.OTGUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public class FileProperties {
    private static final String STORAGE_PRIMARY = "primary";
    private static final String COM_ANDROID_EXTERNALSTORAGE_DOCUMENTS = "com.android.externalstorage.documents";

    public static final List<String> ANDROID_DATA_DIRS = Arrays.asList(
            "Android/data",
            "Android/obb");

    public static final List<String> ANDROID_DEVICE_DATA_DIRS = Arrays.asList(new File(Environment.getExternalStorageDirectory(), "Android/data").getAbsolutePath(),
            new File(Environment.getExternalStorageDirectory(), "Android/obb").getAbsolutePath());

    private static final Logger log = LoggerFactory.getLogger(FileProperties.class);

    public static boolean isReadable(File file) {
        if (file == null) return false;
        if (!file.exists()) return false;
        try {
            return file.canRead();
        } catch (SecurityException e) {
            return false;
        }
    }

    public static boolean isWritable(File file) {
        if (file == null) return false;
        boolean isExisting = file.exists();
        try {
            FileOutputStream output = new FileOutputStream(file, true);
            try {
                output.close();
            } catch (IOException e) {
                log.warn("failed to check if file is writable", e);
            }
        } catch (FileNotFoundException e) {
            log.warn("failed to check if file is writable as file not available", e);
            return false;
        }
        boolean result = file.canWrite();
        if (!isExisting) {
            file.delete();
        }
        return result;
    }

    public static boolean isWritableNormalOrSaf(File folder, Context c) {
        if (folder == null) {
            return false;
        }

        if (!folder.exists() || !folder.isDirectory()) {
            return false;
        }

        int i = 0;
        File file;
        do {
            String fileName = "AugendiagnoseDummyFile" + ++i;
            file = new File(folder, fileName);
        } while (file.exists());

        if (isWritable(file)) {
            return true;
        }

        DocumentFile document = ExternalSdCardOperation.getDocumentFile(file, false, c);
        if (document != null && document.canWrite()) {
            return true;
        }
        deleteFile(file, c);
        return false;
    }


    public static int checkFolder(String f, Context context) {
        if (f == null) return 0;

        if (f.startsWith(CifsContexts.SMB_URI_PREFIX) ||
                f.startsWith(NetCopyClientConnectionPool.SSH_URI_PREFIX) ||
                f.startsWith(NetCopyClientConnectionPool.FTP_URI_PREFIX) ||
                f.startsWith(NetCopyClientConnectionPool.FTPS_URI_PREFIX) ||
                f.startsWith(OTGUtil.PREFIX_OTG) ||
                f.startsWith(CloudHandler.CLOUD_PREFIX_BOX) ||
                f.startsWith(CloudHandler.CLOUD_PREFIX_GOOGLE_DRIVE) ||
                f.startsWith(CloudHandler.CLOUD_PREFIX_DROPBOX) ||
                f.startsWith(CloudHandler.CLOUD_PREFIX_ONE_DRIVE) ||
                f.startsWith("content://")) {
            return 1;
        }

        File folder = new File(f);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP &&
                isOnExtSdCard(folder, context)) {
            if (!folder.exists() || !folder.isDirectory()) {
                return 0;
            }
            if (isWritableNormalOrSaf(folder, context)) {
                return 1;
            }
        } else if (Build.VERSION.SDK_INT == 19 &&
                isOnExtSdCard(folder, context)) {
            return 1;
        } else if (folder.canWrite()) {
            return 1;
        } else {
            return 0;
        }
        return 0;
    }

    public static boolean isValidFilename(String text) {
        Pattern filenameRegex = Pattern.compile("[\\\\\\/:\\*\\?\"<>\\|\\x01-\\x1F\\x7F]", Pattern.CASE_INSENSITIVE);
        return !filenameRegex.matcher(text).find() && !".".equals(text) && !"..".equals(text);
    }

    public static String unmapPathForApi30OrAbove(String uriPath) {
        if (uriPath.startsWith(SCHEME_CONTENT)) {
            Uri uri = Uri.parse(uriPath);
            String path = uri.getPath();
            if (path != null) {
                return new File(Environment.getExternalStorageDirectory(), path.substring(path.indexOf("tree/primary:") + "tree/primary:".length())).getAbsolutePath();
            }
        }
        return uriPath;
    }

    public static String remapPathForApi30OrAbove(String path, boolean openDocumentTree) {
        if (path != null) {
            for (String dir : ANDROID_DEVICE_DATA_DIRS) {
                if (path.startsWith(dir) && !path.equals(dir)) {
                    String suffix = path.substring(dir.length());
                    String documentId = STORAGE_PRIMARY + ":" + suffix.substring(1);
                    SafRootHolder.setVolumeLabel(STORAGE_PRIMARY);
                    if (openDocumentTree) {
                        return DocumentsContract.buildDocumentUri(COM_ANDROID_EXTERNALSTORAGE_DOCUMENTS, documentId).toString();
                    } else {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            return DocumentsContract.buildTreeDocumentUri(COM_ANDROID_EXTERNALSTORAGE_DOCUMENTS, documentId).toString();
                        }
                    }
                }
            }
        }
        return path;
    }

    public static long getDeviceStorageRemainingSpace(String volume) {
        if (STORAGE_PRIMARY.equals(volume)) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                return Environment.getExternalStorageDirectory().getFreeSpace();
            } else {
                try {
                    return AppConfig.getInstance().getSystemService(StorageStatsManager.class)
                            .getFreeBytes(StorageManager.UUID_DEFAULT);
                } catch (IOException e) {
                    return -1L;
                    // throw new RuntimeException(e);
                }
            }
        }
        return 0L;
    }
}
