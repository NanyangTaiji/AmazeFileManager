package com.amaze.filemanager.filesystem;

import android.content.Context;
import android.os.Build;

import androidx.documentfile.provider.DocumentFile;

import com.amaze.filemanager.utils.OTGUtil;
import jcifs.smb.SmbException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.io.IOException;

public class MakeDirectoryOperation {
    private static final Logger log = LoggerFactory.getLogger(MakeDirectoryOperation.class);

    /**
     * Create a folder. The folder may even be on an external SD card for Kitkat.
     *
     * @param file    The folder to be created.
     * @param context The context.
     * @return True if creation was successful.
     */
    @Deprecated
    public static boolean mkdir(File file, Context context) {
        if (file == null) return false;
        if (file.exists()) {
            // nothing to create.
            return file.isDirectory();
        }

        // Try the normal way
        if (file.mkdirs()) {
            return true;
        }

        // Try with Storage Access Framework.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP &&
                ExternalSdCardOperation.isOnExtSdCard(file, context)) {
            DocumentFile document = ExternalSdCardOperation.getDocumentFile(file, true, context);
            if (document == null) return false;
            // getDocumentFile implicitly creates the directory.
            return document.exists();
        }

        // Try the Kitkat workaround.
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.KITKAT) {
            try {
                return MediaStoreHack.mkdir(context, file);
            } catch (IOException e) {
                return false;
            }
        } else {
            return false;
        }
    }

    /**
     * Creates the directories on the given [file] path, including nonexistent parent directories.
     * So use the proper [HybridFile] constructor as per your need.
     *
     * @param context The context.
     * @param file    The HybridFile object representing the directory to be created.
     * @return True if the directory was successfully created, otherwise false.
     */
    public static boolean mkdirs(Context context, HybridFile file) {
        boolean isSuccessful = true;
        switch (file.getMode()) {
            case SMB:
                try {
                    jcifs.smb.SmbFile smbFile = file.getSmbFile();
                    smbFile.mkdirs();
                } catch (SmbException e) {
                    log.warn("failed to make directory in smb", e);
                    isSuccessful = false;
                }
                break;
            case OTG:
                DocumentFile documentFile =
                        OTGUtil.getDocumentFile(file.getPath(), context, true);
                isSuccessful = documentFile != null;
                break;
            case FILE:
                isSuccessful = mkdir(new File(file.getPath()), context);
                break;
            case ANDROID_DATA:
                isSuccessful = false; // With ANDROID_DATA will not accept create directory
                break;
            default:
                isSuccessful = true;
        }
        return isSuccessful;
    }
}

