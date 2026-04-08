package com.safelogj.mikread.helpers;

import android.net.Uri;

import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModel;

public class UriViewModel extends ViewModel {

    private Uri mCurrentFileUri;
    @Nullable
    public Uri getCurrentFileUri() {
        return mCurrentFileUri;
    }

    public void setCurrentFileUri(@Nullable Uri uri) {
        this.mCurrentFileUri = uri;
    }
}
