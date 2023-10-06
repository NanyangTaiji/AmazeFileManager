package com.amaze.filemanager.adapters.glide.cloudicon;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import com.amaze.filemanager.filesystem.cloud.CloudUtil;
import com.bumptech.glide.Priority;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.data.DataFetcher;
import java.io.IOException;
import java.io.InputStream;

public class CloudIconDataFetcher implements DataFetcher<Bitmap> {

    private static final String TAG = CloudIconDataFetcher.class.getSimpleName();
    private Context context;
    private String path;
    private int width;
    private int height;
    private InputStream inputStream;

    public CloudIconDataFetcher(Context context, String path, int width, int height) {
        this.context = context;
        this.path = path;
        this.width = width;
        this.height = height;
    }

    @Override
    public void loadData(Priority priority, DataCallback<? super Bitmap> callback) {
        inputStream = CloudUtil.getThumbnailInputStreamForCloud(context, path);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.outWidth = width;
        options.outHeight = height;
        Bitmap drawable = BitmapFactory.decodeStream(inputStream, null, options);
        callback.onDataReady(drawable);
    }

    @Override
    public void cleanup() {
        try {
            if (inputStream != null) {
                inputStream.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "Error cleaning up cloud icon fetch", e);
        }
    }

    @Override
    public void cancel() {
        // No operation
    }

    @Override
    public Class<Bitmap> getDataClass() {
        return Bitmap.class;
    }

    @Override
    public DataSource getDataSource() {
        return DataSource.REMOTE;
    }
}

