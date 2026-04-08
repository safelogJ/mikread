package com.safelogj.mikread.helpers;

import android.util.Log;

import androidx.annotation.NonNull;

import com.safelogj.mikread.AppController;

import java.util.ArrayList;
import java.util.List;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;

public class RouterCookieJar implements CookieJar {

    private final List<Cookie> mCookieList = new ArrayList<>();

    @Override
    public synchronized void saveFromResponse(@NonNull HttpUrl url, @NonNull List<Cookie> cookies) {
        if (!cookies.isEmpty()) {
            mCookieList.clear();
            mCookieList.addAll(cookies);
               Log.d(AppController.LOG_TAG, "Сохранены новые куки: " + cookies.size());
            for (Cookie c : cookies) {
                       Log.d(AppController.LOG_TAG, "сохранены Cookie: " + c.name() + "=" + c.value());
            }
        }
    }

    @NonNull
    @Override
    public synchronized List<Cookie> loadForRequest(@NonNull HttpUrl url) {
          Log.d(AppController.LOG_TAG, "Запрошены куки для : " + url);
        return mCookieList;
    }

    public void clearCookie() {
        mCookieList.clear();
    }

}
