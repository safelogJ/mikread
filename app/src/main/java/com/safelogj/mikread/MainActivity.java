package com.safelogj.mikread;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.EditText;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.documentfile.provider.DocumentFile;

import com.safelogj.mikread.databinding.ActivityMainBinding;
import com.safelogj.mikread.databinding.RouterRowBinding;
import com.safelogj.mikread.helpers.PassFieldListener;

import java.io.InputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private AppController appController;
    private String selectedHost = AppController.EMPTY_STRING;
    private final ActivityResultCallback<ActivityResult> callbackForGeneralPermitURI = result -> {
        if (result.getResultCode() == RESULT_OK && result.getData() != null) {
            Uri uri = result.getData().getData();
            if (uri != null) {
                DocumentFile documentFile = DocumentFile.fromSingleUri(MainActivity.this, uri);
                if (documentFile.exists()) {
                    try (InputStream is = getContentResolver().openInputStream(uri)) {
                        CertificateFactory cf = CertificateFactory.getInstance("X.509");
                        X509Certificate cert = (X509Certificate) cf.generateCertificate(is);
                        byte[] certBytes = cert.getEncoded();
                        String certName = documentFile.getName();
                        appController.addCertToRouter(certBytes, selectedHost, certName);
                        drawCertName(certName);
                        Log.d(AppController.LOG_TAG, "Сертификат успешно импортирован");
                    } catch (Exception e) {
                        drawCertName(appController.getString(R.string.cert_import_error));
                        Log.i(AppController.LOG_TAG, "Ошибка импорта сертификата: " + e.getMessage());
                    }
                }
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

    private ActivityMainBinding mBinding;
    private MikrotikRouter currentRouter;
    private Map<String, MikrotikRouter> routersMap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        mBinding = ActivityMainBinding.inflate(getLayoutInflater());
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
        setLightStatusBar();
        appController = (AppController) getApplication();
        routersMap = appController.getRoutersMap();
        currentRouter = appController.getCurrentRouter();
        setAddCertBtn();
        setDelCertBtn();
        mBinding.btnAddSet.setOnClickListener(view -> setAddSetBtnListener());
        mBinding.btnConnect.setOnClickListener(view -> connectToRouter());
        mBinding.btnDel.setOnClickListener(view -> delRouter());
        mBinding.titleTextHost.setOnClickListener(view -> sortRouterTable(AppController.HOST_COMPARATOR));
        mBinding.titleTextLogin.setOnClickListener(view -> sortRouterTable(AppController.USER_COMPARATOR));
        mBinding.titleTextNote.setOnClickListener(view -> sortRouterTable(AppController.NOTE_COMPARATOR));
        mBinding.editPassword.setOnTouchListener(new PassFieldListener());
    }

    public void drawError(String errorString) {
        runOnUiThread(() -> mBinding.errorText.setText(errorString));
    }

    public void startSmsActivity() {
        runOnUiThread(() -> startActivity(new Intent(this, SmsActivity.class)));
    }

    @Override
    protected void onStart() {
        super.onStart();
        drawCurrentRouter();
        selectedHost = appController.getSelectedHost();
        drawRouterList();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mBinding.errorText.setText(appController.getConnectedRouter().getErrorText());
    }

    @Override
    protected void onStop() {
        appController.setSelectedHost(selectedHost);
        fillRouterFromFields(currentRouter);
        appController.setCurrentRouter(currentRouter);
        appController.writeSettingsToFile();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void setAddSetBtnListener() {
        MikrotikRouter router;
        String host = mBinding.editRouterHost.getText().toString().trim();
        if (isLocalHost(host)) {
            router = MikrotikRouter.buildLocalhost(appController, host);
        } else {
            router = fillRouterFromFields(new MikrotikRouter(appController));
        }

        if (router.isValidRouterToConnect()) {
            appController.addRouterToMap(router);
            drawRouterList();
            appController.writeSettingsToFile();
        } else {
            drawRedField();
        }
    }


    private void connectToRouter() {
        if (!appController.getConnectedRouter().isConnecting()) {
            String host = mBinding.editRouterHost.getText().toString().trim();
            if (isLocalHost(host)) {
                appController.setMotherFileSmsList(new ArrayList<>());
                Intent intent = new Intent(this, FileActivity.class);
                intent.putExtra(AppController.LOCALHOST_KEY, host);
                startActivity(intent);
            } else {
                MikrotikRouter router = fillRouterFromFields(new MikrotikRouter(appController));
                if (router.isValidRouterToConnect()) {
                    appController.connectToValidRouter(router);
                } else {
                    drawRedField();
                }
            }
        }

    }

    private void delRouter() {
        if (!selectedHost.isEmpty()) {
            appController.delRouterFromMap(selectedHost);
            selectedHost = AppController.EMPTY_STRING;
            appController.writeSettingsToFile();
            drawRouterList();
            mBinding.btnDel.setBackground(AppCompatResources.getDrawable(this, R.drawable.btn_del_winbox));
        }
    }

    private void sortRouterTable(int comparatorIdx) {
        appController.sortRoutersMap(comparatorIdx);
        drawRouterList();
    }


    private void drawCurrentRouter() {
        mBinding.editRouterHost.setText(currentRouter.getHost());
        mBinding.editLogin.setText(currentRouter.getUser());
        mBinding.editPassword.setText(currentRouter.getPass());
        mBinding.editRouterNote.setText(currentRouter.getNote());
    }

    private MikrotikRouter fillRouterFromFields(MikrotikRouter router) {
        router.setHost(mBinding.editRouterHost.getText().toString().trim());
        router.setUser(mBinding.editLogin.getText().toString().trim());
        router.setPass(mBinding.editPassword.getText().toString().trim());
        router.setNote(mBinding.editRouterNote.getText().toString().trim());
        return router;
    }

    private void setLightStatusBar() {
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(true);
        controller.setAppearanceLightNavigationBars(true);
        getWindow().setNavigationBarColor(ContextCompat.getColor(this, R.color.white));
    }

    private void drawRouterList() {
        mBinding.routerTable.removeAllViews();
        for (MikrotikRouter router : routersMap.values()) {
            RouterRowBinding rowBinding = RouterRowBinding.inflate(getLayoutInflater(), mBinding.routerTable, false);
            rowBinding.rowTextHost.setOnClickListener(v -> selectRow(rowBinding));
            rowBinding.rowTextLogin.setOnClickListener(v -> selectRow(rowBinding));
            rowBinding.rowTextNote.setOnClickListener(v -> selectRow(rowBinding));

            String host = router.getHost();
            if (host.equals(selectedHost)) {
                drawSelectedRowBackground(rowBinding);
                drawCertName(router.getCertName());
            }
            rowBinding.rowTextHost.setText(host);
            rowBinding.rowTextLogin.setText(router.getUser());
            rowBinding.rowTextNote.setText(router.getNote());
            mBinding.routerTable.addView(rowBinding.getRoot());
        }
    }

    private void selectRow(RouterRowBinding selectedRow) {
        for (int i = 0; i < mBinding.routerTable.getChildCount(); i++) {
            View rowView = mBinding.routerTable.getChildAt(i);
            RouterRowBinding binding = RouterRowBinding.bind(rowView);

            binding.rowHostScroll.setBackgroundResource(R.drawable.router_field_bg);
            binding.rowLoginScroll.setBackgroundResource(R.drawable.router_field_bg);
            binding.rowNoteScroll.setBackgroundResource(R.drawable.router_field_bg);
        }
        drawSelectedRowBackground(selectedRow);
        String host = selectedRow.rowTextHost.getText().toString();
        selectedHost = host;
        MikrotikRouter router = routersMap.get(host);
        if (router != null) {
            mBinding.editRouterHost.setText(router.getHost());
            mBinding.editLogin.setText(router.getUser());
            mBinding.editPassword.setText(router.getPass());
            mBinding.editRouterNote.setText(router.getNote());
            drawCertName(router.getCertName());
        }

    }

    private void drawSelectedRowBackground(RouterRowBinding selectedRow) {
        selectedRow.rowHostScroll.setBackgroundResource(R.drawable.router_field_select_bg);
        selectedRow.rowLoginScroll.setBackgroundResource(R.drawable.router_field_select_bg);
        selectedRow.rowNoteScroll.setBackgroundResource(R.drawable.router_field_select_bg);
        mBinding.btnDel.setBackground(AppCompatResources.getDrawable(this, R.drawable.btn_del_winbox_red));
    }

    private void drawRedField() {
        drawEditTextRedBorder(mBinding.editRouterHost);
        drawEditTextRedBorder(mBinding.editLogin);
        drawEditTextRedBorder(mBinding.editPassword);
    }

    private void drawEditTextRedBorder(EditText editText) {
        if (editText.getText().toString().trim().isEmpty()) {
            editText.setBackground(AppCompatResources.getDrawable(this, R.drawable.field_bg_winbox_red));
            handler.postDelayed(() ->
                            editText.setBackground(AppCompatResources.getDrawable(this, R.drawable.field_bg_winbox)),
                    1000
            );
        }
    }

    private boolean isLocalHost(String host) {
        return host.equals("localhost") || host.equals("127.0.0.1");
    }

    private void setAddCertBtn() {
        mBinding.addCertBtn.setOnClickListener(view -> {
            if (!selectedHost.isEmpty() && MikrotikRouter.isRestApiHost(selectedHost)) {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2
                        && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    requestAskReadFilePermit.launch(Manifest.permission.READ_EXTERNAL_STORAGE);
                    return;
                }
                requestGeneralPermitURI.launch(getIntentActionOpenDoc());
            } else {
                drawCertName(appController.getString(R.string.choose_router));
            }
        });
    }

    private void setDelCertBtn() {
        mBinding.delCertBtn.setOnClickListener(v -> {
            if (!selectedHost.isEmpty() && MikrotikRouter.isRestApiHost(selectedHost)) {
                MikrotikRouter router = routersMap.get(selectedHost);
                if (router != null) {
                    appController.removeCertFromRouter(router);
                    drawCertName(AppController.EMPTY_STRING);
                }
            } else {
                drawCertName(appController.getString(R.string.choose_router));
            }
        });
    }

    private void drawCertName(String certName) {
        mBinding.certNameText.setText(certName);
    }

    private Intent getIntentActionOpenDoc() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        String[] mimeTypes = {"application/x-x509-ca-cert", "application/pkix-cert", "application/x-pem-file"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return intent;
    }

}