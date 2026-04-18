package com.safelogj.mikread;


import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.util.Log;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.text.BidiFormatter;
import androidx.core.text.HtmlCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.lifecycle.ViewModelProvider;

import com.safelogj.mikread.databinding.ActivityFileBinding;
import com.safelogj.mikread.databinding.NoticeRowBinding;
import com.safelogj.mikread.databinding.SmsRowBinding;
import com.safelogj.mikread.helpers.UriViewModel;
import com.safelogj.mikread.sms.MotherSms;

import java.util.ArrayList;


public class FileActivity extends AppCompatActivity {
    private static final String ERROR_TEXT = "errorText";
    private final BidiFormatter bidiFormatter = BidiFormatter.getInstance();
    private ActivityFileBinding mBinding;
    private AppController appController;
    private UriViewModel uriViewModel;
    private final ActivityResultCallback<ActivityResult> callbackForGeneralPermitURI = result -> {
        if (result.getResultCode() == RESULT_OK && result.getData() != null) {
            Uri uri = result.getData().getData();
            if (uri != null) {
                final int takeFlags = (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                try {
                    getContentResolver().takePersistableUriPermission(uri, takeFlags);
                    Log.d(AppController.LOG_TAG, "Разрешение на URI сохранено: " + uri);
                } catch (SecurityException e) {
                    Log.d(AppController.LOG_TAG, "Ошибка получения разрешений на URI: " + e.getMessage(), e);
                }
                DocumentFile documentFile = DocumentFile.fromSingleUri(FileActivity.this, uri);
                if (!documentFile.exists()) {
                    Log.d(AppController.LOG_TAG, "Файл не найден или путь неверен!");
                    return;
                }
                uriViewModel.setCurrentFileUri(uri);
                appController.buildSmsFromFile(uri);
            }
        }
    };
    private final ActivityResultLauncher<Intent> requestGeneralPermitURI =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), callbackForGeneralPermitURI);

    private final ActivityResultCallback<Boolean> callbackAskReadFilePermit = result -> {
        if (Boolean.TRUE == result) {
            requestGeneralPermitURI.launch(getIntentActionOpenDoc());
        }
    };
    private final ActivityResultLauncher<String> requestAskReadFilePermit =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), callbackAskReadFilePermit);
    private String errorText = AppController.EMPTY_STRING;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        mBinding = ActivityFileBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(mBinding.getRoot(), (v, insets) -> {
            Insets systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets gestureInsets = insets.getInsets(WindowInsetsCompat.Type.mandatorySystemGestures());
            int leftPadding = Math.max(gestureInsets.left, systemInsets.left) + 20;
            int rightPadding = Math.max(gestureInsets.right, systemInsets.right) + 20;
            int bottomPadding = Math.max(gestureInsets.bottom, systemInsets.bottom) + 20;
            int leftPaddingLand = Math.max(leftPadding, systemInsets.top) + 20;
            int rightPaddingLand = Math.max(rightPadding, systemInsets.top) + 20;

            if (v.getContext().getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                v.setPadding(leftPaddingLand, systemInsets.top + 20, rightPaddingLand, bottomPadding);
            } else {
                v.setPadding(leftPadding, systemInsets.top + 20, rightPadding, bottomPadding);
            }
            return WindowInsetsCompat.CONSUMED;
        });

        appController = (AppController) getApplication();
        setLightStatusBar();
        setFileSmsBtn();
        setDelFileBtn();
        Intent intent = getIntent();
        if (intent != null) {
            String host = intent.getStringExtra(AppController.LOCALHOST_KEY);
            if (host != null) {
                mBinding.hostText.setText(host);
            }
        }
        uriViewModel = new ViewModelProvider(this).get(UriViewModel.class);
    }

    public void reDrawSmsList() {
        runOnUiThread(() -> {
            if (appController.getMotherFileSmsList().isEmpty()) {
                errorText = appController.getString(R.string.inbox_empty);
            } else {
                errorText = AppController.EMPTY_STRING;
            }
            drawFileSmsDecodedList();
        });
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        errorText = savedInstanceState.getString(ERROR_TEXT);

    }

    @Override
    protected void onResume() {
        super.onResume();
        drawFileSmsDecodedList();
    }

    //    onPause()
//    onStop()

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(ERROR_TEXT, errorText);
    }

    private void setLightStatusBar() {
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(true);
        controller.setAppearanceLightNavigationBars(true);
        getWindow().setNavigationBarColor(ContextCompat.getColor(this, R.color.white));
    }

    private void drawFileSmsDecodedList() {
        mBinding.fileSmsErrorText.setText(errorText);
        mBinding.fileSmsTable.removeAllViews();
        for (MotherSms motherSms : appController.getMotherFileSmsList()) {
            SmsRowBinding smsBinding = SmsRowBinding.inflate(getLayoutInflater(), mBinding.fileSmsTable, false);

            String message = motherSms.getFinalText();
            smsBinding.smsText.setText(bidiFormatter.unicodeWrap(message));
            smsBinding.smsText.setMovementMethod(LinkMovementMethod.getInstance());
            mBinding.fileSmsTable.addView(smsBinding.getRoot());
        }
        NoticeRowBinding noticeRowBinding = NoticeRowBinding.inflate(getLayoutInflater(), mBinding.fileSmsTable, false);
        noticeRowBinding.noticeText.setText(HtmlCompat.fromHtml(getString(R.string.notice), HtmlCompat.FROM_HTML_MODE_LEGACY));
        noticeRowBinding.noticeText.setMovementMethod(LinkMovementMethod.getInstance());
        mBinding.fileSmsTable.addView(noticeRowBinding.getRoot());
    }

    private Intent getIntentActionOpenDoc() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("text/plain");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
           // | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        );
        return intent;
    }

    private void setFileSmsBtn() {
        mBinding.fileSmsBtn.setOnClickListener(view -> {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2
                    && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestAskReadFilePermit.launch(Manifest.permission.READ_EXTERNAL_STORAGE);
                return;
            }
            requestGeneralPermitURI.launch(getIntentActionOpenDoc());
        });
    }

    private void setDelFileBtn() {
        mBinding.delFileSmsBtn.setOnClickListener(view -> {
            Uri fileUri = uriViewModel.getCurrentFileUri();
            if (!appController.getMotherFileSmsList().isEmpty() && fileUri != null) {
                DocumentFile documentFile = DocumentFile.fromSingleUri(FileActivity.this, fileUri);
                if (documentFile.exists() && documentFile.delete()) {
                    Log.d(AppController.LOG_TAG, "Файл успешно удален.");
                    uriViewModel.setCurrentFileUri(null);
                    errorText = AppController.EMPTY_STRING;
                    appController.setMotherFileSmsList(new ArrayList<>());
                } else {
                    Log.d(AppController.LOG_TAG, "Не удалось удалить файл. У провайдера нет поддержки удаления.");
                    errorText = appController.getString(R.string.file_remove_error);
                }
                drawFileSmsDecodedList();
            } else {
                Log.d(AppController.LOG_TAG, "URI = NULL или файл не смс");
            }
        });

    }
}