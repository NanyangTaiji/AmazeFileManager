package com.amaze.filemanager.filesystem;

import android.content.Context;
import android.os.Build;

import androidx.documentfile.provider.DocumentFile;

import com.amaze.filemanager.ui.icons.MimeTypes;
import com.amaze.filemanager.utils.AppConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

public class MakeFileOperation {
    private static final Logger log = LoggerFactory.getLogger(MakeFileOperation.class);

    /**
     * Get a temp file.
     *
     * @param file The base file for which to create a temp file.
     * @param context The context.
     * @return The temp file.
     */
    public static File getTempFile(File file, Context context) {
        File extDir = context.getExternalFilesDir(null);
        return new File(extDir, file.getName());
    }

    public static boolean mkfile(File file, Context context) {
        if (file == null) return false;
        if (file.exists()) {
            // nothing to create.
            return !file.isDirectory();
        }

        // Try the normal way
        try {
            if (file.createNewFile()) {
                return true;
            }
        } catch (IOException e) {
            log.warn("failed to make file", e);
        }

        // Try with Storage Access Framework.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP &&
                ExternalSdCardOperation.isOnExtSdCard(file, context)) {
            try {
                DocumentFile document =
                        ExternalSdCardOperation.getDocumentFile(file.getParentFile(), true, context);
                // getDocumentFile implicitly creates the directory.
                if (document != null && document.createFile(
                        MimeTypes.getMimeType(file.getPath(), file.isDirectory()),
                        file.getName()) != null) {
                    return true;
                }
            } catch (UnsupportedOperationException e) {
                log.warn("Failed to create file on sd card using document file", e);
                return false;
            }
        }

        return Build.VERSION.SDK_INT == Build.VERSION_CODES.KITKAT &&
                MediaStoreHack.mkfile(context, file);
    }

    public static boolean mktextfile(String data, String path, String fileName) {
        File f = new File(path, fileName + AppConstants.NEW_FILE_DELIMITER + AppConstants.NEW_FILE_EXTENSION_TXT);
        FileOutputStream out = null;
        OutputStreamWriter outputWriter = null;
        try {
            if (f.createNewFile()) {
                out = new FileOutputStream(f, false);
                outputWriter = new OutputStreamWriter(out);
                outputWriter.write(data);
                return true;
            } else {
                return false;
            }
        } catch (IOException io) {
            log.warn("Error writing file contents", io);
            return false;
        } finally {
            try {
                if (outputWriter != null) {
                    outputWriter.flush();
                    outputWriter.close();
                }
                if (out != null) {
                    out.flush();
                    out.close();
                }
            } catch (IOException e) {
                log.warn("Error closing file output stream", e);
            }
        }
    }
}

