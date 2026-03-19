package com.safelogj.mikread.helpers;

import android.net.Uri;

import androidx.lifecycle.ViewModel;

public class UriViewModel extends ViewModel {

    private Uri mCurrentFileUri;

    public Uri getCurrentFileUri() {
        return mCurrentFileUri;
    }

    public void setCurrentFileUri(Uri uri) {
        this.mCurrentFileUri = uri;
    }
}
