package com.amaze.filemanager.asynchronous.handlers;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.View;
import androidx.recyclerview.widget.RecyclerView;
import com.amaze.filemanager.adapters.RecyclerAdapter;
import com.amaze.filemanager.adapters.data.LayoutElementParcelable;
import com.amaze.filemanager.filesystem.CustomFileObserver;
import com.amaze.filemanager.filesystem.HybridFile;
import com.amaze.filemanager.ui.fragments.MainFragment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.lang.ref.WeakReference;
import java.util.Objects;

public class FileHandler extends Handler {
    private WeakReference<MainFragment> mainFragment;
    private RecyclerView listView;
    private boolean useThumbs;
    private Logger log = LoggerFactory.getLogger(FileHandler.class);

    public FileHandler(MainFragment mainFragment, RecyclerView listView, boolean useThumbs) {
        super(Looper.getMainLooper());
        this.mainFragment = new WeakReference<>(mainFragment);
        this.listView = listView;
        this.useThumbs = useThumbs;
    }

    @Override
    public void handleMessage(Message msg) {
        super.handleMessage(msg);
        MainFragment main = mainFragment.get();
        if (main == null || main.getMainFragmentViewModel() == null || main.getElementsList() == null || main.getMainActivity() == null) {
            return;
        }

        String path = (String) msg.obj;
        switch (msg.what) {
            case CustomFileObserver.GOBACK:
                main.goBack();
                break;
            case CustomFileObserver.NEW_ITEM:
                if (path == null) {
                    log.error("Path is empty for file");
                    return;
                }
                HybridFile fileCreated = new HybridFile(main.getMainFragmentViewModel().openMode, main.getCurrentPath() + "/" + path);
                LayoutElementParcelable felement=fileCreated.generateLayoutElement(main.requireContext(), useThumbs);
                main.getElementsList().add(felement);
                break;
            case CustomFileObserver.DELETED_ITEM:
                for (LayoutElementParcelable element : main.getElementsList()) {
                    if (new File(element.desc).getName().equals(path)) {
                        main.getElementsList().remove(element);
                        break;
                    }
                }
                break;
            default:
                super.handleMessage(msg);
                return;
        }

        if (listView.getVisibility() == View.VISIBLE) {
            if (main.getElementsList().size() == 0) {
                // no item left in list, recreate views
                main.reloadListElements(true, main.getMainFragmentViewModel().getResults(), !main.getMainFragmentViewModel().isList());
            } else {
                // we already have some elements in list view, invalidate the adapter
                RecyclerAdapter adapter = (RecyclerAdapter) listView.getAdapter();
                if (adapter != null) {
                    adapter.setItems(listView, main.getElementsList());
                }
            }
        } else {
            // there was no list view, means the directory was empty
            main.loadlist(main.getCurrentPath(), true, main.getMainFragmentViewModel().openMode, true);
        }

        if (main.getCurrentPath() != null) {
            Objects.requireNonNull(main.getMainActivityViewModel()).evictPathFromListCache(main.getCurrentPath());
        }

        main.computeScroll();
    }
}

