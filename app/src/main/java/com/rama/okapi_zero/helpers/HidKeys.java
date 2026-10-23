package com.rama.okapi_zero.helpers;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure-Java helpers for a standard 8-byte USB HID boot keyboard (US layout).
 * No Android dependencies, so it can be unit tested on a plain JVM.
 *
 * Report layout: [modifiers][reserved][key1][key2][key3][key4][key5][key6]
 */
public final class HidKeys {
    private HidKeys() {
    }

    public static final int REPORT_LENGTH = 8;
    public static final byte MOD_NONE = 0x00;
    public static final byte MOD_LSHIFT = 0x02;

    /** Standard boot keyboard report descriptor (63 bytes). */
    public static final int[] REPORT_DESCRIPTOR = {
            0x05, 0x01,       // Usage Page (Generic Desktop)
            0x09, 0x06,       // Usage (Keyboard)
            0xA1, 0x01,       // Collection (Application)
            0x05, 0x07,       //   Usage Page (Key Codes)
            0x19, 0xE0,       //   Usage Minimum (224)
            0x29, 0xE7,       //   Usage Maximum (231)
            0x15, 0x00,       //   Logical Minimum (0)
            0x25, 0x01,       //   Logical Maximum (1)
            0x75, 0x01,       //   Report Size (1)
            0x95, 0x08,       //   Report Count (8)
            0x81, 0x02,       //   Input (Data, Variable, Absolute) - modifier byte
            0x95, 0x01,       //   Report Count (1)
            0x75, 0x08,       //   Report Size (8)
            0x81, 0x03,       //   Input (Constant) - reserved byte
            0x95, 0x05,       //   Report Count (5)
            0x75, 0x01,       //   Report Size (1)
            0x05, 0x08,       //   Usage Page (LEDs)
            0x19, 0x01,       //   Usage Minimum (1)
            0x29, 0x05,       //   Usage Maximum (5)
            0x91, 0x02,       //   Output (Data, Variable, Absolute) - LEDs
            0x95, 0x01,       //   Report Count (1)
            0x75, 0x03,       //   Report Size (3)
            0x91, 0x03,       //   Output (Constant) - LED padding
            0x95, 0x06,       //   Report Count (6)
            0x75, 0x08,       //   Report Size (8)
            0x15, 0x00,       //   Logical Minimum (0)
            0x25, 0x65,       //   Logical Maximum (101)
            0x05, 0x07,       //   Usage Page (Key Codes)
            0x19, 0x00,       //   Usage Minimum (0)
            0x29, 0x65,       //   Usage Maximum (101)
            0x81, 0x00,       //   Input (Data, Array) - 6 key slots
            0xC0              // End Collection
    };

    private static final String UNSHIFTED_PUNCT = "-=[]\\;'`,./";
    private static final int[] UNSHIFTED_PUNCT_CODES = {0x2D, 0x2E, 0x2F, 0x30, 0x31, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38};
    private static final String SHIFTED_PUNCT = "_+{}|:\"~<>?";
    private static final int[] SHIFTED_PUNCT_CODES = {0x2D, 0x2E, 0x2F, 0x30, 0x31, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38};
    private static final String SHIFTED_DIGITS = "!@#$%^&*()";

    /** Returns {modifier, keycode} for a character, or null if unsupported. */
    public static int[] lookup(char c) {
        if (c >= 'a' && c <= 'z') return new int[]{0, 0x04 + (c - 'a')};
        if (c >= 'A' && c <= 'Z') return new int[]{MOD_LSHIFT, 0x04 + (c - 'A')};
        if (c >= '1' && c <= '9') return new int[]{0, 0x1E + (c - '1')};
        if (c == '0') return new int[]{0, 0x27};
        int i = SHIFTED_DIGITS.indexOf(c);
        if (i >= 0) return new int[]{MOD_LSHIFT, i == 9 ? 0x27 : 0x1E + i};
        i = UNSHIFTED_PUNCT.indexOf(c);
        if (i >= 0) return new int[]{0, UNSHIFTED_PUNCT_CODES[i]};
        i = SHIFTED_PUNCT.indexOf(c);
        if (i >= 0) return new int[]{MOD_LSHIFT, SHIFTED_PUNCT_CODES[i]};
        switch (c) {
            case '\n': return new int[]{0, 0x28};
            case '\t': return new int[]{0, 0x2B};
            case ' ': return new int[]{0, 0x2C};
            default: return null;
        }
    }

    /** Builds press+release reports for the text. Characters outside US ASCII are skipped. */
    public static List<byte[]> reportsFor(String text) {
        List<byte[]> reports = new ArrayList<>();
        for (int n = 0; n < text.length(); n++) {
            int[] k = lookup(text.charAt(n));
            if (k == null) continue;
            byte[] press = new byte[REPORT_LENGTH];
            press[0] = (byte) k[0];
            press[2] = (byte) k[1];
            reports.add(press);
            reports.add(new byte[REPORT_LENGTH]); // all zeros = release
        }
        return reports;
    }

    /** Number of characters in the text that have no US-layout mapping. */
    public static int countUnsupported(String text) {
        int count = 0;
        for (int n = 0; n < text.length(); n++) {
            if (lookup(text.charAt(n)) == null) count++;
        }
        return count;
    }

    /** Encodes bytes as POSIX printf octal escapes, e.g. \005\001. Safe for Android's sh/toybox. */
    public static String toOctalEscapes(byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (byte b : data) {
            int v = b & 0xFF;
            sb.append('\\').append((char) ('0' + (v >> 6))).append((char) ('0' + ((v >> 3) & 7))).append((char) ('0' + (v & 7)));
        }
        return sb.toString();
    }

    public static byte[] descriptorBytes() {
        byte[] out = new byte[REPORT_DESCRIPTOR.length];
        for (int i = 0; i < out.length; i++) out[i] = (byte) REPORT_DESCRIPTOR[i];
        return out;
    }
}
