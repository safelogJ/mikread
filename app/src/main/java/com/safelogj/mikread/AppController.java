package com.safelogj.mikread;

import android.app.Activity;
import android.app.Application;
import android.net.Uri;
import android.os.Bundle;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.safelogj.mikread.helpers.BaseRouterComparator;
import com.safelogj.mikread.helpers.RouterHostComparator;
import com.safelogj.mikread.helpers.RouterNoteComparator;
import com.safelogj.mikread.helpers.RouterUserComparator;
import com.safelogj.mikread.sms.MotherSms;
import com.safelogj.mikread.sms.MotherSmsFactory;
import com.safelogj.mikread.sms.Sms;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.OkHttpClient;


public class AppController extends Application {

    public static final String LOG_TAG = "mikread";
    public static final String LOCALHOST_KEY = "localHost";
    public static final String EMPTY_STRING = "";
    public static final int HOST_COMPARATOR = 0;
    public static final int USER_COMPARATOR = 1;
    public static final int NOTE_COMPARATOR = 2;
    private static final String ROUTERS = "routers";
    private static final String ROUTERS_JSON = "routers.txt";
    private static final String ROUTERS_LIST = "routersList";
    private static final String ROUTER_HOST = "routerHost";
    private static final String ROUTER_USER = "routerUser";
    private static final String ROUTER_PASS = "routerPass";
    private static final String ROUTER_NOTE = "routerNote";
    private static final String CURRENT_ROUTER = "currentRouter";
    private static final String KEY_ALIAS = "MikrotikRouterKeyAlias";
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String SMS_IN_FILE_PHONE_PATTERN = "{\"phone\":\"";
    private static final String SMS_IN_FILE_TIMESTAMP_PATTERN = "\",\"timestamp\":\"";
    private static final String SMS_IN_FILE_MESSAGE_PATTERN = "\",\"message\":\"";
    private static final String SMS_IN_FILE_PDU_PATTERN = "\",\"pdu\":\"";
    private static final String SMS_IN_FILE_SOURCE_PATTERN = "\",\"source\":\"";
    private static final String SMS_IN_FILE_TYPE_PATTERN = "\",\"type\":\"";
    private static final String SMS_IN_FILE_END_PATTERN = "\"}";
    private static final int GCM_TAG_LENGTH = 16; // Длина аутентификационного тега в байтах (128 бит)
    private static final int AES_KEY_SIZE = 256;
    private static final String ENCRYPTED_DATA_KEY = "encryptedData"; // Ключ для хранения зашифрованных данных в файле
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Map<String, MikrotikRouter> routersMap = new LinkedHashMap<>();
    private final BaseRouterComparator[] comparators = new BaseRouterComparator[]{
            new RouterHostComparator(),
            new RouterUserComparator(),
            new RouterNoteComparator()
    };
    private volatile List<MotherSms> motherFileSmsList = new ArrayList<>();
    private MikrotikRouter currentRouter = new MikrotikRouter(this);
    private String selectedHost = EMPTY_STRING;
    private WeakReference<Activity> currentActivityRef;
    private MikrotikRouter connectedRouter = new MikrotikRouter(this);
    private Cipher mCipher;
    private OkHttpClient okHttpClient;


    @Override
    public void onCreate() {
        super.onCreate();
        regActivityListener();
        readRoutersListAndSettingsEncrypted();
        initOkHttpClient();
    }

    public Map<String, MikrotikRouter> getRoutersMap() {
        return routersMap;
    }

    public MikrotikRouter getCurrentRouter() {
        return currentRouter;
    }

    public void setCurrentRouter(MikrotikRouter currentRouter) {
        this.currentRouter = currentRouter;
    }

    public String getSelectedHost() {
        return selectedHost;
    }

    public List<MotherSms> getMotherFileSmsList() {
        return motherFileSmsList;
    }

