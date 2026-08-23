package com.safelogj.mikread.sms;

import com.safelogj.mikread.AppController;

import java.util.ArrayList;
import java.util.List;

public class MotherSms {
    private final List<Sms> parts = new ArrayList<>();
    private final long groupTimestamp;
    private String stringTimestamp;
    private final String source;
    private final String phone;
    private String finalText = AppController.EMPTY_STRING;
    private boolean isDeleting;

    public MotherSms(long groupTimestamp, String source, String phone) {
        this.groupTimestamp = groupTimestamp;
        this.source = source;
        this.phone = phone;
    }

    public boolean isDeleting() {
        return isDeleting;
    }

    public void setDeleting(boolean deleting) {
        isDeleting = deleting;
    }

    public void addPart(Sms sms) {
        parts.add(sms);
    }

    public List<Sms> getParts() {
        return parts;
    }

    public long getGroupTimestamp() {
        return groupTimestamp;
    }

    public String getSource() {
        return source;
    }

    public String getPhone() {
        return phone;
    }

    public void setFinalText(String finalText) {
        this.finalText = finalText;
    }

    public String getFinalText() {
        return finalText;
    }

    public String getStringTimestamp() {
        return stringTimestamp;
    }

    public void setStringTimestamp(String stringTimestamp) {
        this.stringTimestamp = stringTimestamp;
    }

    public boolean isPduContains(String pdu) {
        for (Sms sms : parts) {
            if (sms.getPdu().equals(pdu)) {
                return true;
            }
        }
        return false;
    }
}
