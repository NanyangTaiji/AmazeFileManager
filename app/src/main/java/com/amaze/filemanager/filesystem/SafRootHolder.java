package com.amaze.filemanager.filesystem;

import android.net.Uri;

public class SafRootHolder {
    private static Uri uriRoot = null;
    private static String volumeLabel = null;

    public static Uri getUriRoot() {
        return uriRoot;
    }

    public static void setUriRoot(Uri uri) {
        SafRootHolder.uriRoot = uri;
    }

    public static String getVolumeLabel() {
        return volumeLabel;
    }

    public static void setVolumeLabel(String label) {
        SafRootHolder.volumeLabel = label;
    }
}

