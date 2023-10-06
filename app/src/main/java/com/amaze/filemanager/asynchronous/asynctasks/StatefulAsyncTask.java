package com.amaze.filemanager.asynchronous.asynctasks;

/**
 * Interface to define state for AsyncTask
 */
public interface StatefulAsyncTask<T> {

    /**
     * Set callback to current AsyncTask. To be used to attach the context on
     * orientation change of fragment / activity
     * @param t callback
     */
    void setCallback(T t);
}

