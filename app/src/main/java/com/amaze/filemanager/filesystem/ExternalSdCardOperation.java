package com.amaze.filemanager.filesystem;

import android.annotation.TargetApi;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import androidx.documentfile.provider.DocumentFile;
import androidx.preference.PreferenceManager;
import com.amaze.filemanager.database.UtilsHandler;
import com.amaze.filemanager.ui.fragments.preferencefragments.PreferencesConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ExternalSdCardOperation {
    private static final Logger log = LoggerFactory.getLogger(UtilsHandler.class);

    /**
     * Get a DocumentFile corresponding to the given file (for writing on ExtSdCard on Android 5). If
     * the file is not existing, it is created.
     *
     * @param file        The file.
     * @param isDirectory Flag indicating if the file should be a directory.
     * @param context     The context.
     * @return The DocumentFile
     */
    public static DocumentFile getDocumentFile(File file, boolean isDirectory, Context context) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
            return DocumentFile.fromFile(file);
        }

        File baseFolder = new File(getExtSdCardFolder(file, context));
        if (baseFolder == null) {
            return null;
        }

        boolean originalDirectory = false;
        String relativePath = null;
        try {
            String fullPath = file.getCanonicalPath();
            if (!baseFolder.equals(fullPath)) {
                relativePath = fullPath.substring((int) (baseFolder.length() + 1));
            } else {
                originalDirectory = true;
            }
        } catch (IOException e) {
            return null;
        }

        String preferenceUri = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(PreferencesConstants.PREFERENCE_URI, null);
        Uri treeUri = preferenceUri != null ? Uri.parse(preferenceUri) : null;

        // Start with the root of SD card and then parse through the document tree.
        DocumentFile document = DocumentFile.fromTreeUri(context, treeUri);
        if (originalDirectory || relativePath == null) {
            return document;
        }

        String[] parts = relativePath.split("/");
        for (String part : parts) {
            if (document == null) {
                return null;
            }

            DocumentFile nextDocument = document.findFile(part);
            if (nextDocument == null) {
                if (isDirectory || !part.contains(".")) {
                    nextDocument = document.createDirectory(part);
                } else {
                    nextDocument = document.createFile("image", part);
                }
            }
            document = nextDocument;
        }

        return document;
    }

    /**
     * Get a list of external SD card paths. (Kitkat or higher.)
     *
     * @param context The context.
     * @return A list of external SD card paths.
     */
    @TargetApi(Build.VERSION_CODES.KITKAT)
    public static String[] getExtSdCardPaths(Context context) {
        List<String> paths = new ArrayList<>();
        for (File file : context.getExternalFilesDirs("external")) {
            if (file != null && !file.equals(context.getExternalFilesDir("external"))) {
                int index = file.getAbsolutePath().lastIndexOf("/Android/data");
                if (index < 0) {
                    log.warn("Unexpected external file dir: " + file.getAbsolutePath());
                } else {
                    String path = file.getAbsolutePath().substring(0, index);
                    try {
                        path = new File(path).getCanonicalPath();
                    } catch (IOException e) {
                        // Keep non-canonical path.
                    }
                    paths.add(path);
                }
            }
        }
        if (paths.isEmpty()) {
            paths.add("/storage/sdcard1");
        }
        return paths.toArray(new String[0]);
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    public static String[] getExtSdCardPathsForActivity(Context context) {
        List<String> paths = new ArrayList<>();
        for (File file : context.getExternalFilesDirs("external")) {
            if (file != null) {
                int index = file.getAbsolutePath().lastIndexOf("/Android/data");
                if (index < 0) {
                    log.warn("Unexpected external file dir: " + file.getAbsolutePath());
                } else {
                    String path = file.getAbsolutePath().substring(0, index);
                    try {
                        path = new File(path).getCanonicalPath();
                    } catch (IOException e) {
                        // Keep non-canonical path.
                    }
                    paths.add(path);
                }
            }
        }
        if (paths.isEmpty()) {
            paths.add("/storage/sdcard1");
        }
        return paths.toArray(new String[0]);
    }

    /**
     * Determine the main folder of the external SD card containing the given file.
     *
     * @param file    The file.
     * @param context The context.
     * @return The main folder of the external SD card containing this file, if the file is on an SD card. Otherwise, null is returned.
     */
    @TargetApi(Build.VERSION_CODES.KITKAT)
    public static String getExtSdCardFolder(File file, Context context) {
        String[] extSdPaths = getExtSdCardPaths(context);
        try {
            for (String path : extSdPaths) {
                if (file.getCanonicalPath().startsWith(path)) {
                    return path;
                }
            }
        } catch (IOException e) {
            return null;
        }
        return null;
    }

    /**
     * Determine if a file is on an external SD card. (Kitkat or higher.)
     *
     * @param file    The file.
     * @param context The context.
     * @return True if on an external SD card.
     */
    @TargetApi(Build.VERSION_CODES.KITKAT)
    public static boolean isOnExtSdCard(File file, Context context) {
        return getExtSdCardFolder(file, context) != null;
    }
}

