package com.amaze.filemanager.asynchronous.asynctasks;

import androidx.annotation.MainThread;
import io.reactivex.Flowable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

import java.util.concurrent.Callable;

public interface Task<V, T extends Callable<V>> {
    /**
     * This should return a callable to be run on a worker thread
     * The [Callable] cannot return null
     */
    T getTask();

    /**
     * This function will be called on the main thread if an exception is thrown
     */
    @MainThread
    void onError(Throwable error);

    /**
     * If the task does not return null and doesn't throw an error, this
     * function will be called with the result of the operation on the main thread
     */
    @MainThread
    void onFinish(V value);


    /**
     * This creates and starts a [Flowable] from a [Task].
     */
    public static <V, T extends Callable<V>> Disposable fromTask(Task<V, T> task) {
        return Flowable.fromCallable(task.getTask())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(task::onFinish, task::onError);
    }
}

