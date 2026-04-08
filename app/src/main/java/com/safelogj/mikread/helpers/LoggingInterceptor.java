package com.safelogj.mikread.helpers;

import android.util.Log;

import androidx.annotation.NonNull;

import com.safelogj.mikread.AppController;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public class LoggingInterceptor implements Interceptor  {
    @NonNull
    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();

        // --- Логирование запроса (опционально) ---
        Log.d(AppController.LOG_TAG, "--> SENDING REQUEST: " + request.url());
        Log.d(AppController.LOG_TAG, "Headers: " + request.headers());

        long t1 = System.nanoTime();
        Response response = chain.proceed(request);
        long t2 = System.nanoTime();

        // --- Логирование ответа (то, что вам нужно) ---
        Log.d(AppController.LOG_TAG, "<-- RECEIVED RESPONSE: " + response.request().url() + " (" + (t2 - t1) / 1e6 + "ms)");
        Log.d(AppController.LOG_TAG, "Status Code: " + response.code());
        Log.d(AppController.LOG_TAG, "Headers: " + response.headers());

        return response;
    }
}
