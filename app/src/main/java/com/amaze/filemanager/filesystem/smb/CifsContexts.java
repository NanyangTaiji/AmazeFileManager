package com.amaze.filemanager.filesystem.smb;

import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import jcifs.CIFSException;
import jcifs.config.PropertyConfiguration;
import jcifs.context.BaseContext;
import jcifs.context.SingletonContext;
import io.reactivex.Single;
import io.reactivex.schedulers.Schedulers;

/*
In this Java code, the CifsContexts class contains static methods and constants
to mimic the behavior of the provided Kotlin object. Default parameter values
are handled through method overloading, and properties are initialized using double brace
initialization. The contexts map is a ConcurrentHashMap to handle concurrent access.

 */

public class CifsContexts {

    public static final String SMB_URI_PREFIX = "smb://";

    private static final String TAG = CifsContexts.class.getSimpleName();

    private static final Properties defaultProperties = new Properties() {{
        setProperty("jcifs.resolveOrder", "BCAST");
        setProperty("jcifs.smb.client.responseTimeout", "30000");
        setProperty("jcifs.netbios.retryTimeout", "5000");
        setProperty("jcifs.netbios.cachePolicy", "-1");
    }};

    private static final ConcurrentHashMap<String, BaseContext> contexts = new ConcurrentHashMap<>();

    public static void clearBaseContexts() {
        for (Map.Entry<String, BaseContext> entry : contexts.entrySet()) {
            try {
                entry.getValue().close();
            } catch (CIFSException e) {
                Log.w(TAG, "Error closing SMB connection", e);
            }
        }
        contexts.clear();
    }

    public static BaseContext createWithDisableIpcSigningCheck(String basePath, boolean disableIpcSigningCheck) {
        if (disableIpcSigningCheck) {
            Properties extraProperties = new Properties();
            extraProperties.put("jcifs.smb.client.ipcSigningEnforced", "false");
            return create(basePath, extraProperties);
        } else {
            return create(basePath, null);
        }
    }

    public static BaseContext create(String basePath, Properties extraProperties) {
        String basePathKey = Uri.parse(basePath).buildUpon().build().toString();

        if (contexts.containsKey(basePathKey)) {
            return contexts.get(basePathKey);
        } else {
            BaseContext context = Single.fromCallable(() -> {
                        try {
                            Properties properties = new Properties(defaultProperties);
                            if (extraProperties != null) {
                                properties.putAll(extraProperties);
                            }
                            return new BaseContext(new PropertyConfiguration(properties));
                        } catch (CIFSException e) {
                            Log.e(TAG, "Error initializing jcifs BaseContext, returning default", e);
                            return SingletonContext.getInstance();
                        }
                    }).subscribeOn(Schedulers.io())
                    .blockingGet();

            contexts.put(basePathKey, context);
            return context;
        }
    }
}

