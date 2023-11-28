package com.amaze.filemanager.asynchronous.services;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.IBinder;
import android.widget.RemoteViews;
import androidx.annotation.StringRes;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.preference.PreferenceManager;
import com.amaze.filemanager.R;
import com.amaze.filemanager.application.AppConfig;
import com.amaze.filemanager.asynchronous.management.ServiceWatcherUtil;
import com.amaze.filemanager.filesystem.FileUtil;
import com.amaze.filemanager.filesystem.HybridFileParcelable;
import com.amaze.filemanager.filesystem.files.FileUtils;
import com.amaze.filemanager.filesystem.files.GenericCopyUtil;
import com.amaze.filemanager.ui.activities.MainActivity;
import com.amaze.filemanager.ui.notifications.NotificationConstants;
import com.amaze.filemanager.utils.DatapointParcelable;
import com.amaze.filemanager.utils.ObtainableServiceBinder;
import com.amaze.filemanager.utils.ProgressHandler;
import io.reactivex.Completable;
import io.reactivex.CompletableEmitter;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipOutputStream;

public class ZipService extends AbstractProgressiveService {
    private Logger log = LoggerFactory.getLogger(ZipService.class);
    private ObtainableServiceBinder mBinder = new ObtainableServiceBinder(this);
    private CompositeDisposable disposables = new CompositeDisposable();
    private NotificationManagerCompat mNotifyManager;
    private NotificationCompat.Builder mBuilder;
    private ProgressListener progressListener = null;
    private ProgressHandler progressHandler = new ProgressHandler();
    private ArrayList<DatapointParcelable> dataPackages = new ArrayList<>();
    private int accentColor = 0;
    private SharedPreferences sharedPreferences = null;
    private RemoteViews customSmallContentViews = null;
    private RemoteViews customBigContentViews = null;

    @Override
    public void onCreate() {
        super.onCreate();
        registerReceiver(receiver1, new IntentFilter(KEY_COMPRESS_BROADCAST_CANCEL));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String mZipPath = intent.getStringExtra(KEY_COMPRESS_PATH);
        ArrayList<HybridFileParcelable> baseFiles = intent.getParcelableArrayListExtra(KEY_COMPRESS_FILES);
        File zipFile = new File(mZipPath);
        mNotifyManager = NotificationManagerCompat.from(getApplicationContext());
        if (!zipFile.exists()) {
            try {
                zipFile.createNewFile();
            } catch (IOException e) {
                log.warn("failed to create zip file", e);
            }
        }
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        accentColor = ((AppConfig) getApplication()).getUtilsProvider().getColorPreference()
                .getCurrentUserColorPreferences(this, sharedPreferences).getAccent();

        Intent notificationIntent = new Intent(this, MainActivity.class)
                .putExtra(MainActivity.KEY_INTENT_PROCESS_VIEWER, true);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT
        );

        customSmallContentViews = new RemoteViews(getPackageName(), R.layout.notification_service_small);
        customBigContentViews = new RemoteViews(getPackageName(), R.layout.notification_service_big);

        Intent stopIntent = new Intent(KEY_COMPRESS_BROADCAST_CANCEL);
        PendingIntent stopPendingIntent = PendingIntent.getBroadcast(
                getApplicationContext(),
                1234,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT
        );
        NotificationCompat.Action action = new NotificationCompat.Action(
                R.drawable.ic_zip_box_grey,
                getString(R.string.stop_ftp),
                stopPendingIntent
        );
        mBuilder = new NotificationCompat.Builder(this, NotificationConstants.CHANNEL_NORMAL_ID)
                .setSmallIcon(R.drawable.ic_zip_box_grey)
                .setContentIntent(pendingIntent)
                .setCustomContentView(customSmallContentViews)
                .setCustomBigContentView(customBigContentViews)
                .setCustomHeadsUpContentView(customSmallContentViews)
                .setStyle(new NotificationCompat.DecoratedCustomViewStyle())
                .addAction(action)
                .setOngoing(true)
                .setColor(accentColor);

