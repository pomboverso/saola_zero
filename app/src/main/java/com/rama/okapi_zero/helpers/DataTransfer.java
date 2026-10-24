package com.rama.okapi_zero.helpers;

import com.rama.okapi_zero.objects.Entry;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** Import/export format: one JSON document, plain text. */
public final class DataTransfer {
    private DataTransfer() {
    }

    public static final String FILE_NAME = "okapi_export.json";
    private static final int VERSION = 1;

    public static String toJson(List<Entry> entries) throws JSONException {
        JSONArray array = new JSONArray();
        for (Entry e : entries) {
            JSONObject o = new JSONObject();
            o.put("name", e.name);
            o.put("user", e.user);
            o.put("password", e.password);
            o.put("url", e.url);
            o.put("totp", e.totp);
            o.put("updatedAt", e.updatedAt);
            array.put(o);
        }
        JSONObject root = new JSONObject();
        root.put("app", "okapi");
        root.put("version", VERSION);
        root.put("entries", array);
        return root.toString(2);
    }

    public static List<Entry> fromJson(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        JSONArray array = root.getJSONArray("entries");
        List<Entry> entries = new ArrayList<Entry>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject o = array.getJSONObject(i);
            Entry e = new Entry(0, o.optString("name", ""), o.optString("user", ""), o.optString("password", ""),
                    o.optString("url", ""), o.optString("totp", ""), o.optLong("updatedAt", 0), 0);
            if (e.name.length() + e.user.length() + e.password.length() + e.url.length() + e.totp.length() == 0) continue;
            entries.add(e);
        }
        return entries;
    }

    public static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toString("UTF-8");
    }
}
