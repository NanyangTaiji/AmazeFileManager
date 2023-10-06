package com.amaze.filemanager.asynchronous.asynctasks;

import android.os.AsyncTask;
import android.text.TextUtils;
import com.amaze.filemanager.ui.activities.texteditor.SearchResultIndex;
import com.amaze.filemanager.utils.OnAsyncTaskFinished;
import com.amaze.filemanager.utils.OnProgressUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

public class SearchTextTask extends AsyncTask<Void, SearchResultIndex, List<SearchResultIndex>> {
    private final String textToSearch;
    private final String searchedText;
    private final OnProgressUpdate<SearchResultIndex> updateListener;
    private final OnAsyncTaskFinished<List<SearchResultIndex>> listener;
    private final LineNumberReader lineNumberReader;
    private final Logger log = LoggerFactory.getLogger(SearchTextTask.class);

    public SearchTextTask(String textToSearch, String searchedText,
                          OnProgressUpdate<SearchResultIndex> updateListener,
                          OnAsyncTaskFinished<List<SearchResultIndex>> listener) {
        this.textToSearch = textToSearch;
        this.searchedText = searchedText;
        this.updateListener = updateListener;
        this.listener = listener;
        StringReader stringReader = new StringReader(textToSearch);
        this.lineNumberReader = new LineNumberReader(stringReader);
    }

    @Override
    protected List<SearchResultIndex> doInBackground(Void... params) {
        if (TextUtils.isEmpty(searchedText)) {
            return new ArrayList<>();
        }

        List<SearchResultIndex> searchResultIndices = new ArrayList<>();
        int charIndex = 0;
        while (charIndex < textToSearch.length() - searchedText.length()) {
            if (isCancelled()) break;
            int nextPosition = textToSearch.indexOf(searchedText, charIndex);
            if (nextPosition == -1) {
                break;
            }
            try {
                lineNumberReader.skip((nextPosition - charIndex));
            } catch (IOException e) {
                log.warn("failed to search text", e);
            }
            charIndex = nextPosition;
            SearchResultIndex index = new SearchResultIndex(
                    charIndex,
                    charIndex + searchedText.length(),
                    lineNumberReader.getLineNumber()
            );
            searchResultIndices.add(index);
            publishProgress(index);
            charIndex++;
        }

        return searchResultIndices;
    }

    @Override
    protected void onProgressUpdate(SearchResultIndex... values) {
        updateListener.onUpdate(values[0]);
    }

    @Override
    protected void onPostExecute(List<SearchResultIndex> searchResultIndices) {
        listener.onAsyncTaskFinished(searchResultIndices);
    }
}

