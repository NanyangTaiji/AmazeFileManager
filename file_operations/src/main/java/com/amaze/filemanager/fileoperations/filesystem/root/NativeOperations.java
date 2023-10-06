package com.amaze.filemanager.fileoperations.filesystem.root;

public class NativeOperations {

    static {
        System.loadLibrary("rootoperations");
    }

    /** Whether path file is directory or not  */
    public static native boolean isDirectory(String path);
}
