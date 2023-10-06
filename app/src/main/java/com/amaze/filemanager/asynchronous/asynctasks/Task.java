package com.amaze.filemanager.asynchronous.asynctasks;
import io.reactivex.Flowable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

import java.util.concurrent.Callable;

public interface Task<V, T extends Callable<V>> {
    T getTask();
    void onError(Throwable error);
    void onFinish(V value);

    static <V, T extends Callable<V>> Disposable fromTask(Task<V, T> task) {
        return Flowable.fromCallable(task.getTask())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(task::onFinish, task::onError);
    }
}