    public void setMotherFileSmsList(List<MotherSms> motherFileSmsList) {
        this.motherFileSmsList = motherFileSmsList;
    }

    public WeakReference<Activity> getCurrentActivityRef() {
        return currentActivityRef;
    }

    public void setSelectedHost(String selectedHost) {
        this.selectedHost = selectedHost;
    }

    public MikrotikRouter getConnectedRouter() {
        return connectedRouter;
    }

    @Nullable
    public OkHttpClient getOkHttpClient() {
        return okHttpClient;
    }

    public void writeSettingsToFile() {
        executor.execute(this::writeRoutersListAndSettingsEncrypted);
    }

    public void addRouterToMap(MikrotikRouter router) {
        routersMap.put(router.getHost(), router);
    }

    public void delRouterFromMap(String host) {
        routersMap.remove(host);
    }

    public void connectToValidRouter(MikrotikRouter router) {
        connectedRouter = router;
        executor.execute(router::readSmsFromRouter);
    }

    public void removeMotherSms(MotherSms sms) {
        executor.execute(() -> connectedRouter.removeSmsFromRouter(sms));
    }

    public void sortRoutersMap(int comparatorIdx) {
        List<MikrotikRouter> entries = new ArrayList<>(routersMap.values());
        Collections.sort(entries, comparators[comparatorIdx]);
        comparators[comparatorIdx].toggleOrder();
        LinkedHashMap<String, MikrotikRouter> sortedMap = new LinkedHashMap<>();
        for (MikrotikRouter router : entries) {
            sortedMap.put(router.getHost(), router);
        }
        routersMap.clear();
        routersMap.putAll(sortedMap);
    }

    public void buildSmsFromFile(Uri uri) {
        executor.execute(() -> {
            setMotherFileSmsList(new ArrayList<>());
            List<Sms> decodedPartFileSmsList = readSmsFile(uri);
            if (!decodedPartFileSmsList.isEmpty()) {
                MotherSmsFactory.fillMotherSmsList(decodedPartFileSmsList, motherFileSmsList);
            }
            drawSmsFile();
        });
    }

    private void drawSmsFile() {
        if (currentActivityRef != null) {
            Activity activity = currentActivityRef.get();
            if (activity instanceof FileActivity fileActivity) {
                fileActivity.reDrawSmsList();
            }
        }
    }

