package com.rama.saola.managers;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.rama.saola.objects.Entry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class EntriesManager extends SQLiteOpenHelper {
    private static final String TABLE = "entries";
    private static final String COLUMNS = "id, name, user, password, url, totp, updated_at, group_id";

    public EntriesManager(Context context) {
        super(context.getApplicationContext(), "saola.db", null, 4);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createEntriesTable(db);
    }

    private void createEntriesTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE entries (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL DEFAULT '', user TEXT NOT NULL DEFAULT '', password TEXT NOT NULL DEFAULT '', url TEXT NOT NULL DEFAULT '', totp TEXT NOT NULL DEFAULT '', updated_at INTEGER NOT NULL, sort_order INTEGER NOT NULL DEFAULT 0, group_id TEXT NOT NULL DEFAULT '" + PrefsManager.DEFAULT_GROUP_ID + "')");
        // sort_order is legacy: manual sorting was removed, the column is kept only because SQLite cannot drop it on old Android.
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
        if (oldVersion >= 3 && oldVersion < 4) {
            // Entries created above for oldVersion < 3 already have the column.
            db.execSQL("ALTER TABLE entries ADD COLUMN group_id TEXT NOT NULL DEFAULT '" + PrefsManager.DEFAULT_GROUP_ID + "'");
        }
    }

    private Entry read(Cursor c) {
        return new Entry(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getString(5), c.getLong(6), c.getString(7));
    }

    public List<Entry> getAll() {
        List<Entry> entries = new ArrayList<Entry>();
        Cursor cursor = getReadableDatabase().rawQuery("SELECT " + COLUMNS + " FROM entries ORDER BY id ASC", null);
        try {
            while (cursor.moveToNext()) {
                entries.add(read(cursor));
            }
        } finally {
            cursor.close();
        }
        return entries;
    }

    private ContentValues valuesOf(Entry e) {
        ContentValues values = new ContentValues();
        values.put("name", e.name);
        values.put("user", e.user);
        values.put("password", e.password);
        values.put("url", e.url);
        values.put("totp", e.totp);
        values.put("updated_at", System.currentTimeMillis());
        values.put("group_id", e.groupId == null || e.groupId.length() == 0 ? PrefsManager.DEFAULT_GROUP_ID : e.groupId);
        return values;
    }

    /** Inserts (id == 0) or updates. Returns the row id. */
    public long save(Entry e) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = valuesOf(e);
        if (e.id == 0) {
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

    /** Moves the given entries to a group without touching their modification date. */
    public void moveToGroup(Collection<Long> ids, String groupId) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues values = new ContentValues();
            values.put("group_id", groupId);
            for (Long id : ids) {
                db.update(TABLE, values, "id = ?", new String[]{String.valueOf(id)});
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /** Moves every entry of one group into another (used when a group is deleted). */
    public void reassignGroup(String fromGroupId, String toGroupId) {
        ContentValues values = new ContentValues();
        values.put("group_id", toGroupId);
        getWritableDatabase().update(TABLE, values, "group_id = ?", new String[]{fromGroupId});
    }
}