        NotificationConstants.setMetadata(this, mBuilder, NotificationConstants.TYPE_NORMAL);
        startForeground(NotificationConstants.ZIP_ID, mBuilder.build());
        initNotificationViews();
        super.onStartCommand(intent, flags, startId);
        super.progressHalted();
        CompressTask zipTask = new CompressTask(this, baseFiles, zipFile.getAbsolutePath());
        disposables.add(zipTask.compress());
        // If we get killed, after returning from here, restart
        return START_NOT_STICKY;
    }

    @Override
    public NotificationManagerCompat getNotificationManager() {
        return mNotifyManager;
    }

    @Override
    public NotificationCompat.Builder getNotificationBuilder() {
        return mBuilder;
    }

    @Override
    public int getNotificationId() {
        return NotificationConstants.ZIP_ID;
    }

    @StringRes
    @Override
    public int getTitle(boolean move) {
        return R.string.compressing;
    }

    @Override
    public RemoteViews getNotificationCustomViewSmall() {
        return customSmallContentViews;
    }

    @Override
    public RemoteViews getNotificationCustomViewBig() {
        return customBigContentViews;
    }

    @Override
    public ProgressListener getProgressListener() {
        return progressListener;
    }

    @Override
    public void setProgressListener(ProgressListener progressListener) {
        this.progressListener = progressListener;
    }

    @Override
    public ArrayList<DatapointParcelable> getDataPackages() {
        return dataPackages;
    }

    @Override
    public ProgressHandler getProgressHandler() {
        return progressHandler;
    }

    @Override
    public void clearDataPackages() {
        dataPackages.clear();
    }

    private class CompressTask {
        private ZipService zipService;
        private ArrayList<HybridFileParcelable> baseFiles;
        private String zipPath;
        private ZipOutputStream zos;
        private ServiceWatcherUtil watcherUtil;

        public CompressTask(ZipService zipService, ArrayList<HybridFileParcelable> baseFiles, String zipPath) {
            this.zipService = zipService;
            this.baseFiles = baseFiles;
            this.zipPath = zipPath;
        }

        public Disposable compress() {
            return Completable.create(emitter -> {
                        int totalBytes = (int) FileUtils.getTotalBytes(baseFiles, zipService.getApplicationContext());
                        progressHandler.setSourceSize(baseFiles.size());
                        progressHandler.setTotalSize(totalBytes);

                        progressHandler.setProgressListener(speed -> {
                            publishResults(speed, false, false);
                        });
                        zipService.addFirstDatapoint(
                                baseFiles.get(0).getName(zipService.getApplicationContext()),
                                baseFiles.size(),
                                totalBytes,
                                false
                        );
                        execute(
                                emitter,
                                zipService.getApplicationContext(),
                                FileUtils.hybridListToFileArrayList(baseFiles),
                                zipPath
                        );

                        emitter.onComplete();
                    })
                    .subscribeOn(Schedulers.io())
                    .observeOn(Schedulers.single())
                    .subscribe(
                            () -> {
                                watcherUtil.stopWatch();
                                Intent intent = new Intent(MainActivity.KEY_INTENT_LOAD_LIST)
                                        .putExtra(MainActivity.KEY_INTENT_LOAD_LIST_FILE, zipPath);
                                zipService.sendBroadcast(intent);
                                zipService.stopSelf();
                            },
                            throwable -> log.error(throwable.getMessage() != null ? throwable.getMessage() : "ZipService.CompressAsyncTask.compress failed")
                    );
        }

        public void cancel() {
            progressHandler.setCancelled(true);
            File zipFile = new File(zipPath);
            if (zipFile.exists()) {
                zipFile.delete();
            }
        }

        public void execute(CompletableEmitter emitter, Context context, ArrayList<File> baseFiles, String zipPath) {
            OutputStream out = null;
            File zipDirectory = new File(zipPath);
            watcherUtil = new ServiceWatcherUtil(progressHandler);
            watcherUtil.watch(zipService);
            try {
                out = FileUtil.getOutputStream(zipDirectory, context);
                zos = new ZipOutputStream(new BufferedOutputStream(out));
                for (int i = 0; i < baseFiles.size(); i++) {
                    if (emitter.isDisposed()) {
                        return;
                    }
                    progressHandler.setFileName(baseFiles.get(i).getName());
                    progressHandler.setSourceFilesProcessed(i + 1);
                    compressFile(baseFiles.get(i), "");
                }
            } catch (IOException e) {
                log.warn("failed to zip file", e);
            } finally {
                try {
                    if (zos != null) {
                        zos.flush();
                        zos.close();
                    }
                    context.sendBroadcast(
                            new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                                    .setData(Uri.fromFile(zipDirectory))
                    );
                } catch (IOException e) {
                    log.warn("failed to close zip streams", e);
                }
            }
        }

        private void compressFile(File file, String path) throws IOException {
            if (progressHandler.getCancelled()) {
                return;
            }
            if (!file.isDirectory()) {
                zos.putNextEntry(createZipEntry(file, path));
                byte[] buf = new byte[GenericCopyUtil.DEFAULT_BUFFER_SIZE];
                int len;
                try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file))) {
                    while ((len = bis.read(buf)) > 0) {
                        if (!progressHandler.getCancelled()) {
                            zos.write(buf, 0, len);
                            ServiceWatcherUtil.position += len;
                        } else {
                            break;
                        }
                    }
                }
                return;
            }
            File[] files = file.listFiles();
            if (files != null) {
                for (File subFile : files) {
                    compressFile(subFile, createZipEntryPrefixWith(path) + file.getName());
                }
            }
        }
    }

    private String createZipEntryPrefixWith(String path) {
        return path.isEmpty() ? path : path + "/";
    }

    private ZipEntry createZipEntry(File file, String path) {
        ZipEntry zipEntry = new ZipEntry(createZipEntryPrefixWith(path) + file.getName());
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            try {
                BasicFileAttributes attrs = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
                zipEntry.setCreationTime(attrs.creationTime());
                zipEntry.setLastAccessTime(attrs.lastAccessTime());
                zipEntry.setLastModifiedTime(FileTime.fromMillis(attrs.lastModifiedTime().toMillis()));
            } catch (IOException e) {
                log.warn("Failed to set file attributes for zip entry", e);
            }
        } else {
            zipEntry.setTime(file.lastModified());
        }
        return zipEntry;
    }

    private BroadcastReceiver receiver1 = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            progressHandler.setCancelled(true);
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(receiver1);
        disposables.dispose();
    }

    public static final String KEY_COMPRESS_PATH = "zip_path";
    public static final String KEY_COMPRESS_FILES = "zip_files";
    public static final String KEY_COMPRESS_BROADCAST_CANCEL = "zip_cancel";
}

