package com.guga.ledger;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LedgerDb extends SQLiteOpenHelper {

    private static final String DB_NAME = "ledger.db";
    private static final int DB_VERSION = 1;
    private static final String TABLE = "entries";

    public LedgerDb(Context ctx) {
        super(ctx, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "type INTEGER NOT NULL," +
                "amount_cents INTEGER NOT NULL," +
                "major TEXT NOT NULL," +
                "minor TEXT NOT NULL," +
                "note TEXT," +
                "date_millis INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX idx_entries_date ON " + TABLE + "(date_millis)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // v1 暂无升级逻辑
    }

    public long insert(Entry e) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put("type", e.type);
        v.put("amount_cents", e.amountCents);
        v.put("major", e.major);
        v.put("minor", e.minor);
        v.put("note", e.note);
        v.put("date_millis", e.dateMillis);
        return db.insert(TABLE, null, v);
    }

    public void update(Entry e) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put("type", e.type);
        v.put("amount_cents", e.amountCents);
        v.put("major", e.major);
        v.put("minor", e.minor);
        v.put("note", e.note);
        v.put("date_millis", e.dateMillis);
        db.update(TABLE, v, "id=?", new String[]{String.valueOf(e.id)});
    }

    public void delete(long id) {
        getWritableDatabase().delete(TABLE, "id=?", new String[]{String.valueOf(id)});
    }

    /** 某时间段内的流水，按日期倒序、id 倒序 */
    public List<Entry> list(long fromMillis, long toMillis) {
        List<Entry> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query(TABLE, null,
                "date_millis>=? AND date_millis<?",
                new String[]{String.valueOf(fromMillis), String.valueOf(toMillis)},
                null, null, "date_millis DESC, id DESC");
        try {
            while (c.moveToNext()) out.add(fromCursor(c));
        } finally {
            c.close();
        }
        return out;
    }

    /** 某时间段内 type 的总金额（分） */
    public long sum(long fromMillis, long toMillis, int type) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT COALESCE(SUM(amount_cents),0) FROM " + TABLE +
                        " WHERE date_millis>=? AND date_millis<? AND type=?",
                new String[]{String.valueOf(fromMillis), String.valueOf(toMillis), String.valueOf(type)});
        try {
            return c.moveToFirst() ? c.getLong(0) : 0;
        } finally {
            c.close();
        }
    }

    /** 某时间段内按大类汇总（分），金额从大到小 */
    public Map<String, Long> sumByMajor(long fromMillis, long toMillis, int type) {
        Map<String, Long> out = new LinkedHashMap<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT major, SUM(amount_cents) s FROM " + TABLE +
                        " WHERE date_millis>=? AND date_millis<? AND type=? GROUP BY major ORDER BY s DESC",
                new String[]{String.valueOf(fromMillis), String.valueOf(toMillis), String.valueOf(type)});
        try {
            while (c.moveToNext()) out.put(c.getString(0), c.getLong(1));
        } finally {
            c.close();
        }
        return out;
    }

    /** 某大类下按小类汇总（分），金额从大到小 */
    public Map<String, Long> sumByMinor(long fromMillis, long toMillis, int type, String major) {
        Map<String, Long> out = new LinkedHashMap<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT minor, SUM(amount_cents) s FROM " + TABLE +
                        " WHERE date_millis>=? AND date_millis<? AND type=? AND major=? GROUP BY minor ORDER BY s DESC",
                new String[]{String.valueOf(fromMillis), String.valueOf(toMillis), String.valueOf(type), major});
        try {
            while (c.moveToNext()) out.put(c.getString(0), c.getLong(1));
        } finally {
            c.close();
        }
        return out;
    }

    private Entry fromCursor(Cursor c) {
        Entry e = new Entry();
        e.id = c.getLong(c.getColumnIndexOrThrow("id"));
        e.type = c.getInt(c.getColumnIndexOrThrow("type"));
        e.amountCents = c.getLong(c.getColumnIndexOrThrow("amount_cents"));
        e.major = c.getString(c.getColumnIndexOrThrow("major"));
        e.minor = c.getString(c.getColumnIndexOrThrow("minor"));
        e.note = c.getString(c.getColumnIndexOrThrow("note"));
        e.dateMillis = c.getLong(c.getColumnIndexOrThrow("date_millis"));
        return e;
    }

    // ---------- 时间工具 ----------
    public static long monthStart(int year, int month1to12) {
        Calendar cal = Calendar.getInstance();
        cal.set(year, month1to12 - 1, 1, 0, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    public static long nextMonthStart(int year, int month1to12) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(monthStart(year, month1to12));
        cal.add(Calendar.MONTH, 1);
        return cal.getTimeInMillis();
    }

    public static long dayStart(long millis) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(millis);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }
}
