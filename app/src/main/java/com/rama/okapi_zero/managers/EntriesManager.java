package com.rama.okapi_zero.managers;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.rama.okapi_zero.objects.Entry;

import java.util.ArrayList;
import java.util.List;

public class EntriesManager extends SQLiteOpenHelper {
    private static final String TABLE = "entries";
    private static final String COLUMNS = "id, name, user, password, url, totp, updated_at, sort_order";

    public EntriesManager(Context context) {
        super(context.getApplicationContext(), "okapi.db", null, 3);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createEntriesTable(db);
    }

    private void createEntriesTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE entries (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL DEFAULT '', user TEXT NOT NULL DEFAULT '', password TEXT NOT NULL DEFAULT '', url TEXT NOT NULL DEFAULT '', totp TEXT NOT NULL DEFAULT '', updated_at INTEGER NOT NULL, sort_order INTEGER NOT NULL DEFAULT 0)");
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
                ContentValues values = new ContentValues();
                values.put("sort_order", i);
                db.update("messages", values, "id = ?", new String[]{String.valueOf(ids.get(i))});
            }
        }
        if (oldVersion < 3) {
            // Old single-text messages become entries, keeping their text as the entry name.
            createEntriesTable(db);
            db.execSQL("INSERT INTO entries (name, updated_at, sort_order) SELECT text, updated_at, sort_order FROM messages");
            db.execSQL("DROP TABLE messages");
        }
    }

    private Entry read(Cursor c) {
        return new Entry(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getString(5), c.getLong(6), c.getInt(7));
    }

    public List<Entry> getAll() {
        List<Entry> entries = new ArrayList<Entry>();
        Cursor cursor = getReadableDatabase().rawQuery("SELECT " + COLUMNS + " FROM entries ORDER BY sort_order ASC, updated_at DESC", null);
        try {
            while (cursor.moveToNext()) {
                entries.add(read(cursor));
            }
        } finally {
            cursor.close();
        }
        return entries;
    }

    private Entry getById(long id) {
        Cursor cursor = getReadableDatabase().rawQuery("SELECT " + COLUMNS + " FROM entries WHERE id = ?", new String[]{String.valueOf(id)});
        try {
            return cursor.moveToFirst() ? read(cursor) : null;
        } finally {
            cursor.close();
        }
    }

    private ContentValues valuesOf(Entry e) {
        ContentValues values = new ContentValues();
        values.put("name", e.name);
        values.put("user", e.user);
        values.put("password", e.password);
        values.put("url", e.url);
        values.put("totp", e.totp);
        values.put("updated_at", System.currentTimeMillis());
        return values;
    }

    /** Inserts (id == 0) or updates. Returns the row id. */
    public long save(Entry e) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = valuesOf(e);
        if (e.id == 0) {
            values.put("sort_order", nextSortOrder(db));
            return db.insert(TABLE, null, values);
        }
        db.update(TABLE, values, "id = ?", new String[]{String.valueOf(e.id)});
        return e.id;
    }

    /** Adds entries that are not already stored (same name, user, password, url and totp). Returns how many were added. */
    public int importEntries(List<Entry> entries) {
        SQLiteDatabase db = getWritableDatabase();
        int added = 0;
        db.beginTransaction();
        try {
            for (Entry e : entries) {
                Cursor c = db.rawQuery("SELECT 1 FROM entries WHERE name = ? AND user = ? AND password = ? AND url = ? AND totp = ? LIMIT 1",
                        new String[]{e.name, e.user, e.password, e.url, e.totp});
                boolean exists;
                try {
                    exists = c.moveToFirst();
                } finally {
                    c.close();
                }
                if (exists) continue;
                ContentValues values = valuesOf(e);
                if (e.updatedAt > 0) values.put("updated_at", e.updatedAt);
                values.put("sort_order", nextSortOrder(db));
                db.insert(TABLE, null, values);
                added++;
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return added;
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
        Cursor cursor = db.rawQuery("SELECT MAX(sort_order) FROM entries", null);
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
        Entry current = getById(id);
        if (current == null) return;
        String sql = "SELECT id, sort_order FROM entries WHERE sort_order " + (upward ? "<" : ">") + " ? ORDER BY sort_order " + (upward ? "DESC" : "ASC") + " LIMIT 1";
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