    private List<Sms> readSmsFile(@NonNull Uri uri) {
        List<Sms> result = new ArrayList<>();

        try (InputStream is = getContentResolver().openInputStream(uri);
             InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {

            StringBuilder sb = new StringBuilder();
            char[] buf = new char[4096];
            int n;
            while ((n = reader.read(buf)) != -1) {
                sb.append(buf, 0, n);
            }

            String fileText = sb.toString();
            int patternLength = SMS_IN_FILE_PHONE_PATTERN.length();
            int startIdx = fileText.indexOf(SMS_IN_FILE_PHONE_PATTERN);

            while (startIdx >= 0) {
                int nextIdx = fileText.indexOf(SMS_IN_FILE_PHONE_PATTERN, startIdx + patternLength);

                String jsonSms;
                if (nextIdx >= 0) {
                    jsonSms = fileText.substring(startIdx, nextIdx).trim();
                    startIdx = nextIdx;
                } else {
                    jsonSms = fileText.substring(startIdx).trim();
                    startIdx = -1;
                }
                Sms sms = parseJsonSms(jsonSms);
                sms.decodePduToText();
                if (sms.isValidSms()) {
                    result.add(sms);
                }

            }

        } catch (IOException e) {
            result = new ArrayList<>();
        }

        return result;
    }
    @NonNull
    private Sms parseJsonSms(@NonNull String line) {
        if (line.isEmpty()) {
            return Sms.empty();
        }

        String phone = extractBetween(line, SMS_IN_FILE_PHONE_PATTERN, SMS_IN_FILE_TIMESTAMP_PATTERN);
        String timestamp = extractBetween(line, SMS_IN_FILE_TIMESTAMP_PATTERN, SMS_IN_FILE_MESSAGE_PATTERN);
        String message = extractBetween(line, SMS_IN_FILE_MESSAGE_PATTERN, SMS_IN_FILE_PDU_PATTERN);
        String pdu = extractBetween(line, SMS_IN_FILE_PDU_PATTERN, SMS_IN_FILE_SOURCE_PATTERN);
        String source = extractBetween(line, SMS_IN_FILE_SOURCE_PATTERN, SMS_IN_FILE_TYPE_PATTERN);
        String type = extractBetween(line, SMS_IN_FILE_TYPE_PATTERN, SMS_IN_FILE_END_PATTERN);

        return new Sms(phone, timestamp, message, pdu, source, type);
    }

    private String extractBetween(String text, String start, String end) {
        if (text == null) return AppController.EMPTY_STRING;

        int startIdx = text.indexOf(start);
        if (startIdx < 0) return AppController.EMPTY_STRING;

        startIdx += start.length();
        int endIdx = text.indexOf(end, startIdx);
        if (endIdx < 0) return AppController.EMPTY_STRING;

        return text.substring(startIdx, endIdx);
    }

    private void buildRouterJson(JSONObject routerJson, MikrotikRouter router) throws JSONException {
        String host = router.getHost();
        routerJson.put(ROUTER_HOST, host != null ? host : EMPTY_STRING);
        String user = router.getUser();
        routerJson.put(ROUTER_USER, user != null ? user : EMPTY_STRING);
        String pass = router.getPass();
        routerJson.put(ROUTER_PASS, pass != null ? pass : EMPTY_STRING);
        String note = router.getNote();
        routerJson.put(ROUTER_NOTE, note != null ? note : EMPTY_STRING);
    }

    private void readRouterJson(JSONObject routerJson, MikrotikRouter router) {
        String host = routerJson.optString(ROUTER_HOST, EMPTY_STRING);
        String user = routerJson.optString(ROUTER_USER, EMPTY_STRING);
        String pass = routerJson.optString(ROUTER_PASS, EMPTY_STRING);
        String note = routerJson.optString(ROUTER_NOTE, EMPTY_STRING);
        router.setHost(host);
        router.setUser(user);
        router.setPass(pass);
        router.setNote(note);
    }

    private void regActivityListener() {
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
                //
            }

            @Override
            public void onActivityStarted(@NonNull Activity activity) {
                currentActivityRef = new WeakReference<>(activity);
            }

            @Override
            public void onActivityResumed(@NonNull Activity activity) {
                currentActivityRef = new WeakReference<>(activity);
            }

            @Override
            public void onActivityPaused(@NonNull Activity activity) {
                //
            }

            @Override
            public void onActivityStopped(@NonNull Activity activity) {
                Activity current = currentActivityRef != null ? currentActivityRef.get() : null;
                if (current == activity) {
                    currentActivityRef = null;
                }
            }

            @Override
            public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
                //
            }

