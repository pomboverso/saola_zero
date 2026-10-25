package com.rama.saola.helpers;

import com.rama.saola.managers.GroupManager;
import com.rama.saola.objects.Entry;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Import/export as CSV (RFC 4180), plain text.
 *
 * Export columns: Title, Username, Password, URL, TOTP, Group.
 * Import finds columns by header name, so files from KeePassXC (Title, Username, Password, URL, TOTP),
 * Bitwarden (name, login_username, login_password, login_uri, login_totp), Chrome (name, url, username, password),
 * LastPass and 1Password style exports work. A group/folder column is matched to the group with the same name (created if missing). Columns that Okapi has no field for (notes...) are ignored.
 */
public final class DataTransfer {
    private DataTransfer() {
    }

    public static final String FILE_NAME = "saola_export.csv";
    private static final String[] HEADER = {"Title", "Username", "Password", "URL", "TOTP", "Group"};

    private static final List<String> NAME_COLUMNS = Arrays.asList("title", "name");
    private static final List<String> USER_COLUMNS = Arrays.asList("username", "login_username", "user", "login", "user name");
    private static final List<String> PASSWORD_COLUMNS = Arrays.asList("password", "login_password", "pass");
    private static final List<String> URL_COLUMNS = Arrays.asList("url", "login_uri", "website", "web site", "uri");
    private static final List<String> GROUP_COLUMNS = Arrays.asList("group", "grouping", "folder", "group name", "category");
    private static final List<String> TOTP_COLUMNS = Arrays.asList("totp", "login_totp", "otpauth", "otp", "one-time password");

    public static String toCsv(List<Entry> entries, GroupManager groups) {
        StringBuilder sb = new StringBuilder();
        appendRow(sb, HEADER);
        for (Entry e : entries) {
            appendRow(sb, new String[]{e.name, e.user, e.password, e.url, e.totp, groups.getGroupLabel(groups.resolveGroupId(e.groupId))});
        }
        return sb.toString();
    }

    private static void appendRow(StringBuilder sb, String[] fields) {
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) sb.append(',');
            sb.append('"').append(fields[i].replace("\"", "\"\"")).append('"');
        }
        sb.append("\r\n");
    }

    /** Throws IllegalArgumentException if the file has no recognizable header. */
    public static List<Entry> fromCsv(String csv, GroupManager groups) {
        List<List<String>> rows = parse(csv);
        if (rows.isEmpty()) throw new IllegalArgumentException("The file is empty");
        List<String> header = rows.get(0);
        int name = find(header, NAME_COLUMNS);
        int user = find(header, USER_COLUMNS);
        int password = find(header, PASSWORD_COLUMNS);
        int url = find(header, URL_COLUMNS);
        int totp = find(header, TOTP_COLUMNS);
        int group = find(header, GROUP_COLUMNS);
        if (name < 0 && user < 0 && password < 0 && url < 0 && totp < 0) {
            throw new IllegalArgumentException("No Title, Username, Password, URL or TOTP column found in the first row");
        }
        List<Entry> entries = new ArrayList<Entry>();
        for (int r = 1; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            String entryName = cell(row, name).trim();
            String entryUser = cell(row, user).trim();
            String entryPassword = cell(row, password);
            String entryUrl = cell(row, url).trim();
            String entryTotp = cell(row, totp).trim();
            if (entryName.length() + entryUser.length() + entryPassword.length() + entryUrl.length() + entryTotp.length() == 0) continue;
            // Only touch groups for rows that are actually kept, so empty rows do not create empty groups.
            String groupId = groups.findOrCreateByLabel(cell(row, group));
            entries.add(new Entry(0, entryName, entryUser, entryPassword, entryUrl, entryTotp, 0, groupId));
        }
        return entries;
    }

    private static int find(List<String> header, List<String> aliases) {
        for (int i = 0; i < header.size(); i++) {
            if (aliases.contains(header.get(i).trim().toLowerCase(Locale.ROOT))) return i;
        }
        return -1;
    }

    private static String cell(List<String> row, int index) {
        return index >= 0 && index < row.size() ? row.get(index) : "";
    }

    /** RFC 4180: quoted fields may contain commas, doubled quotes and line breaks. Handles CRLF, LF and a UTF-8 BOM. */
    static List<List<String>> parse(String text) {
        List<List<String>> rows = new ArrayList<List<String>>();
        if (text.length() > 0 && text.charAt(0) == '﻿') text = text.substring(1);
        List<String> row = new ArrayList<String>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean fieldStarted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
                fieldStarted = true;
            } else if (c == ',') {
                row.add(field.toString());
                field.setLength(0);
                fieldStarted = true;
            } else if (c == '\r' || c == '\n') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                if (fieldStarted || field.length() > 0 || row.size() > 0) {
                    row.add(field.toString());
                    rows.add(row);
                }
                row = new ArrayList<String>();
                field.setLength(0);
                fieldStarted = false;
            } else {
                field.append(c);
                fieldStarted = true;
            }
        }
        if (fieldStarted || field.length() > 0 || row.size() > 0) {
            row.add(field.toString());
            rows.add(row);
        }
        return rows;
    }

    public static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toString("UTF-8");
    }
}
