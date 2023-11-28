package com.amaze.filemanager.database;

import com.amaze.filemanager.application.AppConfig;
import com.amaze.filemanager.database.models.explorer.EncryptedEntry;
import io.reactivex.schedulers.Schedulers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;

public class CryptHandler {

    private static final Logger log = LoggerFactory.getLogger(CryptHandler.class);
    private static final ExplorerDatabase database = AppConfig.getInstance().getExplorerDatabase();

    private CryptHandler() {
    }

    // Static inner class holding the Singleton instance
    private static class CryptHandlerHolder {
        private static final CryptHandler INSTANCE = new CryptHandler();
    }

    // Public method to get the Singleton instance
    public static CryptHandler getInstance() {
        return CryptHandlerHolder.INSTANCE;
    }


    /**
     * Add {@link EncryptedEntry} to database.
     */
    public static void addEntry(EncryptedEntry encryptedEntry) {
        database.encryptedEntryDao().insert(encryptedEntry)
                .subscribeOn(Schedulers.io())
                .subscribe();
    }

    /**
     * Remove {@link EncryptedEntry} of specified path.
     */
    public static void clear(String path) {
        database.encryptedEntryDao().delete(path)
                .subscribeOn(Schedulers.io())
                .subscribe();
    }

    /**
     * Update specified new {@link EncryptedEntry} in database.
     */
    public static void updateEntry(EncryptedEntry oldEncryptedEntry, EncryptedEntry newEncryptedEntry) {
        database.encryptedEntryDao().update(newEncryptedEntry)
                .subscribeOn(Schedulers.io())
                .subscribe();
    }

    /**
     * Find {@link EncryptedEntry} of specified path. Returns null if not exist.
     */
    public static EncryptedEntry findEntry(String path) {
        try {
            return database.encryptedEntryDao().select(path)
                    .subscribeOn(Schedulers.io())
                    .blockingGet();
        } catch (Exception e) {
            log.error(e.getMessage());
            return null;
        }
    }

    public static EncryptedEntry[] getAllEntries() {
        List<EncryptedEntry> encryptedEntryList = database.encryptedEntryDao().list()
                .subscribeOn(Schedulers.io())
                .blockingGet();
        return encryptedEntryList.toArray(new EncryptedEntry[0]);
    }
}

