package com.safelogj.mikread.sms;

import android.util.Log;

import androidx.annotation.NonNull;

import com.safelogj.mikread.AppController;

import java.nio.charset.StandardCharsets;

public class Sms {
    public static final String ID_KEY = ".id";
    public static final String PHONE_KEY = "phone";
    public static final String TIMESTAMP_KEY = "timestamp";
    public static final String MESSAGE_KEY = "message";
    public static final String PDU_KEY = "pdu";
    public static final String SOURCE_KEY = "source";
    public static final String TYPE_KEY = "type";
    public static final String MODEL_KEY = "model";
    public static final String DETAIL_KEY = "detail";
    private static final String GSM_ALPHABET = "@£$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞ\u001BÆæßÉ !\"#¤%&'()*+,-./0123456789:;<=>?¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿abcdefghijklmnopqrstuvwxyzäöñüà";
    private final String phone;
    private final String timestamp;
    private final String message;
    private final String source;
    private String pdu;
    private String type;
    private String decodeMessage = AppController.EMPTY_STRING;
    private int udh = -1;

    public static Sms empty() {
        return new Sms(null, null, null, null, null, null);
    }

    public Sms(String phone, String timestamp, String message, String pdu, String source, String type) {
        this.phone = phone == null ? AppController.EMPTY_STRING : phone.trim();
        this.timestamp = timestamp == null ? AppController.EMPTY_STRING : timestamp.trim();
        this.message = message == null ? AppController.EMPTY_STRING : message;
        this.pdu = pdu == null ? AppController.EMPTY_STRING : pdu;
        this.source = source == null ? AppController.EMPTY_STRING : source.trim();
        this.type = type == null ? AppController.EMPTY_STRING : type;
    }

    public boolean isValidSms() {
        return !phone.isEmpty() && !timestamp.isEmpty() && !decodeMessage.isEmpty();
    }

    @NonNull
    public String getTimestamp() {
        return timestamp;
    }

    @NonNull
    public String getDecodeMessage() {
        return decodeMessage;
    }


    public int getUdh() {
        return udh;
    }

    @NonNull
    public String getPhone() {
        return phone;
    }

    @NonNull
    public String getMessage() {
        return message;
    }

    @NonNull
    public String getSource() {
        return source;
    }

    public void decodePduToText() {
        if (pdu.isEmpty()) {
            pdu = getPduFromMessage(message);
        }
        if (pdu.isEmpty()) return;

        byte[] data = hexStringPduToByteArray(pdu);
        // Минимальный PDU содержит SMSC(1+7), FirstOctet(1), OA(2+), PID(1), DCS(1), SCTS(7), UDL(1)
        if (data.length < 12) return; // минимальная длина PDU для анализа

        int index = 0;
        // 1. SMSC (Service Center Address)
        // Первый байт — длина номера сервис-центра в байтах.
        int smscLen = data[index++] & 0xFF; // переводим значение byte в int зануляя первые 24 бита, чтоб получить правильное положительное значение
        index += smscLen;
        if (index >= data.length) return;

        // 2. First Octet (Тип PDU)
        // Бит 6 (0x40) указывает на наличие User Data Header (UDHI).
        int firstOctet = data[index++] & 0xFF;

        // 3. Originating Address (Номер отправителя)
        if (index >= data.length) return;
        int addrLen = data[index++] & 0xFF; // Длина номера в ПОЛУ-октетах (цифрах)
        if (index >= data.length) return;
        int addrType = data[index++] & 0xFF; // Формат номера (International, National и т.д.)
        // Расчет байтов: на 1 байт приходится 2 цифры номера. +1 для учета нечетной длины.
        int addrBytes = (addrLen + 1) / 2;

        if (index + addrBytes > data.length) return;
        index += addrBytes;

        // 4. PID (Protocol Identifier)
        if (index >= data.length) return;
        index++; // Обычно 0x00 для стандартных SMS

        // 5. DCS (Data Coding Scheme) — КРИТИЧНО для декодирования
        // Определяет: 7-bit, 8-bit (binary) или 16-bit (UCS2/UTF-16).
        if (index >= data.length) return;
        int dcs = data[index++] & 0xFF;

        // 6. Timestamp (Service Centre Time Stamp) 7 байт в формате BCD (год, месяц, день, час, мин, сек, таймзона).
        index += 7;

        // 7. User Data Length (UDL)
        // Для 7-битной кодировки это кол-во СИМВОЛОВ, для 8/16-битной — кол-во БАЙТ.
        if (index >= data.length) return;
        int udl = data[index++] & 0xFF;

        // По умолчанию считаем, что UDH нет
        udh = -1; // Сброс номера части (по умолчанию -1, если сообщение одиночное)
        int shift = 0;

        // 8. User Data Header (если есть)
        // 8. Разбор User Data Header (UDH) — если сообщение склееное (multipart)
        if ((firstOctet & 0x40) != 0) { // UDHI = 1
            if (index >= data.length) return;
            int udhLen = data[index++] & 0xFF; // Длина всего заголовка UDH

            if (index + udhLen < data.length && udhLen >= 5) {
                int iei = data[index] & 0xFF; // Information Element Identifier Идентификатор элемента (0x00 или 0x08)
                int ieLen = data[index + 1] & 0xFF; // Длина данных элемента
                // Проверяем только стандартные типы UDH (8-bit и 16-bit reference)
                if ((iei == 0x00 && ieLen == 3) || (iei == 0x08 && ieLen == 4)) { // 0x00 = 8-bit reference number, 0x08 = 16-bit reference number
                    //  int ref = data[index + 2] & 0xFF; // Message reference
                    //  int total = data[index + 3] & 0xFF; // Total parts
                    if (iei == 0x00) {
                        udh = data[index + 4] & 0xFF; // This part number Порядковый номер части
                    } else {
                        udh = data[index + 5] & 0xFF; // 16-bit reference  Порядковый номер части (16-bit вариант)
                    }
                }
            }

            if ((dcs & 0x0C) == 0x08 || (dcs & 0x0C) == 0x04) { // UCS2 или 8-bit.
                // Пропускаем заголовок, сдвигаем индекс за пределы заголовка к началу самого текста.
                index += udhLen;
                udl -= (udhLen + 1); // UCS2, 8-bit: вычитаем байты (сам заголовок + байт длины)
            } else { // ((dcs & 0x0C) == 0x00) 7-bit (и зарезервированные 11xx) трактуем как 7-bit GSM
                // Если кодировка 7-битная, UDL включает в себя и UDH, выраженный в "септетах".
                index--; // возвращаем индекс на позицию udhLen из позиции iei
                shift = getUdhSeptetsCount(udhLen + 1); // получаем кол-во септетов для (udh + его длинна) которые надо пропустить перед текстом
            }
        }

        // 9. Извлечение полезной нагрузки (User Data)
        if (index >= data.length) return;
        int userDataLength = data.length - index; // тут userDataLength равен udl
        // 10. Декодирование текста в зависимости от DCS
        try {
            if ((dcs & 0x0C) == 0x08) { // Проверка бит 2 и 3 в DCS: 10xx = UCS2 (UTF-16BE)
                decodeMessage = new String(data, index, userDataLength, StandardCharsets.UTF_16BE);
                Log.d(AppController.LOG_TAG, "Decode type = UCS2 " + decodeMessage);
            } else if ((dcs & 0x0C) == 0x04) { // 01xx: 8-bit Data
                decodeMessage = new String(data, index, userDataLength, StandardCharsets.ISO_8859_1);
                Log.d(AppController.LOG_TAG, "Decode type = 8-bit");
            } else { // ((dcs & 0x0C) == 0x00) 7-bit (00xx и зарезервированные 11xx) трактуем как 7-bit GSM
                decodeMessage = decode7bit(data, index, udl, shift);
                Log.d(AppController.LOG_TAG, "Decode type = 7-bit GSM "+ decodeMessage);
            }
        } catch (Exception e) {
            decodeMessage = AppController.EMPTY_STRING;
            Log.w(AppController.LOG_TAG, "Ошибка декодирования PDU ID=" + pdu, e);
        }
    }

