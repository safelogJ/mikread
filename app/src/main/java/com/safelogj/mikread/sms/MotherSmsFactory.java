package com.safelogj.mikread.sms;


import androidx.annotation.NonNull;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MotherSmsFactory {
    private static final String SMS_ROW_PATTERN = "%ninterface: %s%ndate: %s%nphone: %s%n%n";
    private static final String TIME_FORMAT_PATTERN = "yyyy-MM-dd HH:mm:ssZ";
    private static final Pattern COLON_OFFSET_PATTERN = Pattern.compile("([+-]\\d{2}):(\\d{2})$");
    private static final long TIME_WINDOW_MS = 7_000; // 7 секунд

    private MotherSmsFactory() {
    }

    public static boolean isSameTimestamp(String timestamp, long motherTimestamp) {
        long timeMillis = parseTimeToMillis(timestamp);
        return Math.abs(timeMillis - motherTimestamp) <= TIME_WINDOW_MS;
    }

    public static void fillMotherSmsList(List<Sms> smsList, List<MotherSms> mothersList) {
        for (Sms sms : smsList) {
            long timeMillis = parseTimeToMillis(sms.getTimestamp());
            String smsSource = sms.getSource();
            String smsPhone = sms.getPhone();
            boolean addedToExistingGroup = false;
            for (MotherSms mother : mothersList) {
                long delta = Math.abs(timeMillis - mother.getGroupTimestamp());
                if (delta <= TIME_WINDOW_MS && smsSource.equals(mother.getSource()) && smsPhone.equals(mother.getPhone())) {
                    mother.addPart(sms);
                    addedToExistingGroup = true;
                    break;
                }
            }
            if (!addedToExistingGroup) {
                MotherSms newMotherSms = new MotherSms(timeMillis, smsSource, smsPhone);
                newMotherSms.addPart(sms);
                mothersList.add(newMotherSms);
            }
        }
        concatByUdh(mothersList);
        Collections.sort(mothersList, (o1, o2) -> Long.compare(o1.getGroupTimestamp(), o2.getGroupTimestamp()));
    }

    private static void concatByUdh(List<MotherSms> mothersList) {
        for (MotherSms motherSms: mothersList) {
            List<Sms> smsList = motherSms.getParts();
            smsList = filterUniqueAndLongestMessages(smsList);
            Collections.sort(smsList, (o1, o2) -> Integer.compare(o1.getUdh(), o2.getUdh()));
            StringBuilder builder = new StringBuilder();
            for(Sms sms: smsList) {
                motherSms.setStringTimestamp(sms.getTimestamp());
                builder.append(sms.getDecodeMessage());
            }
            String title = String.format(SMS_ROW_PATTERN, motherSms.getSource(), motherSms.getStringTimestamp(), motherSms.getPhone());
            motherSms.setFinalText(title + builder + "\n");
        }
    }

    private static List<Sms> filterUniqueAndLongestMessages(List<Sms> smsList) {
        Map<String, Sms> uniqueTextMap = new LinkedHashMap<>();
        for (Sms sms : smsList) {
            String decodeMessage = sms.getDecodeMessage();
            if (!uniqueTextMap.containsKey(decodeMessage)) {
                uniqueTextMap.put(decodeMessage, sms);
            }
        }

        // Получаем список уникальных Sms (в порядке их первого появления)
        List<Sms> uniqueSmsList = new ArrayList<>(uniqueTextMap.values());
        int n = uniqueSmsList.size();
        if (n <= 1) {
            return uniqueSmsList;
        }
        // Шаг 2: Поиск и удаление префиксов.
        // В массиве remove[i] == true, если сообщение с индексом i должно быть удалено.
        boolean[] remove = new boolean[n];
        // Двойной цикл для попарного сравнения
        for (int i = 0; i < n - 1; i++) {
            if (remove[i]) continue;  // Если Sms[i] уже помечено как удаляемое, пропускаем его

            String decodeMessageI = uniqueSmsList.get(i).getDecodeMessage();
            for (int j = i + 1; j < n; j++) {
                if (!remove[j]) {
                    String decodeMessageJ = uniqueSmsList.get(j).getDecodeMessage();
                    // Проверка на префикс:
                    // Сценарий 1: decodeMessageJ длиннее и начинается с decodeMessageI (decodeMessageI - префикс/кусок)
                    if (decodeMessageJ.length() > decodeMessageI.length() && decodeMessageJ.startsWith(decodeMessageI)) {
                        // decodeMessageI — более короткий кусок decodeMessageJ, поэтому decodeMessageI удаляем
                        remove[i] = true;
                        break; // decodeMessageI удалено, нет смысла сравнивать его с другими
                    }
                    // Сценарий 2: decodeMessageI длиннее и начинается с decodeMessageJ (decodeMessageJ - префикс/кусок)
                    else if (decodeMessageI.length() > decodeMessageJ.length() && decodeMessageI.startsWith(decodeMessageJ)) {
                        // decodeMessageJ — более короткий кусок decodeMessageI, поэтому decodeMessageJ удаляем
                        remove[j] = true;
                        // Продолжаем цикл j, так как decodeMessageI (самое длинное на данный момент) может быть префиксом еще более длинного.
                    }
                }

            }
        }

        // Шаг 3: Сборка финального списка.
        List<Sms> fullList = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (!remove[i]) {
                fullList.add(uniqueSmsList.get(i));
            }
        }
        return fullList;
    }

    private static long parseTimeToMillis(String timeString) {
        String processedString = getParsibleString(timeString);

        // Формат с "Z" в конце шаблона требует смещения в виде "+HHMM"
        SimpleDateFormat sdf = new SimpleDateFormat(TIME_FORMAT_PATTERN, Locale.US);

        try {
            Date date = sdf.parse(processedString);
            if (date != null) {
                return date.getTime();
            }
        } catch (ParseException e) {
            //
        }

        return 0L;
    }

    @NonNull
    private static String getParsibleString(String timeString) {
        String processedString = timeString;

        // 1. Обработка смещения: SimpleDateFormat (в API 21) не поддерживает двоеточие в "+HH:MM" (нужно "+HHMM")
        Matcher matcher = COLON_OFFSET_PATTERN.matcher(timeString);
        if (matcher.find()) {
            // Превращаем "+03:00" → "+0300"
            processedString = matcher.replaceFirst("$1$2");
        }

        // 2. Обработка "Z" (индикатор UTC): SimpleDateFormat ожидает "+0000"
        if (processedString.endsWith("Z")) {
            // Превращаем "Z" → "+0000"
            processedString = processedString.replace("Z", "+0000");
        }
        return processedString;
    }
}


