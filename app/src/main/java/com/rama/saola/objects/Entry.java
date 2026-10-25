package com.rama.saola.objects;

/** One password-manager record. id == 0 means "not saved yet". */
public final class Entry {
    public final long id;
    public final String name;
    public final String user;
    public final String password;
    public final String url;
    public final String totp;
    public final long updatedAt;
    public final String groupId;

    public Entry(long id, String name, String user, String password, String url, String totp, long updatedAt, String groupId) {
        this.id = id;
        this.name = name;
        this.user = user;
        this.password = password;
        this.url = url;
        this.totp = totp;
        this.updatedAt = updatedAt;
        this.groupId = groupId;
    }

    /** What to show in the list: name, else url, else user. */
    public String title() {
        if (name.length() > 0) return name;
        if (url.length() > 0) return url;
        if (user.length() > 0) return user;
        return "(no name)";
    }
}
