package org.xdty.phone.number.model.area;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.xdty.phone.number.R;
import org.xdty.phone.number.model.INumber;
import org.xdty.phone.number.model.NumberHandler;
import org.xdty.phone.number.util.NumberType;
import org.xdty.phone.number.util.Utils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 内置国际国家代码库(area_code.db)数据源。
 *
 * <p>仅对非 +86/0086 前缀的国际号码生效，识别其国家/地区名称。
 * area_code.db 表 area_code，列 cityName(国家名) / cityCode(区号，如 001/001242)。</p>
 *
 * <p>匹配采用「最长 cityCode 前缀」策略：如 +1242(巴哈马) 应优先命中最长的 001242，
 * 而非笼统的 001(美国/加拿大)。</p>
 */
public class AreaCodeHandler implements NumberHandler<AreaCodeNumber> {

    public static final String DB_NAME = "area_code.db";
    private static final String TABLE = "area_code";
    private static final String COL_NAME = "cityName";
    private static final String COL_CODE = "cityCode";

    private final Context mContext;

    // 缓存句柄（惰性加载，32KB 极小，一次性读入内存避免反复查 SQLite）
    private static List<AreaCodeEntry> sEntries;
    private static boolean sLoaded;

    public AreaCodeHandler(Context context) {
        mContext = context.getApplicationContext();
    }

    @Override
    public AreaCodeNumber find(String number) {
        if (Utils.analyzeNumber(number) != NumberType.INTL) {
            // 仅国际号码（非 +86/0086）才使用本库
            return null;
        }

        List<AreaCodeEntry> entries = loadEntries();
        if (entries == null || entries.isEmpty()) {
            return null;
        }

        String code = Utils.normalizeAreaCode(number);
        if (code == null || code.length() == 0) {
            return null;
        }

        String country = matchCountry(entries, code);
        if (country == null) {
            return null;
        }
        return new AreaCodeNumber(number, country);
    }

    @Override
    public boolean isOnline() {
        return false;
    }

    @Override
    public int getApiId() {
        return INumber.API_ID_AREA;
    }

    private List<AreaCodeEntry> loadEntries() {
        if (sLoaded) {
            return sEntries;
        }
        List<AreaCodeEntry> entries = new ArrayList<>();
        SQLiteDatabase db = null;
        Cursor cur = null;
        try {
            File dbFile = Utils.createCacheFile(mContext, DB_NAME, R.raw.area_code);
            db = SQLiteDatabase.openOrCreateDatabase(dbFile, null);
            cur = db.rawQuery("SELECT \"" + COL_NAME + "\",\"" + COL_CODE
                    + "\" FROM \"" + TABLE + "\"", null);
            while (cur != null && cur.moveToNext()) {
                String name = cur.getString(0);
                String code = cur.getString(1);
                if (name != null && code != null) {
                    entries.add(new AreaCodeEntry(name.trim(), code.trim()));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            entries = null;
        } finally {
            if (cur != null) {
                try {
                    cur.close();
                } catch (Exception ignored) {
                }
            }
            if (db != null) {
                try {
                    db.close();
                } catch (Exception ignored) {
                }
            }
        }
        sEntries = entries;
        sLoaded = true;
        return entries;
    }

    /**
     * 从 entries 中按「最长 cityCode 前缀」匹配，返回国家名；未命中返回 null。
     * 抽为纯静态方法便于 JVM 单测。
     */
    static String matchCountry(List<AreaCodeEntry> entries, String code) {
        String best = null;
        int bestLen = -1;
        for (AreaCodeEntry e : entries) {
            if (code.startsWith(e.code) && e.code.length() > bestLen) {
                best = e.name;
                bestLen = e.code.length();
            }
        }
        return best;
    }

    /** 区号与国家名条目。 */
    static class AreaCodeEntry {
        final String name;
        final String code;

        AreaCodeEntry(String name, String code) {
            this.name = name;
            this.code = code;
        }
    }
}
