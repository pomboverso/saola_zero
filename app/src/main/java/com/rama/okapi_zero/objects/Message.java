package com.rama.okapi_zero.objects;

public final class Message {
    public final long id;
    public final String text;
    public final long updatedAt;
    public final int sortOrder;

    public Message(long id, String text, long updatedAt, int sortOrder) {
        this.id = id;
        this.text = text;
        this.updatedAt = updatedAt;
        this.sortOrder = sortOrder;
    }
}
