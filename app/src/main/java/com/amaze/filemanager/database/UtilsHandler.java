package com.amaze.filemanager.database;

import android.content.Context;
import android.os.Environment;
import android.widget.Toast;
import com.amaze.filemanager.BuildConfig;
import com.amaze.filemanager.R;
import com.amaze.filemanager.database.models.OperationData;
import com.amaze.filemanager.database.models.utilities.Bookmark;
import com.amaze.filemanager.database.models.utilities.Grid;
import com.amaze.filemanager.database.models.utilities.Hidden;
import com.amaze.filemanager.database.models.utilities.History;
import com.amaze.filemanager.database.models.utilities.SftpEntry;
import com.amaze.filemanager.database.models.utilities.SmbEntry;
import com.googlecode.concurrenttrees.radix.ConcurrentRadixTree;
import com.googlecode.concurrenttrees.radix.node.concrete.DefaultCharArrayNodeFactory;
import com.googlecode.concurrenttrees.radix.node.concrete.voidvalue.VoidValue;
import io.reactivex.schedulers.Schedulers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class UtilsHandler {

    private final Context context;
    private final UtilitiesDatabase utilitiesDatabase;
    private final Logger log = LoggerFactory.getLogger(UtilsHandler.class);

    public enum Operation {
        HISTORY, HIDDEN, LIST, GRID, BOOKMARKS, SMB, SFTP
    }

    public UtilsHandler(Context context, UtilitiesDatabase utilitiesDatabase) {
        this.context = context;
        this.utilitiesDatabase = utilitiesDatabase;
    }

    public void saveToDatabase(OperationData operationData) {
        switch (operationData.getType()) {
            case HIDDEN:
                utilitiesDatabase.hiddenEntryDao().insert(new Hidden(operationData.path))
                        .subscribeOn(Schedulers.io()).subscribe();
                break;
            case HISTORY:
                utilitiesDatabase.historyEntryDao().deleteByPath(operationData.path)
                        .andThen(utilitiesDatabase.historyEntryDao().insert(new History(operationData.path)))
                        .subscribeOn(Schedulers.io()).subscribe();
                break;
            case LIST:
                utilitiesDatabase.listEntryDao().insert(new com.amaze.filemanager.database.models.utilities.List(operationData.path))
                        .subscribeOn(Schedulers.io()).subscribe();
                break;
            case GRID:
                utilitiesDatabase.gridEntryDao().insert(new Grid(operationData.path))
                        .subscribeOn(Schedulers.io()).subscribe();
                break;
            case BOOKMARKS:
                utilitiesDatabase.bookmarkEntryDao().insert(new Bookmark(operationData.name, operationData.path))
                        .subscribeOn(Schedulers.io()).subscribe();
                break;
            case SMB:
                utilitiesDatabase.smbEntryDao().deleteByNameAndPath(operationData.name, operationData.path)
                        .andThen(utilitiesDatabase.smbEntryDao().insert(new SmbEntry(operationData.name, operationData.path)))
                        .subscribeOn(Schedulers.io()).subscribe();
                break;
            case SFTP:
                utilitiesDatabase.sftpEntryDao().deleteByNameAndPath(operationData.name, operationData.path)
                        .andThen(utilitiesDatabase.sftpEntryDao().insert(new SftpEntry(operationData.path, operationData.name,
                                operationData.getHostKey(), operationData.getSshKeyName(), operationData.getSshKey())))
                        .subscribeOn(Schedulers.io()).subscribe();
                break;
            default:
                throw new IllegalStateException("Unidentified operation!");
        }
    }

    public void removeFromDatabase(OperationData operationData) {
        switch (operationData.getType()) {
            case HIDDEN:
                utilitiesDatabase.hiddenEntryDao().deleteByPath(operationData.path)
                        .subscribeOn(Schedulers.io()).subscribe();
                break;
            case HISTORY:
                utilitiesDatabase.historyEntryDao().deleteByPath(operationData.path)
                        .subscribeOn(Schedulers.io()).subscribe();
                break;
            case LIST:
                utilitiesDatabase.listEntryDao().deleteByPath(operationData.path)
                        .subscribeOn(Schedulers.io()).subscribe();
                break;
            case GRID:
                utilitiesDatabase.gridEntryDao().deleteByPath(operationData.path)
                        .subscribeOn(Schedulers.io()).subscribe();
                break;
            case BOOKMARKS:
                removeBookmarksPath(operationData.name, operationData.path);
                break;
            case SMB:
                removeSmbPath(operationData.name, operationData.path);
                break;
            case SFTP:
                removeSftpPath(operationData.name, operationData.path);
                break;
            default:
                throw new IllegalStateException("Unidentified operation!");
        }
    }

    public void addCommonBookmarks() {
        File sd = Environment.getExternalStorageDirectory();
        String[] dirs = {
                new File(sd, Environment.DIRECTORY_DCIM).getAbsolutePath(),
                new File(sd, Environment.DIRECTORY_DOWNLOADS).getAbsolutePath(),
                new File(sd, Environment.DIRECTORY_MOVIES).getAbsolutePath(),
                new File(sd, Environment.DIRECTORY_MUSIC).getAbsolutePath(),
                new File(sd, Environment.DIRECTORY_PICTURES).getAbsolutePath()
        };
        for (String dir : dirs) {
            saveToDatabase(new OperationData(Operation.BOOKMARKS, new File(dir).getName(), dir));
        }
    }

    public void updateSsh(String connectionName, String oldConnectionName, String path,
                          String hostKey, String sshKeyName, String sshKey) {
        utilitiesDatabase.sftpEntryDao().findByName(oldConnectionName)
                .subscribeOn(Schedulers.io())
                .subscribe(entry -> {
                    entry.name=connectionName;
                    entry.path=path;
                    entry.hostKey=hostKey;
                    if (sshKeyName != null && sshKey != null) {
                        entry.sshKeyName=sshKeyName;
                        entry.sshKey=sshKey;
                    }
                    utilitiesDatabase.sftpEntryDao().update(entry).subscribeOn(Schedulers.io()).subscribe();
                });
    }

    public LinkedList<String> getHistoryLinkedList() {
        LinkedList<String> paths = new LinkedList<>();
        for (History history : utilitiesDatabase.historyEntryDao().list()
                .subscribeOn(Schedulers.io()).blockingGet()) {
            paths.add(history.path);
        }
        return paths;
    }

    public ConcurrentRadixTree<VoidValue> getHiddenFilesConcurrentRadixTree() {
        ConcurrentRadixTree<VoidValue> paths = new ConcurrentRadixTree<>(new DefaultCharArrayNodeFactory());
        for (String path : utilitiesDatabase.hiddenEntryDao().listPaths()
                .subscribeOn(Schedulers.io()).blockingGet()) {
            paths.put(path, VoidValue.SINGLETON);
        }
        return paths;
    }

    public ArrayList<String> getListViewList() {
        return new ArrayList<>(utilitiesDatabase.listEntryDao().listPaths()
                .subscribeOn(Schedulers.io()).blockingGet());
    }

    public ArrayList<String> getGridViewList() {
        return new ArrayList<>(utilitiesDatabase.gridEntryDao().listPaths()
                .subscribeOn(Schedulers.io()).blockingGet());
    }

    public ArrayList<String[]> getBookmarksList() {
        ArrayList<String[]> row = new ArrayList<>();
        for (Bookmark bookmark : utilitiesDatabase.bookmarkEntryDao().list()
                .subscribeOn(Schedulers.io()).blockingGet()) {
            row.add(new String[]{bookmark.name, bookmark.path});
        }
        return row;
    }

    public ArrayList<String[]> getSmbList() {
        ArrayList<String[]> retval = new ArrayList<>();
        for (SmbEntry entry : utilitiesDatabase.smbEntryDao().list()
                .subscribeOn(Schedulers.io()).blockingGet()) {
            retval.add(new String[]{entry.name, entry.path});
        }
        return retval;
    }

    public List<String[]> getSftpList() {
        ArrayList<String[]> retval = new ArrayList<>();
        for (SftpEntry entry : utilitiesDatabase.sftpEntryDao().list()
                .subscribeOn(Schedulers.io()).blockingGet()) {
            String path = entry.path;
            if (path == null) {
                log.error("Error decrypting path: " + entry.path);
                Toast.makeText(context, context.getString(R.string.failed_smb_decrypt_path), Toast.LENGTH_LONG).show();
            } else {
                retval.add(new String[]{entry.name, path});
            }
        }
        return retval;
    }

    public String getRemoteHostKey(String uri) {
        try {
            return utilitiesDatabase.sftpEntryDao().getRemoteHostKey(uri)
                    .subscribeOn(Schedulers.io()).blockingGet();
        } catch (Exception e) {
            if (BuildConfig.DEBUG) {
                log.warn("Error getting public key for URI [" + uri + "]", e);
            }
            return null;
        }
    }

    public String getSshAuthPrivateKeyName(String uri) {
        try {
            return utilitiesDatabase.sftpEntryDao().getSshAuthPrivateKeyName(uri)
                    .subscribeOn(Schedulers.io()).blockingGet();
        } catch (Exception e) {
            log.error("Error getting SSH private key name", e);
            return null;
        }
    }

    public String getSshAuthPrivateKey(String uri) {
        try {
            return utilitiesDatabase.sftpEntryDao().getSshAuthPrivateKey(uri)
                    .subscribeOn(Schedulers.io()).blockingGet();
        } catch (Exception e) {
            if (BuildConfig.DEBUG) {
                log.error("Error getting auth private key for URI [" + uri + "]", e);
            }
            return null;
        }
    }

    private void removeBookmarksPath(String name, String path) {
        utilitiesDatabase.bookmarkEntryDao().deleteByNameAndPath(name, path)
                .subscribeOn(Schedulers.io()).subscribe();
    }

    private void removeSmbPath(String name, String path) {
        if ("".equals(path)) {
            utilitiesDatabase.smbEntryDao().deleteByName(name)
                    .subscribeOn(Schedulers.io()).subscribe();
        } else {
            utilitiesDatabase.smbEntryDao().deleteByNameAndPath(name, path)
                    .subscribeOn(Schedulers.io()).subscribe();
        }
    }

    private void removeSftpPath(String name, String path) {
        if ("".equals(path)) {
            utilitiesDatabase.sftpEntryDao().deleteByName(name)
                    .subscribeOn(Schedulers.io()).subscribe();
        } else {
            utilitiesDatabase.sftpEntryDao().deleteByNameAndPath(name, path)
                    .subscribeOn(Schedulers.io()).subscribe();
        }
    }

    public void renameBookmark(String oldName, String oldPath, String newName, String newPath) {
        Bookmark bookmark = utilitiesDatabase.bookmarkEntryDao().findByNameAndPath(oldName, oldPath)
                .subscribeOn(Schedulers.io()).blockingGet();
        bookmark.name=newName;
        bookmark.path=newPath;
        utilitiesDatabase.bookmarkEntryDao().update(bookmark).subscribeOn(Schedulers.io()).subscribe();
    }

    public void renameSMB(String oldName, String oldPath, String newName, String newPath) {
        utilitiesDatabase.smbEntryDao().findByNameAndPath(oldName, oldPath)
                .subscribeOn(Schedulers.io()).subscribe(smbEntry -> {
                    smbEntry.name=newName;
                    smbEntry.path=newPath;
                    utilitiesDatabase.smbEntryDao().update(smbEntry).subscribeOn(Schedulers.io()).subscribe();
                });
    }

    public void clearTable(Operation table) {
        switch (table) {
            case HISTORY:
                utilitiesDatabase.historyEntryDao().clear()
                        .subscribeOn(Schedulers.io()).subscribe();
                break;
            default:
                break;
        }
    }
}

