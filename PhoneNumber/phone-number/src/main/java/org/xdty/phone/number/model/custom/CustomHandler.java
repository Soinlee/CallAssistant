package org.xdty.phone.number.model.custom;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.preference.PreferenceManager;
import android.text.TextUtils;

import org.xdty.phone.number.model.INumber;
import org.xdty.phone.number.model.NumberHandler;
import org.xdty.phone.number.util.NumberType;
import org.xdty.phone.number.util.Utils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 自定义 SQLite 归属地库数据源处理器。
 *
 * <p>外部导入的 db 文件位于 {@code context.getExternalFilesDir(null)/custom.db}，
 * 表名与列名映射由导入流程写入默认 SharedPreferences（见本类常量 key），
 * 因此本处理器不依赖固定的表名/列名，也不依赖 app 模块。</p>
 *
 * <p>未导入或查询异常时一律返回 null，不会阻塞其它数据源兜底。</p>
 */
public class CustomHandler implements NumberHandler<CustomNumber> {

    public static final String KEY_IMPORTED = "custom_db_imported";
    public static final String KEY_TABLE = "custom_db_table";
    public static final String KEY_COL_PHONE = "custom_db_col_phone";
    public static final String KEY_COL_PROVINCE = "custom_db_col_province";
    public static final String KEY_COL_CITY = "custom_db_col_city";
    public static final String KEY_COL_ISP = "custom_db_col_isp";
    /** 可选：座机区号列（city_code）。不存在时座机查询优雅降级为 null。 */
    public static final String KEY_COL_CITYCODE = "custom_db_col_citycode";
    public static final String DB_NAME = "custom.db";

    /** 表名/列名白名单：仅允许字母数字下划线，防止拼接注入。 */
    private static final String IDENTIFIER = "[A-Za-z0-9_]+";

    private final Context mContext;

    // 缓存句柄与元数据（懒加载，避免 40MB 库被反复打开）
    private static SQLiteDatabase sDb;
    private static boolean sLoaded;
    private static boolean sImported;
    private static String sTable;
    private static String sPhoneCol;
    private static String sProvinceCol;
    private static String sCityCol;
    private static String sIspCol;
    private static String sCityCodeCol;
    private static File sDbFile;

    public CustomHandler(Context context) {
        mContext = context.getApplicationContext();
    }

    @Override
    public CustomNumber find(String number) {
        ensureLoaded();

        if (!sImported || sDb == null) {
            return null;
        }

        number = Utils.fixNumberPlus(number);
        if (number == null || number.contains("+")) {
            return null;
        }

        NumberType type = Utils.analyzeNumber(number);
        switch (type) {
            case CN_MOBILE:
                return findByPhoneCol(number);
            case CN_FIXED:
                return findByCityCodeCol(number);
            case CN_SPECIAL:
            case INTL:
            case UNKNOWN:
            default:
                // 特服/国际/未知号不查 custom 号段库，交由其它 handler 兜底
                return null;
        }
    }

    /** 手机号：按号段(前7位)查 phone 列，输出 province/city/isp。 */
    private CustomNumber findByPhoneCol(String number) {
        if (number.length() < 7) {
            return null;
        }
        String prefix = number.substring(0, 7);
        Cursor cur = null;
        try {
            String sql = "SELECT \""
                    + sProvinceCol + "\",\"" + sCityCol + "\",\"" + sIspCol
                    + "\" FROM \"" + sTable + "\" WHERE \"" + sPhoneCol + "\" = ?";
            cur = sDb.rawQuery(sql, new String[]{prefix});
            if (cur.moveToFirst()) {
                return new CustomNumber(number,
                        cur.getString(0),
                        cur.getString(1),
                        cur.getString(2));
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cur != null) {
                try {
                    cur.close();
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }

    /**
     * 座机号：按区号查 city_code 列，输出 province/city，isp 置空。
     *
     * <p>区号匹配采用试探式 —— 用 city_code 库作“区号词典”，对候选区号逐个匹配：
     * 带0号码取 010/02x(3位)、0xyz(4位)；去0号码(国际来电)补0后取 010/02x、0xyz。</p>
     */
    private CustomNumber findByCityCodeCol(String number) {
        if (TextUtils.isEmpty(sCityCodeCol) || !isSafe(sCityCodeCol)) {
            // 老库无 city_code 列：座位查询优雅降级
            return null;
        }
        List<String> candidates = new ArrayList<>();
        if (number.startsWith("0")) {
            if (number.length() >= 3) {
                candidates.add(number.substring(0, 3));
            }
            if (number.length() >= 4) {
                candidates.add(number.substring(0, 4));
            }
        } else {
            if (number.length() >= 3) {
                candidates.add("0" + number.substring(0, 2));
            }
            if (number.length() >= 4) {
                candidates.add("0" + number.substring(0, 3));
            }
        }

        Cursor cur = null;
        try {
            for (String code : candidates) {
                // 号码体长度需 7~8 位才视为合法座机
                int codeLen = number.startsWith("0") ? code.length() : code.length() - 1;
                if (number.length() <= codeLen) {
                    continue;
                }
                String body = number.substring(codeLen);
                if (body.length() < 7 || body.length() > 8) {
                    continue;
                }
                String sql = "SELECT \""
                        + sProvinceCol + "\",\"" + sCityCol
                        + "\" FROM \"" + sTable + "\" WHERE \"" + sCityCodeCol + "\" = ?";
                cur = sDb.rawQuery(sql, new String[]{code});
                if (cur.moveToFirst()) {
                    return new CustomNumber(number,
                            cur.getString(0),
                            cur.getString(1),
                            null); // 座机不输出运营商
                }
                if (cur != null) {
                    cur.close();
                    cur = null;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cur != null) {
                try {
                    cur.close();
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }

    @Override
    public boolean isOnline() {
        return false;
    }

    @Override
    public int getApiId() {
        return INumber.API_ID_CUSTOM;
    }

    private void ensureLoaded() {
        if (sLoaded) {
            return;
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        if (prefs == null) {
            return;
        }
        sImported = prefs.getBoolean(KEY_IMPORTED, false);
        if (sImported) {
            sTable = prefs.getString(KEY_TABLE, null);
            sPhoneCol = prefs.getString(KEY_COL_PHONE, null);
            sProvinceCol = prefs.getString(KEY_COL_PROVINCE, null);
            sCityCol = prefs.getString(KEY_COL_CITY, null);
            sIspCol = prefs.getString(KEY_COL_ISP, null);
            sCityCodeCol = prefs.getString(KEY_COL_CITYCODE, null);
        }

        File dir = mContext.getExternalFilesDir(null);
        sDbFile = (dir != null) ? new File(dir, DB_NAME) : null;

        // 校验元数据完整性 + 白名单，且文件确实存在，才尝试打开
        sDb = null;
        if (sImported && sDbFile != null && sDbFile.exists()
                && isSafe(sTable) && isSafe(sPhoneCol)
                && isSafe(sProvinceCol) && isSafe(sCityCol) && isSafe(sIspCol)) {
            try {
                sDb = SQLiteDatabase.openDatabase(
                        sDbFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
            } catch (Exception e) {
                e.printStackTrace();
                sDb = null;
            }
        }
        sLoaded = true;
    }

    private static boolean isSafe(String s) {
        return !TextUtils.isEmpty(s) && s.matches(IDENTIFIER);
    }

    /** 供导入流程在重新导入或清除后重置句柄缓存。 */
    public static void invalidate() {
        if (sDb != null) {
            try {
                sDb.close();
            } catch (Exception ignored) {
            }
            sDb = null;
        }
        sLoaded = false;
    }
}
