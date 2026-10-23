package com.rama.okapi_zero.managers;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.rama.okapi_zero.objects.Message;

import java.util.ArrayList;
import java.util.List;

public class MessagesManager extends SQLiteOpenHelper {
    private static final String TABLE = "messages";
    private static final String COLUMNS = "id, text, updated_at, sort_order";

    public MessagesManager(Context context) {
        super(context.getApplicationContext(), "okapi.db", null, 2);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE messages (id INTEGER PRIMARY KEY AUTOINCREMENT, text TEXT NOT NULL, updated_at INTEGER NOT NULL, sort_order INTEGER NOT NULL DEFAULT 0)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE messages ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 0");
            List<Long> ids = new ArrayList<Long>();
            Cursor cursor = db.rawQuery("SELECT id FROM messages ORDER BY updated_at DESC", null);
            try {
                while (cursor.moveToNext()) {
                    ids.add(cursor.getLong(0));
                }
            } finally {
                cursor.close();
            }
            for (int i = 0; i < ids.size(); i++) {
                setSortOrder(db, ids.get(i), i);
            }
        }
    }

    private Message read(Cursor cursor) {
        return new Message(cursor.getLong(0), cursor.getString(1), cursor.getLong(2), cursor.getInt(3));
    }

    public List<Message> getAll() {
        List<Message> messages = new ArrayList<Message>();
        Cursor cursor = getReadableDatabase().rawQuery("SELECT " + COLUMNS + " FROM messages ORDER BY sort_order ASC, updated_at DESC", null);
        try {
            while (cursor.moveToNext()) {
                messages.add(read(cursor));
            }
        } finally {
            cursor.close();
        }
        return messages;
    }

    private Message getById(long id) {
        Cursor cursor = getReadableDatabase().rawQuery("SELECT " + COLUMNS + " FROM messages WHERE id = ?", new String[]{String.valueOf(id)});
        try {
            return cursor.moveToFirst() ? read(cursor) : null;
        } finally {
            cursor.close();
        }
    }

    public long save(Long id, String text) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("text", text);
        values.put("updated_at", System.currentTimeMillis());
        if (id == null) {
            values.put("sort_order", nextSortOrder(db));
            return db.insert(TABLE, null, values);
        }
        db.update(TABLE, values, "id = ?", new String[]{String.valueOf(id)});
        return id;
    }

    public void delete(long id) {
        getWritableDatabase().delete(TABLE, "id = ?", new String[]{String.valueOf(id)});
    }

    public void moveUp(long id) {
        swapWithNeighbor(id, true);
    }

    public void moveDown(long id) {
        swapWithNeighbor(id, false);
    }

    private int nextSortOrder(SQLiteDatabase db) {
        Cursor cursor = db.rawQuery("SELECT MAX(sort_order) FROM messages", null);
        try {
            return cursor.moveToFirst() && !cursor.isNull(0) ? cursor.getInt(0) + 1 : 0;
        } finally {
            cursor.close();
        }
    }

    private void setSortOrder(SQLiteDatabase db, long id, int order) {
        ContentValues values = new ContentValues();
        values.put("sort_order", order);
        db.update(TABLE, values, "id = ?", new String[]{String.valueOf(id)});
    }

    private void swapWithNeighbor(long id, boolean upward) {
        SQLiteDatabase db = getWritableDatabase();
        Message current = getById(id);
        if (current == null) return;
        String sql = "SELECT id, sort_order FROM messages WHERE sort_order " + (upward ? "<" : ">") + " ? ORDER BY sort_order " + (upward ? "DESC" : "ASC") + " LIMIT 1";
        long neighborId;
        int neighborOrder;
        Cursor cursor = db.rawQuery(sql, new String[]{String.valueOf(current.sortOrder)});
        try {
            if (!cursor.moveToFirst()) return;
            neighborId = cursor.getLong(0);
            neighborOrder = cursor.getInt(1);
        } finally {
            cursor.close();
        }
        setSortOrder(db, id, neighborOrder);
        setSortOrder(db, neighborId, current.sortOrder);
    }
}
