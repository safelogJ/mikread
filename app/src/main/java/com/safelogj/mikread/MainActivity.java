package com.safelogj.mikread;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowInsets;
import android.widget.EditText;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.safelogj.mikread.databinding.ActivityMainBinding;
import com.safelogj.mikread.databinding.RouterRowBinding;
import com.safelogj.mikread.helpers.PassFieldListener;

import java.util.ArrayList;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private ActivityMainBinding mBinding;
    private AppController appController;
    private MikrotikRouter currentRouter;
    private Map<String, MikrotikRouter> routersMap;
    private String selectedHost;

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
                drawSelectedRow(rowBinding);
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
        drawSelectedRow(selectedRow);
        String host = selectedRow.rowTextHost.getText().toString();
        selectedHost = host;
        MikrotikRouter router = routersMap.get(host);
        if (router != null) {
            mBinding.editRouterHost.setText(router.getHost());
            mBinding.editLogin.setText(router.getUser());
            mBinding.editPassword.setText(router.getPass());
            mBinding.editRouterNote.setText(router.getNote());
        }

    }

    private void drawSelectedRow(RouterRowBinding selectedRow) {
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

}