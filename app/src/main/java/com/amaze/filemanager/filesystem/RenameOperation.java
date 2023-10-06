package com.amaze.filemanager.filesystem;

import android.content.Context;
import android.os.Build;
import android.util.Log;
import com.amaze.filemanager.fileoperations.exceptions.ShellNotRunningException;
import androidx.documentfile.provider.DocumentFile;
import java.io.*;
import java.nio.channels.FileChannel;

public class RenameOperation {
    private static final String LOG = "RenameOperation";

    private static boolean copyFile(File source, File target, Context context) {
        FileInputStream inStream = null;
        OutputStream outStream = null;
        FileChannel inChannel = null;
        FileChannel outChannel = null;
        try {
            inStream = new FileInputStream(source);

            // First try the normal way
            if (FileProperties.isWritable(target)) {
                // standard way
                outStream = new FileOutputStream(target);
                inChannel = inStream.getChannel();
                outChannel = ((FileOutputStream) outStream).getChannel();
                inChannel.transferTo(0, inChannel.size(), outChannel);
            } else {
                outStream = buildOutputStream(target, context);
                if (outStream != null) {
                    // Both for SAF and for Kitkat, write to output stream.
                    byte[] buffer = new byte[16384]; // MAGIC_NUMBER
                    int bytesRead;
                    while ((bytesRead = inStream.read(buffer)) != -1) {
                        outStream.write(buffer, 0, bytesRead);
                    }
                } else {
                    return false;
                }
            }
        } catch (IOException e) {
            Log.e(LOG, "Error when copying file from " + source.getAbsolutePath() + " to " + target.getAbsolutePath(), e);
            return false;
        } finally {
            try {
                if (inStream != null) {
                    inStream.close();
                }
                if (outStream != null) {
                    outStream.close();
                }
                if (inChannel != null) {
                    inChannel.close();
                }
                if (outChannel != null) {
                    outChannel.close();
                }
            } catch (IOException e) {
                // ignore exception
            }
        }
        return true;
    }

    private static OutputStream buildOutputStream(File target, Context context) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // Storage Access Framework
            return context.getContentResolver().openOutputStream(ExternalSdCardOperation.getDocumentFile(target, false, context).getUri());
        } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.KITKAT) {
            // Workaround for Kitkat ext SD card
            return context.getContentResolver().openOutputStream(MediaStoreHack.getUriFromFile(target.getAbsolutePath(), context));
        }
        return null;
    }

    private static boolean rename(File f, String name, boolean root) throws ShellNotRunningException {
        String parentName = f.getParent();
        File parentFile = f.getParentFile();

        String newPath = parentName + "/" + name;
        if (parentFile.canWrite()) {
            return f.renameTo(new File(newPath));
        } else if (root) {
            //TODO ny
            //renameFile(f.getAbsolutePath(), newPath);
            return true;
        }
        return false;
    }

    public static boolean renameFolder(File source, File target, Context context) throws ShellNotRunningException {
        // First try the normal rename.
        if (rename(source, target.getName(), false)) {
            return true;
        }
        if (target.exists()) {
            return false;
        }

        // Try the Storage Access Framework if it is just a rename within the same parent folder.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP &&
                source.getParent().equals(target.getParent()) &&
                ExternalSdCardOperation.isOnExtSdCard(source, context)) {
            DocumentFile document = ExternalSdCardOperation.getDocumentFile(source, true, context);
            if (document != null && document.renameTo(target.getName())) {
                return true;
            }
        }

        // Try the manual way, moving files individually.
        if (!MakeDirectoryOperation.mkdir(target, context)) {
            return false;
        }
        File[] sourceFiles = source.listFiles();
        if (sourceFiles != null) {
            for (File sourceFile : sourceFiles) {
                File targetFile = new File(target, sourceFile.getName());
                if (!copyFile(sourceFile, targetFile, context)) {
                    // stop on first error
                    return false;
                }
            }
            // Only after successfully copying all files, delete files in the source folder.
            for (File sourceFile : sourceFiles) {
                if (!DeleteOperation.deleteFile(sourceFile, context)) {
                    // stop on first error
                    return false;
                }
            }
        }
        return true;
    }
}

