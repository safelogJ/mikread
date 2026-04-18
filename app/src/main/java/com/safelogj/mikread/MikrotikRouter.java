package com.safelogj.mikread;

import android.app.Activity;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.safelogj.mikread.sms.MotherSms;
import com.safelogj.mikread.sms.MotherSmsFactory;
import com.safelogj.mikread.sms.Sms;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;


import me.legrange.mikrotik.ApiConnection;
import me.legrange.mikrotik.MikrotikApiException;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MikrotikRouter {
    public static final int SMS_REMOVED_NO_PERM = 100;
    public static final int SMS_REMOVED_INTERRUPT = 101;
    public static final int LTE_NOT_ACTIVE = 102;
    public static final int SMS_REMOVED_NO_SUCH_ITEM = 103;
    public static final int COMMAND_TIMEOUT = 5_000;
    public static final int SLEEP_TIMEOUT = 1100;
    private static final String HTTPS = "https://";
    private static final String READ_SMS_COMMAND_DETAIL = "/tool/sms/inbox/print detail";
    private static final String READ_SMS_COMMAND_DETAIL_REST_API = "/rest/tool/sms/inbox";
    private static final String REMOVE_ALL_SMS_COMMAND_PATTERN = "/tool/sms/inbox/remove numbers=%s";
    private static final String ROUTER_MODEL_PRINT = "/system/routerboard/print";
    private static final String ROUTER_MODEL_REST_API = "/rest/system/routerboard";
    private static final String REMOVE_SMS_COMMAND_DETAIL_REST_API = "/rest/tool/sms/inbox/";
    private static final String ERROR_NO_PERMISSIONS = "not enough permissions";
    private static final String ERROR_LTE_NOT_ACTIVE = "LTE not active";
    private static final String ERROR_NO_SUCH_ITEM = "no such item";
    private static final String ERROR_DELETED_INTERRUPTED = "error: deletion interrupted";
    private static final String SPACE = " ";
    private static final String REST_API_PORT_PATTERN = ".*:\\d{1,5}$";
    private final AppController appController;
    private volatile String errorText = AppController.EMPTY_STRING;
    private volatile boolean isConnecting;
    private volatile List<MotherSms> motherSmsList = new ArrayList<>();
    private List<Sms> decodedPartSmsList = new ArrayList<>();
    private String host = AppController.EMPTY_STRING;
    private String user = AppController.EMPTY_STRING;
    private String pass = AppController.EMPTY_STRING;
    private String note = AppController.EMPTY_STRING;
    private String model = AppController.EMPTY_STRING;
    private String credential = AppController.EMPTY_STRING;
    private String certName = AppController.EMPTY_STRING;
    private byte[] certBytes;
    private long startTime;
    private int delResultCode;

    @NonNull
    public static MikrotikRouter buildLocalhost(AppController appController, String host) {
        MikrotikRouter localRouter = new MikrotikRouter(appController);
        localRouter.setHost(host);
        localRouter.setUser(host);
        localRouter.setPass(host);
        localRouter.setNote(host);
        return localRouter;
    }

    public static boolean isRestApiHost(@NonNull String host) {
        return host.matches(REST_API_PORT_PATTERN);
    }

    public MikrotikRouter(AppController appController) {
        this.appController = appController;
    }

    public String getUser() {
        return user;
    }

    public String getPass() {
        return pass;
    }

    public String getNote() {
        return note;
    }

    public List<MotherSms> getMotherSmsList() {
        return motherSmsList;
    }

    public String getHost() {
        return host;
    }
    @Nullable
    public byte[] getCertBytes() {
        return certBytes;
    }

    public void setCertBytes(@Nullable byte[] certBytes) {
        this.certBytes = certBytes;
    }

    public String getCertName() {
        return certName;
    }

    public void setCertName(String certName) {
        this.certName = certName;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public void setPass(String pass) {
        this.pass = pass;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getModel() {
        return model;
    }

    public boolean isValidRouterToConnect() {
        return !host.isEmpty() && !user.isEmpty() && !pass.isEmpty();
    }

    public boolean isConnecting() {
        return isConnecting;
    }

    @NonNull
    public String getErrorText() {
        return errorText;
    }

    public void readSmsFromRouter() {
        if (isRestApiHost(host)) {
            connectRestApi();
        } else {
            connectApi();
        }
    }

    public void removeSmsFromRouter(MotherSms motherSms) {
        if (isRestApiHost(host)) {
            removeMotherSmsByDateRestApi(motherSms);
        } else {
            removeMotherSmsByDateApi(motherSms);
        }
    }

    private void connectApi() {
        isConnecting = true;
        sendInfoMessageToActivity(appController.getString(R.string.connecting));
        startTime = SystemClock.elapsedRealtime();
        model = AppController.EMPTY_STRING;
        try (ApiConnection con = ApiConnection.connect(host)) {
            con.setTimeout(COMMAND_TIMEOUT);
            con.login(user, pass);
            Log.d(AppController.LOG_TAG, "Перед читкой модели = ");
            readRouterModelApi(con);
            Log.d(AppController.LOG_TAG, "Перед обновленем смсок = ");
            loadInboxSmsAndBuildMothersApi(con);
            Log.d(AppController.LOG_TAG, "Размер списка смс SMS: id = " + decodedPartSmsList.size());
            if (!motherSmsList.isEmpty()) {
                sendInfoMessageToActivity(AppController.EMPTY_STRING);
                startSmsActivity();
            } else {
                sendInfoMessageToActivity(appController.getString(R.string.inbox_empty));
            }

        } catch (MikrotikApiException e) {
            Log.d(AppController.LOG_TAG, "Ошибка при коннекте = " + e.getMessage());
            if (!model.isEmpty()) {
                sendInfoMessageToActivity(appController.getString(R.string.inbox_empty));
            } else {
                errorCatch(e);
            }
        }
        isConnecting = false;
    }

    private void removeMotherSmsByDateApi(MotherSms motherSms) {
        isConnecting = true;
        startTime = SystemClock.elapsedRealtime();
        sendInfoMessageToActivity(appController.getString(R.string.removal));
        model = AppController.EMPTY_STRING;
        try (ApiConnection con = ApiConnection.connect(host)) {
            con.setTimeout(COMMAND_TIMEOUT);
            Log.d(AppController.LOG_TAG, "Перед логином метод = ");
            con.login(user, pass);
            Log.d(AppController.LOG_TAG, "Перед удалением метод = ");
            removePartsSmsApi(motherSms, con);
            Log.d(AppController.LOG_TAG, "Перед читкой модели = ");
            readRouterModelApi(con);
            Log.d(AppController.LOG_TAG, "Перед обновлением смсок = ");
            loadInboxSmsAndBuildMothersApi(con);
            sendInfoMessageToActivity(getFinalMessageToActivity(delResultCode));
            Log.d(AppController.LOG_TAG, "Конец удачнного удаления всех смс, отправлена команда обновиться таблице смс");
        } catch (MikrotikApiException e) {
            Log.d(AppController.LOG_TAG, "Конец удаления всех смс, НЕУДАЧА");
            if (model.isEmpty()) {
                if (!motherSmsList.isEmpty()) {
                    errorCatch(e);
                } else {
                    sendInfoMessageToActivity(appController.getString(R.string.inbox_empty));
                }

            } else {
                sendInfoMessageToActivity(AppController.EMPTY_STRING);
            }
        }
        sendRedrawSmsCommand();
        isConnecting = false;
    }

    private void errorCatch(Exception e) {
        String msg = e.getMessage();
        if (msg != null) {
            Log.d(AppController.LOG_TAG, msg + e.getClass());
            int durationMs = (int) (SystemClock.elapsedRealtime() - startTime);
            msg = msg.replace("after 60000ms", "after " + durationMs + "ms");
        } else {
            msg = AppController.EMPTY_STRING;
        }
        sendInfoMessageToActivity(msg);
    }

    private void removePartsSmsApi(MotherSms motherSms, ApiConnection con) {
        delResultCode = 0;
        String indices = getIndices(sendCommand(READ_SMS_COMMAND_DETAIL, con), motherSms);
        if (!indices.isEmpty()) {
            sendCommand(String.format(Locale.ROOT, REMOVE_ALL_SMS_COMMAND_PATTERN, indices), con);
        } else {
            motherSmsList.remove(motherSms);
        }
    }

    private List<Map<String, String>> sendCommand(String command, ApiConnection con) {
        List<Map<String, String>> res = new ArrayList<>();
        try {
            res = con.execute(command);
            Log.d(AppController.LOG_TAG, "В цикле выполнена команда" + command);

        } catch (MikrotikApiException e) {
            Log.d(AppController.LOG_TAG, " Не выполнена команда в цикле = " + command + "\n " + e.getMessage());
            String msg = e.getMessage();
            if (msg != null) {
                setDelResultCode(msg);
            }
        }
        makePauseBetweenCommand();
        return res;
    }

    private void makePauseBetweenCommand() {
        try {
            Log.d(AppController.LOG_TAG, "Сон между командами = " + SLEEP_TIMEOUT + "ms");
            TimeUnit.MILLISECONDS.sleep(SLEEP_TIMEOUT);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.d(AppController.LOG_TAG, "Прерван сон между командами интераптед = " + SLEEP_TIMEOUT + "ms");
            sendInfoMessageToActivity(appController.getString(R.string.sms_remove_error));
            delResultCode = SMS_REMOVED_INTERRUPT;
        }
    }

    private void sendRedrawSmsCommand() {
        WeakReference<Activity> activityWeakReference = appController.getCurrentActivityRef();
        if (activityWeakReference != null) {
            Activity activity = activityWeakReference.get();
            if (activity instanceof SmsActivity smsActivity) {
                smsActivity.reDrawSmsList();
            }
        }
    }


    private void readRouterModelApi(ApiConnection con) throws MikrotikApiException {
        List<Map<String, String>> res = con.execute(ROUTER_MODEL_PRINT);
        if (!res.isEmpty()) {
            model = res.get(0).get(Sms.MODEL_KEY);
            Log.d(AppController.LOG_TAG, "Модель роутера = " + model);
        }
    }

    private void loadInboxSmsAndBuildMothersApi(ApiConnection con) throws MikrotikApiException {
        decodedPartSmsList = new ArrayList<>();
        motherSmsList = new ArrayList<>();
        List<Map<String, String>> res = con.execute(READ_SMS_COMMAND_DETAIL);
        if (res != null && !res.isEmpty()) {
            for (Map<String, String> sms : res) {
                Sms newSms = new Sms(sms.get(Sms.PHONE_KEY), sms.get(Sms.TIMESTAMP_KEY), sms.get(Sms.MESSAGE_KEY),
                        sms.get(Sms.PDU_KEY), sms.get(Sms.SOURCE_KEY), sms.get(Sms.TYPE_KEY));
                Log.d(AppController.LOG_TAG, "ID =  " + sms.get(".id"));
                newSms.decodePduToText();
                if (newSms.isValidSms()) {
                    decodedPartSmsList.add(newSms);
                }
            }
        } else {
            Log.d(AppController.LOG_TAG, "res =  " + (res == null));
        }
        MotherSmsFactory.fillMotherSmsList(decodedPartSmsList, motherSmsList);
    }

    private void sendInfoMessageToActivity(@NonNull String errorString) {
        errorText = errorString;
        WeakReference<Activity> activityWeakReference = appController.getCurrentActivityRef();
        if (activityWeakReference != null) {
            Activity activity = activityWeakReference.get();
            if (activity instanceof MainActivity mainActivity) {
                mainActivity.drawError(errorString);
            } else if (activity instanceof SmsActivity smsActivity) {
                smsActivity.drawError(errorString);
            }
        }
    }


    private void startSmsActivity() {
        WeakReference<Activity> activityWeakReference = appController.getCurrentActivityRef();
        if (activityWeakReference != null) {
            Activity activity = activityWeakReference.get();
            if (activity instanceof MainActivity mainActivity) {
                mainActivity.startSmsActivity();
            }
        }
    }

    private String getFinalMessageToActivity(int error) {
        return switch (error) {
            case SMS_REMOVED_NO_PERM -> ERROR_NO_PERMISSIONS;
            case SMS_REMOVED_INTERRUPT -> ERROR_DELETED_INTERRUPTED;
            case LTE_NOT_ACTIVE -> ERROR_LTE_NOT_ACTIVE;
            case SMS_REMOVED_NO_SUCH_ITEM -> ERROR_NO_SUCH_ITEM;
            default -> AppController.EMPTY_STRING;
        };
    }

    @NonNull
    private String getIndices(List<Map<String, String>> res, MotherSms motherSms) {
        int id = 0;
        StringBuilder indices = new StringBuilder(AppController.EMPTY_STRING);
        for (Map<String, String> smsFind : res) {
            if (MotherSmsFactory.isSameTimestamp(smsFind.get(Sms.TIMESTAMP_KEY), motherSms.getGroupTimestamp())
                    && motherSms.getSource().equals(smsFind.get(Sms.SOURCE_KEY))
                    && motherSms.getPhone().equals(smsFind.get(Sms.PHONE_KEY))
                    && motherSms.isMessageContains(smsFind.get(Sms.MESSAGE_KEY))) {
                if (indices.length() != 0) {
                    indices.append(",");
                }
                indices.append(id);
            }
            id++;
        }
        Log.d(AppController.LOG_TAG, "Собраны индексы на удаление = " + indices);
        return indices.toString();
    }

    private void connectRestApi() {
        OkHttpClient client = appController.getOkHttpClient();

        if (client == null) {
            sendInfoMessageToActivity(appController.getString(R.string.okhttp_error));
            return;
        }
        isConnecting = true;
        sendInfoMessageToActivity(appController.getString(R.string.connecting));
        startTime = SystemClock.elapsedRealtime();
        model = AppController.EMPTY_STRING;
        checkCredential();
        try {
            readRouterModelRestApi(client);
            loadInboxSmsAndBuildMothersRestApi(client);
            Log.d(AppController.LOG_TAG, "Размер списка смс SMS: id = " + decodedPartSmsList.size());
            if (!motherSmsList.isEmpty()) {
                sendInfoMessageToActivity(AppController.EMPTY_STRING);
                startSmsActivity();
            } else {
                sendInfoMessageToActivity(appController.getString(R.string.inbox_empty));
            }
        } catch (Exception e) {
            Log.d(AppController.LOG_TAG, "Ошибка при REST API = " + e.getMessage());
            if (!model.isEmpty()) {
                sendInfoMessageToActivity(appController.getString(R.string.inbox_empty));
            } else {
                errorCatch(e);
            }
        }
        isConnecting = false;
    }

    private void readRouterModelRestApi(OkHttpClient client) throws IOException, JSONException, IllegalStateException {
        Request request = getRequest(HTTPS + host + ROUTER_MODEL_REST_API);
        if (request == null) return;

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                String body = response.body().string().trim();
                if (body.startsWith("[")) {
                    JSONArray jsonArray = new JSONArray(body);
                    if (jsonArray.length() > 0) {
                        model = jsonArray.getJSONObject(0).getString(Sms.MODEL_KEY);
                    }
                } else {
                    model = new JSONObject(body).getString(Sms.MODEL_KEY);
                }
                Log.d(AppController.LOG_TAG, "Модель роутера = " + model);
            } else {
                Log.d(AppController.LOG_TAG, "Ошибка от роутера: при запросе модели " + response.code() + response.body().string());
                throw new IOException(appController.getString(R.string.router_returned_error_code) + SPACE + response.code());
            }
        }
    }

    private void loadInboxSmsAndBuildMothersRestApi(OkHttpClient client) throws IOException, JSONException, IllegalStateException {
        decodedPartSmsList = new ArrayList<>();
        motherSmsList = new ArrayList<>();

        Request request = getRequest(HTTPS + host + READ_SMS_COMMAND_DETAIL_REST_API);
        if (request == null) return;

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                Log.d(AppController.LOG_TAG, "ответ роутера при REST API запросе смс: " + response.code());
                JSONArray jsonArray = new JSONArray(response.body().string());
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject sms = jsonArray.getJSONObject(i);
                    Sms newSms = new Sms(sms.getString(Sms.PHONE_KEY), sms.getString(Sms.TIMESTAMP_KEY), sms.getString(Sms.MESSAGE_KEY),
                            sms.getString(Sms.PDU_KEY), sms.getString(Sms.SOURCE_KEY), sms.getString(Sms.TYPE_KEY));
                    Log.d(AppController.LOG_TAG, "ID =  " + sms.getString(Sms.ID_KEY));
                    newSms.decodePduToText();
                    if (newSms.isValidSms()) {
                        decodedPartSmsList.add(newSms);
                    }
                }
            } else {
                Log.d(AppController.LOG_TAG, "Ошибка в ответе роутера при REST API запросе смс: " + response.code());
                throw new IOException(appController.getString(R.string.router_returned_error_code) + SPACE + response.code());
            }

        }
        MotherSmsFactory.fillMotherSmsList(decodedPartSmsList, motherSmsList);
    }

    public void removeMotherSmsByDateRestApi(MotherSms motherSms) {
        OkHttpClient client = appController.getOkHttpClient();
        if (client == null) {
            sendInfoMessageToActivity(appController.getString(R.string.okhttp_error));
            return;
        }

        isConnecting = true;
        startTime = SystemClock.elapsedRealtime();
        sendInfoMessageToActivity(appController.getString(R.string.removal));
        model = AppController.EMPTY_STRING;
        checkCredential();

        try {
            removePartsSmsRestApi(motherSms, client);
            readRouterModelRestApi(client);
            loadInboxSmsAndBuildMothersRestApi(client);
            sendInfoMessageToActivity(getFinalMessageToActivity(delResultCode));
        } catch (Exception e) {
            Log.d(AppController.LOG_TAG, "Конец удаления всех смс, НЕУДАЧА");
            if (model.isEmpty()) {
                if (!motherSmsList.isEmpty()) {
                    errorCatch(e);
                } else {
                    sendInfoMessageToActivity(appController.getString(R.string.inbox_empty));
                }

            } else {
                sendInfoMessageToActivity(AppController.EMPTY_STRING);
            }
        }

        sendRedrawSmsCommand();
        isConnecting = false;
    }

    private void removePartsSmsRestApi(MotherSms motherSms, OkHttpClient client) {
        delResultCode = 0;
        String indices = getIndicesRestApi(client, motherSms);
        makePauseBetweenCommand();
        if (!indices.isEmpty()) {
            Request request = getDelRequest(HTTPS + host + REMOVE_SMS_COMMAND_DETAIL_REST_API + indices);
            if (request == null) return;

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    Log.d(AppController.LOG_TAG, "Ошибка в ответе роутера при REST API удалении смс: " + response.code());
                    if (response.code() == HttpURLConnection.HTTP_NOT_FOUND) {
                        throw new IOException(ERROR_NO_SUCH_ITEM);
                    }
                    String body = response.body().string().trim();
                    if (body.startsWith("[")) {
                        JSONArray jsonArray = new JSONArray(body);
                        if (jsonArray.length() > 0) {
                            throw new IOException(jsonArray.getJSONObject(0).getString(Sms.DETAIL_KEY));
                        }
                    } else {
                        throw new IOException(new JSONObject(body).getString(Sms.DETAIL_KEY));
                    }

                } else {
                    Log.d(AppController.LOG_TAG, "Смс удалены при REST API : " + response.code());
                }
            } catch (Exception e) {
                Log.d(AppController.LOG_TAG, "Ошибка при запросе индекса при REST API " + e);
                String msg = e.getMessage();
                if (msg != null) {
                    setDelResultCode(msg);
                }
            }
            makePauseBetweenCommand();

        } else {
            motherSmsList.remove(motherSms);
        }
    }

    @NonNull
    private String getIndicesRestApi(OkHttpClient client, MotherSms motherSms) {
        StringBuilder indices = new StringBuilder(AppController.EMPTY_STRING);

        Request request = getRequest(HTTPS + host + READ_SMS_COMMAND_DETAIL_REST_API);
        if (request == null) return indices.toString();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                JSONArray jsonArray = new JSONArray(response.body().string());
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject smsFind = jsonArray.getJSONObject(i);
                    if (MotherSmsFactory.isSameTimestamp(smsFind.getString(Sms.TIMESTAMP_KEY), motherSms.getGroupTimestamp())
                            && motherSms.getSource().equals(smsFind.getString(Sms.SOURCE_KEY))
                            && motherSms.getPhone().equals(smsFind.getString(Sms.PHONE_KEY))
                            && motherSms.isMessageContains(smsFind.getString(Sms.MESSAGE_KEY))) {
                        if (indices.length() != 0) {
                            indices.append(",");
                        }
                        indices.append(smsFind.getString(Sms.ID_KEY));
                    }
                }
            } else {
                Log.d(AppController.LOG_TAG, "Ошибка в ответе роутера при REST API запросе смс: " + response.code());
            }

        } catch (Exception e) {
            Log.d(AppController.LOG_TAG, "Ошибка при запросе индекса при REST API " + e);
            String msg = e.getMessage();
            if (msg != null) {
                setDelResultCode(msg);
            }
        }
        Log.d(AppController.LOG_TAG, "Собраны индексы REST API на удаление = " + indices);
        return indices.toString();
    }

    @Nullable
    private Request getRequest(String url) {
        Request request;
        try {
            request = new Request.Builder().url(url)
                    .addHeader("Authorization", credential)
                    .get()
                    .build();
        } catch (Exception e) {
            request = null;
        }
        return request;
    }

    @Nullable
    private Request getDelRequest(String url) {
        Request request;
        try {
            request = new Request.Builder().url(url)
                    .addHeader("Authorization", credential)
                    .delete()
                    .build();
        } catch (Exception e) {
            request = null;
        }
        return request;
    }

    private void checkCredential() {
        if (credential.isEmpty()) {
            credential = Credentials.basic(user, pass);
        }
    }



    private void setDelResultCode(@NonNull String msg) {
        if (msg.startsWith(ERROR_NO_PERMISSIONS)) {
            delResultCode = SMS_REMOVED_NO_PERM;
        }
        if (msg.contains(ERROR_LTE_NOT_ACTIVE)) {
            delResultCode = LTE_NOT_ACTIVE;
        }
        if (msg.contains(ERROR_NO_SUCH_ITEM)) {
            delResultCode = SMS_REMOVED_NO_SUCH_ITEM;
        }
    }

}
