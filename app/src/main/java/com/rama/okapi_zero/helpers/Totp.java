package com.rama.okapi_zero.helpers;

import java.io.ByteArrayOutputStream;
import java.net.URLDecoder;
import java.nio.ByteBuffer;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * RFC 6238 TOTP. Accepts either a bare base32 secret or an otpauth:// URI
 * (reads secret, digits, period and algorithm). No Android dependencies.
 */
public final class Totp {
    private Totp() {
    }

    /** Current code, or null if the secret is invalid. */
    public static String now(String secretOrUri) {
        return at(secretOrUri, System.currentTimeMillis() / 1000L);
    }

    public static String at(String secretOrUri, long unixSeconds) {
        String secret = secretOrUri == null ? "" : secretOrUri.trim();
        int digits = 6;
        long period = 30;
        String algo = "HmacSHA1";
        try {
            if (secret.regionMatches(true, 0, "otpauth://", 0, 10)) {
                int q = secret.indexOf('?');
                if (q < 0) return null;
                String found = null;
                for (String pair : secret.substring(q + 1).split("&")) {
                    int eq = pair.indexOf('=');
                    if (eq < 0) continue;
                    String key = pair.substring(0, eq).toLowerCase();
                    String value = URLDecoder.decode(pair.substring(eq + 1), "UTF-8");
                    if (key.equals("secret")) found = value;
                    else if (key.equals("digits")) digits = Integer.parseInt(value);
                    else if (key.equals("period")) period = Long.parseLong(value);
                    else if (key.equals("algorithm")) {
                        String a = value.toUpperCase();
                        if (a.equals("SHA256")) algo = "HmacSHA256";
                        else if (a.equals("SHA512")) algo = "HmacSHA512";
                        else if (!a.equals("SHA1")) return null;
                    }
                }
                if (found == null) return null;
                secret = found;
            }
            if (digits < 6 || digits > 9 || period <= 0) return null;
            byte[] key = base32Decode(secret);
            if (key == null || key.length == 0) return null;

            byte[] counter = ByteBuffer.allocate(8).putLong(unixSeconds / period).array();
            Mac mac = Mac.getInstance(algo);
            mac.init(new SecretKeySpec(key, algo));
            byte[] hash = mac.doFinal(counter);
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24) | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8) | (hash[offset + 3] & 0xFF);
            long mod = 1;
            for (int i = 0; i < digits; i++) mod *= 10;
            String code = String.valueOf(binary % mod);
            while (code.length() < digits) code = "0" + code;
            return code;
        } catch (Exception e) {
            return null;
        }
    }

    /** RFC 4648 base32, case-insensitive, ignoring spaces, dashes and '=' padding. Null if invalid. */
    static byte[] base32Decode(String s) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int buffer = 0;
        int bits = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = Character.toUpperCase(s.charAt(i));
            if (c == ' ' || c == '-' || c == '=') continue;
            int v;
            if (c >= 'A' && c <= 'Z') v = c - 'A';
            else if (c >= '2' && c <= '7') v = 26 + (c - '2');
            else return null;
            buffer = (buffer << 5) | v;
            bits += 5;
            if (bits >= 8) {
                out.write((buffer >> (bits - 8)) & 0xFF);
                bits -= 8;
            }
        }
        return out.toByteArray();
    }
}
