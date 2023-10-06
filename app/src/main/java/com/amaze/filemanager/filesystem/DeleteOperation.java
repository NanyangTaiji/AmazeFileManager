package com.amaze.filemanager.filesystem;

import android.content.ContentValues;
import android.content.Context;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Log;

import androidx.documentfile.provider.DocumentFile;

import java.io.File;

public class DeleteOperation {
    private static final String LOG = "DeleteFileOperation";

    /**
     * Recursively delete a folder.
     *
     * @param file    The folder to be deleted.
     * @param context The context.
     * @return true if successful.
     */
    private static boolean rmdir(File file, Context context) {
        if (!file.exists()) return true;
        File[] files = file.listFiles();
        if (files != null && files.length > 0) {
            for (File child : files) {
                rmdir(child, context);
            }
        }

        // Try the normal way
        if (file.delete()) {
            return true;
        }

        // Try with Storage Access Framework.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            DocumentFile document = ExternalSdCardOperation.getDocumentFile(file, true, context);
            if (document != null && document.delete()) {
                return true;
            }
        }

        // Try the Kitkat workaround.
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.KITKAT) {
            try {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DATA, file.getAbsolutePath());
                context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

                // Delete the created entry, such that content provider will delete the file.
                context.getContentResolver().delete(
                        MediaStore.Files.getContentUri("external"),
                        MediaStore.MediaColumns.DATA + "=?",
                        new String[]{file.getAbsolutePath()}
                );
            } catch (Exception e) {
                Log.e(LOG, "Exception when deleting file " + file.getAbsolutePath(), e);
            }
        }
        return !file.exists();
    }

    /**
     * Delete a file. May be even on an external SD card.
     *
     * @param file    The file to be deleted.
     * @param context The context.
     * @return True if successfully deleted.
     */
    public static boolean deleteFile(File file, Context context) {
        // First try the normal deletion.
        boolean fileDelete = rmdir(file, context);
        if (file.delete() || fileDelete) return true;

        // Try with Storage Access Framework.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP &&
                ExternalSdCardOperation.isOnExtSdCard(file, context)) {
            DocumentFile document = ExternalSdCardOperation.getDocumentFile(file, false, context);
            if (document != null && document.delete()) {
                return true;
            }
        }

        // Try the Kitkat workaround.
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.KITKAT) {
            try {
                android.net.Uri uri = MediaStoreHack.getUriFromFile(file.getAbsolutePath(), context);
                if (uri != null) {
                    context.getContentResolver().delete(uri, null, null);
                    return !file.exists();
                } else {
                    return false;
                }
            } catch (SecurityException e) {
                Log.e(LOG, "Security exception when checking for file " + file.getAbsolutePath(), e);
                return false;
            }
        }
        return !file.exists();
    }
}

