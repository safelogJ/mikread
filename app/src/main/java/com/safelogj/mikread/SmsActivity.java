package com.safelogj.mikread;

import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.text.BidiFormatter;
import androidx.core.text.HtmlCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.safelogj.mikread.databinding.ActivitySmsBinding;
import com.safelogj.mikread.databinding.NoticeRowBinding;
import com.safelogj.mikread.databinding.SmsRowBinding;
import com.safelogj.mikread.sms.MotherSms;


public class SmsActivity extends AppCompatActivity {
    private static final String SELECTED_SMS = "selectedSms";
    private static final String ERASING_TEXT = "erasingText";
    private static final String ERASING_TIME = "erasingTime";
    private final BidiFormatter bidiFormatter = BidiFormatter.getInstance();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable eraserRunnable;
    private ActivitySmsBinding mBinding;
    private AppController appController;
    private MikrotikRouter connectedRouter;
    private String selectedMessage = AppController.EMPTY_STRING;
    private String erasingText = AppController.EMPTY_STRING;
    private long erasingTime = 0;
    private MotherSms selectedMotherSms;
    private TextView selectedTextView;
    private TextView deletedTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        mBinding = ActivitySmsBinding.inflate(getLayoutInflater());
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
        connectedRouter = appController.getConnectedRouter();
        mBinding.smsBtnDel.setOnClickListener(view -> removeSms());
    }

    public void drawError(String errorString) {
        runOnUiThread(() -> mBinding.smsErrorText.setText(errorString));
    }

    public void reDrawSmsList() {
        runOnUiThread(() -> {
            if (eraserRunnable != null) {
                handler.removeCallbacks(eraserRunnable);
            }
            drawSmsDecodedList();
        });
    }

// onStart()

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        selectedMessage = savedInstanceState.getString(SELECTED_SMS);
        erasingText = savedInstanceState.getString(ERASING_TEXT);
        erasingTime = savedInstanceState.getLong(ERASING_TIME);

    }

    @Override
    protected void onResume() {
        super.onResume();
        drawSmsDecodedList();
        if (connectedRouter.isConnecting()) {
            eraseTextGradually(erasingTime, erasingText, deletedTextView);
        }
        mBinding.smsErrorText.setText(connectedRouter.getErrorText());
    }

//    onPause()
//    onStop()

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(SELECTED_SMS, selectedMessage);
        outState.putString(ERASING_TEXT, erasingText);
        outState.putLong(ERASING_TIME, erasingTime);

    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void drawSmsDecodedList() {
        mBinding.smsBtnDel.setBackground(AppCompatResources.getDrawable(this, R.drawable.btn_del_winbox));
        selectedMotherSms = null;
        mBinding.smsTable.removeAllViews();
        for (MotherSms motherSms : connectedRouter.getMotherSmsList()) {
            SmsRowBinding smsBinding = SmsRowBinding.inflate(getLayoutInflater(), mBinding.smsTable, false);
            smsBinding.smsText.setOnClickListener(v -> selectRow(smsBinding));
            smsBinding.smsText.setTag(motherSms);

            String message = motherSms.getFinalText();
            if (message.equals(selectedMessage)) {
                drawSelectedRow(smsBinding);
            }

            if (motherSms.isDeleting()) {
                deletedTextView = smsBinding.smsText;
            }

            smsBinding.smsText.setText(bidiFormatter.unicodeWrap(message));
            smsBinding.smsText.setMovementMethod(LinkMovementMethod.getInstance());
            mBinding.smsTable.addView(smsBinding.getRoot());
        }
        NoticeRowBinding noticeRowBinding = NoticeRowBinding.inflate(getLayoutInflater(), mBinding.smsTable, false);
        noticeRowBinding.noticeText.setText(HtmlCompat.fromHtml(getString(R.string.notice), HtmlCompat.FROM_HTML_MODE_LEGACY));
        noticeRowBinding.noticeText.setMovementMethod(LinkMovementMethod.getInstance());
        mBinding.smsTable.addView(noticeRowBinding.getRoot());

        setModelText();
    }

    private void selectRow(SmsRowBinding selectedRow) {
        for (int i = 0; i < mBinding.smsTable.getChildCount() - 1; i++) {
            View rowView = mBinding.smsTable.getChildAt(i);
            SmsRowBinding binding = SmsRowBinding.bind(rowView);
            binding.smsText.setBackgroundResource(R.drawable.router_field_bg);
        }
        drawSelectedRow(selectedRow);
        selectedMessage = selectedMotherSms.getFinalText();
    }

    private void drawSelectedRow(SmsRowBinding selectedRow) {
        selectedRow.smsText.setBackgroundResource(R.drawable.router_field_select_bg);
        selectedMotherSms = getMotherSmsFromTag(selectedRow.smsText.getTag());
        selectedTextView = selectedRow.smsText;
        mBinding.smsBtnDel.setBackground(AppCompatResources.getDrawable(this, R.drawable.btn_del_winbox_red));
    }

    private MotherSms getMotherSmsFromTag(Object obj) {
        return obj instanceof MotherSms sms ? sms : null;
    }

    private void removeSms() {
        if (!connectedRouter.isConnecting() && selectedMotherSms != null) {
            drawError(AppController.EMPTY_STRING);
            appController.removeMotherSms(selectedMotherSms);

            erasingTime = MikrotikRouter.SLEEP_TIMEOUT * 2L;
            if (connectedRouter.getMotherSmsList().size() == 1) {
                erasingTime += MikrotikRouter.COMMAND_TIMEOUT;
            }
            eraseTextGradually(erasingTime, selectedMessage, selectedTextView);
        }
    }


    private void eraseTextGradually(long erasingTime, String text, TextView deletedTextView) {
        if (deletedTextView == null || text.isEmpty() || erasingTime <= 0) {
            return;
        }

        final TextView textView = deletedTextView;
        if (deletedTextView.getTag() instanceof MotherSms motherSms) {
            motherSms.setDeleting(true);
        }

        final int totalLength = text.length();
        final int tickInterval = 250;
        final int totalSteps = (int) Math.max(1, erasingTime / tickInterval);

        int charsPerStep = totalLength / totalSteps;
        if (charsPerStep < 1) charsPerStep = 1;

        final int finalCharsPerStep = charsPerStep;

        // Очищаем предыдущие задачи, если они были
        if (eraserRunnable != null) {
            handler.removeCallbacks(eraserRunnable);
        }

        eraserRunnable = new Runnable() {
            int currentLength = totalLength;

            @Override
            public void run() {
                currentLength -= finalCharsPerStep;

                if (currentLength <= 0) {
                    textView.setText(AppController.EMPTY_STRING);
                    return;
                }

                erasingText = text.substring(0, currentLength);
                textView.setText(bidiFormatter.unicodeWrap(erasingText));
                handler.postDelayed(this, tickInterval);
            }
        };

        textView.setText(text);
        handler.postDelayed(eraserRunnable, tickInterval);
    }


    private void setLightStatusBar() {
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(true);
        controller.setAppearanceLightNavigationBars(true);
        getWindow().setNavigationBarColor(ContextCompat.getColor(this, R.color.white));
    }

    private void setModelText() {
        String note = connectedRouter.getNote();
        String model = connectedRouter.getModel();
        if (model.isEmpty()) {
            mBinding.routerModel.setText(AppController.EMPTY_STRING);
        } else {
            mBinding.routerModel.setText(note.isEmpty() ? model : note + ": " + model);
        }
    }

}