            @Override
            public void onActivityDestroyed(@NonNull Activity activity) {
                //
            }
        });
    }


    public void writeRoutersListAndSettingsEncrypted() {
        File routersListDir = new File(getFilesDir(), ROUTERS);
        if (!routersListDir.exists() && !routersListDir.mkdirs()) {
            Log.d(LOG_TAG, "Failed to create directory.");
            return;
        }

        File routersListFile = new File(routersListDir, ROUTERS_JSON);

        JSONObject rootJson = new JSONObject();
        JSONObject routersJson = new JSONObject();
        try {
            for (Map.Entry<String, MikrotikRouter> entry : routersMap.entrySet()) {
                JSONObject routerJson = new JSONObject();
                buildRouterJson(routerJson, entry.getValue()); // Пароли здесь в открытом виде
                routersJson.put(entry.getKey(), routerJson);
            }

            JSONObject currentRouterJson = new JSONObject();
            buildRouterJson(currentRouterJson, currentRouter); // Пароль здесь в открытом виде

            rootJson.put(ROUTERS_LIST, routersJson);
            rootJson.put(CURRENT_ROUTER, currentRouterJson);

            // 2. Шифрование всего JSON-контента
            String rawJsonString = rootJson.toString();
            byte[] rawJsonBytes = rawJsonString.getBytes(StandardCharsets.UTF_8);
            byte[] encryptedCombinedBytes = encrypt(rawJsonBytes);
            String encryptedBase64 = Base64.encodeToString(encryptedCombinedBytes, Base64.NO_WRAP);

            // 3. Создание JSON-оболочки для записи в файл
            JSONObject fileWrapper = new JSONObject();
            fileWrapper.put(ENCRYPTED_DATA_KEY, encryptedBase64);

            // 4. Запись JSON-оболочки в файл
            try (FileWriter file = new FileWriter(routersListFile)) {
                file.write(fileWrapper.toString(4));
            }

        } catch (
                Exception e) { // Ловим Exception, т.к. Keystore/Cipher может бросить разные исключения
            Log.d(LOG_TAG, "Error writing encrypted JSON file or key management failure: ", e);
        }
    }

    public void readRoutersListAndSettingsEncrypted() {
        File routersListDir = new File(getFilesDir(), ROUTERS);
        File routersListFile = new File(routersListDir, ROUTERS_JSON);
        StringBuilder fileContent = new StringBuilder();

        if (!routersListFile.exists()) {
            Log.d(LOG_TAG, "Encrypted settings file not found.");
            return;
        }

        // 1. Чтение содержимого файла-оболочки
        try (FileReader reader = new FileReader(routersListFile)) {
            char[] buffer = new char[1024];
            int length;
            while ((length = reader.read(buffer)) != -1) {
                fileContent.append(buffer, 0, length);
            }
        } catch (IOException e) {
            Log.d(LOG_TAG, "Error reading encrypted settings file: ", e);
            return;
        }

        // 2. Извлечение и дешифрование данных
        try {
            JSONObject fileWrapper = new JSONObject(fileContent.toString());
            String encryptedBase64 = fileWrapper.getString(ENCRYPTED_DATA_KEY);

            // Декодирование и дешифрование
            byte[] combinedBytes = Base64.decode(encryptedBase64, Base64.DEFAULT); // Base64.DEFAULT безопасно для декодирования
            byte[] decryptedBytes = decrypt(combinedBytes);
            String rawJsonString = new String(decryptedBytes, StandardCharsets.UTF_8);

            // 3. Парсинг дешифрованного полного JSON
            JSONObject rootJson = new JSONObject(rawJsonString);
            JSONObject routersJson = rootJson.getJSONObject(ROUTERS_LIST);

            routersMap.clear(); // Очищаем старые данные перед чтением новых
            for (Iterator<String> it = routersJson.keys(); it.hasNext(); ) {
                String key = it.next();
                JSONObject routerJson = routersJson.getJSONObject(key);

                MikrotikRouter router = new MikrotikRouter(this);
                readRouterJson(routerJson, router); // Использует открытый пароль из JSON
                routersMap.put(key, router);
            }

            JSONObject currentRouterJson = rootJson.getJSONObject(CURRENT_ROUTER);
            readRouterJson(currentRouterJson, currentRouter); // Использует открытый пароль из JSON

        } catch (
                Exception e) { // Ловим Exception, т.к. Keystore/Cipher может бросить разные исключения
            Log.d(LOG_TAG, "Error reading or decrypting full JSON data:  ", e);
        }
    }

    private SecretKey getOrCreateSecretKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);

        // Попытка получить существующий ключ
        if (keyStore.containsAlias(KEY_ALIAS)) {
            KeyStore.SecretKeyEntry entry = (KeyStore.SecretKeyEntry) keyStore.getEntry(KEY_ALIAS, null);
            return entry.getSecretKey();
        }

        // Если ключа нет, создаем новый (Требуется API 23+ для KeyGenParameterSpec)
        KeyGenerator keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);

        // Настройка параметров: AES/GCM/NoPadding
        keyGenerator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(AES_KEY_SIZE)
                .build());

        return keyGenerator.generateKey();

    }


    private byte[] encrypt(byte[] dataBytes) throws Exception {
        SecretKey secretKey = getOrCreateSecretKey();
        if (mCipher == null) {
            mCipher = Cipher.getInstance(TRANSFORMATION);
        }
        mCipher.init(Cipher.ENCRYPT_MODE, secretKey);

        byte[] iv = mCipher.getIV();
        byte[] encryptedData = mCipher.doFinal(dataBytes);
        byte[] combined = new byte[1 + iv.length + encryptedData.length];
        combined[0] = (byte) iv.length; // Сохраняем длину IV в первом байте
        System.arraycopy(iv, 0, combined, 1, iv.length); // Копируем IV начиная со второго байта
        System.arraycopy(encryptedData, 0, combined, 1 + iv.length, encryptedData.length); // Копируем данные
        return combined;
    }

    private byte[] decrypt(byte[] combinedBytes) throws Exception {
        // Минимальная длина: 1 байт (длина IV) + 1 байт (IV) + 16 байт (GCM Tag) = 18 байт
        if (combinedBytes.length < 1 + GCM_TAG_LENGTH) {
            throw new InvalidKeyException("Combined data too short to contain IV length and GCM Tag.");
        }

        int ivLength = combinedBytes[0] & 0xFF; // Получаем фактическую длину IV из первого байта
        // Проверяем, достаточно ли данных для IV и GCM Tag
        if (combinedBytes.length < 1 + ivLength + GCM_TAG_LENGTH) {
            throw new InvalidKeyException("IV length leads to combined data too short for GCM Tag.");
        }
        // Извлекаем IV
        byte[] iv = Arrays.copyOfRange(combinedBytes, 1, 1 + ivLength);
        // Извлекаем зашифрованные данные (начинаются после байта длины и IV)
        byte[] encryptedData = Arrays.copyOfRange(combinedBytes, 1 + ivLength, combinedBytes.length);

        SecretKey secretKey = getOrCreateSecretKey();
        mCipher = Cipher.getInstance(TRANSFORMATION);
        // GCM_TAG_LENGTH * 8, так как длина тега указывается в битах (16 байт * 8 = 128 бит)
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);

        mCipher.init(Cipher.DECRYPT_MODE, secretKey, spec);
        return mCipher.doFinal(encryptedData);
    }

    private void initOkHttpClient() {
        try {
            final TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {

                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                            //
                        }

                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {

                            for (X509Certificate cert : chain) {
                                cert.checkValidity(); // Выкинет, если дата сертификата истекла
                            }
                        }

                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }
                    }
            };

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());


            okHttpClient = new OkHttpClient.Builder()
                    .sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustAllCerts[0])
                    .hostnameVerifier((hostname, session) -> true)
                    .connectTimeout(60, TimeUnit.SECONDS) // Время на установку связи с роутером
                    .writeTimeout(15, TimeUnit.SECONDS)   // Время на отправку данных
                    .readTimeout(60, TimeUnit.SECONDS)    // Время на ожидание ответа от роутера
                    .callTimeout(70, TimeUnit.SECONDS) // Общее время на весь запрос с ответом, чтоб не переподключалось много раз
                    .retryOnConnectionFailure(true)
                    // .addInterceptor(new LoggingInterceptor())
                    // .cookieJar(new RouterCookieJar())
                    .build();
        } catch (Exception e) {
            Log.d(LOG_TAG, "Ошибка создания : OkHttpClient" + e.getMessage());
        }
    }

}