    private static int getUdhSeptetsCount(int udhLen) {
        // Переводим байты в биты и считаем, сколько септетов это занимает.
        return (udhLen * 8 + 6) / 7;
    }

    @NonNull
    private static String decode7bit(byte[] data, int index, int udl, int shift) {
        StringBuilder sb = new StringBuilder(udl);
        boolean nextIsExtension = false;
        // Начальное смещение в битах
        int currentBitPos = shift * 7;

        for (int i = shift; i < udl; i++) {
            int bytePos = index + (currentBitPos / 8);
            int bitOffset = currentBitPos % 8;

            // Безопасная проверка границ массива
            if (bytePos >= data.length) break;

            // Извлекаем текущий байт
            int currentByte = data[bytePos] & 0xFF;

            // Получаем 7-битный код
            int charCode = (currentByte >>> bitOffset);

            // Если символ разбит между двумя байтами
            if (bitOffset > 1 && bytePos + 1 < data.length) {
                charCode |= (data[bytePos + 1] & 0xFF) << (8 - bitOffset);
            }
            charCode &= 0x7F;

            if (charCode == 0x1B) { // 0x1B (Escape) в протоколе GSM управляющая команда, брать след символ в расширенной таблице
                nextIsExtension = true;
            } else {
                if (nextIsExtension) {
                    sb.append(getExtChar(charCode));
                    nextIsExtension = false;
                } else {
                    sb.append(GSM_ALPHABET.charAt(charCode));
                }
            }

            // Инкремент позиции бит на 7 для следующего шага
            currentBitPos += 7;
        }
        return sb.toString();
    }

    private static char getExtChar(int code) {
        return switch (code) {
            case 10 -> '\n'; // Формально это Page Break, но обычно перевод строки
            case 20 -> '^';
            case 40 -> '{';
            case 41 -> '}';
            case 47 -> '\\';
            case 60 -> '[';
            case 61 -> '~';
            case 62 -> ']';
            case 64 -> '|';
            case 101 -> '€';
            default -> GSM_ALPHABET.charAt(code);
            // Если символ в расширении не найден, возвращаем из основной таблицы, это стандартное поведение (fallback)
        };
    }

    private static byte[] hexStringPduToByteArray(@NonNull String pdu) {
        if (pdu.isEmpty()) return new byte[0];
        pdu = pdu.replaceAll("(?s)[^0-9A-Fa-f].*", "").trim(); // режем всё что не hex
        // если нечётная длина — усекаем последний символ
        if (pdu.length() % 2 != 0) {
            pdu = pdu.substring(0, pdu.length() - 1);
        }

        int len = pdu.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(pdu.charAt(i), 16) << 4)
                    + Character.digit(pdu.charAt(i + 1), 16));
        }
        return data;
    }

    @NonNull
    private static String getPduFromMessage(@NonNull String message) {
        if (message.isEmpty()) return AppController.EMPTY_STRING;
        int pduIndex = message.indexOf("pdu=");
        if (pduIndex < 0) {
            return AppController.EMPTY_STRING;
        }
        return message.substring(pduIndex + 4).trim();
    }

}
