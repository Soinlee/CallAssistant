package org.xdty.phone.number.util;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import org.xdty.phone.number.model.INumber;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Utils {
    private static final String TAG = Utils.class.getSimpleName();

    public static synchronized File createCacheFile(Context context, String filename, int raw)
            throws IOException {
        File cacheFile = new File(context.getCacheDir(), filename);

        if (cacheFile.exists()) {
            return cacheFile;
        }

        InputStream inputStream = context.getResources().openRawResource(raw);
        FileOutputStream fileOutputStream = new FileOutputStream(cacheFile);

        int bufferSize = 1024;
        byte[] buffer = new byte[bufferSize];
        int length;
        while ((length = inputStream.read(buffer)) > 0) {
            fileOutputStream.write(buffer, 0, length);
        }

        try {
            fileOutputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            inputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return cacheFile;
    }

    public static synchronized boolean removeCacheFile(Context context, String filename) {
        File cacheFile = new File(context.getCacheDir(), filename);
        try {
            if (cacheFile.exists()) {
                return cacheFile.delete();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Unzip a zip file.  Will overwrite existing files.
     *
     * @param zipFile  Full path of the zip file you'd like to unzip.
     * @param location Full path of the directory you'd like to unzip to (will be created if it
     *                 doesn't exist).
     * @throws IOException
     */
    public static void unzip(String zipFile, String location) throws IOException {
        final int BUFFER_SIZE = 10240;
        int size;
        byte[] buffer = new byte[BUFFER_SIZE];

        try {
            if (!location.endsWith("/")) {
                location += "/";
            }
            File f = new File(location);
            if (!f.isDirectory()) {
                f.mkdirs();
            }
            ZipInputStream zin = new ZipInputStream(
                    new BufferedInputStream(new FileInputStream(zipFile), BUFFER_SIZE));
            try {
                ZipEntry ze = null;
                while ((ze = zin.getNextEntry()) != null) {
                    String path = location + ze.getName();
                    File unzipFile = new File(path);

                    if (ze.isDirectory()) {
                        if (!unzipFile.isDirectory()) {
                            unzipFile.mkdirs();
                        }
                    } else {
                        // check for and create parent directories if they don't exist
                        File parentDir = unzipFile.getParentFile();
                        if (null != parentDir) {
                            if (!parentDir.isDirectory()) {
                                parentDir.mkdirs();
                            }
                        }

                        // unzip the file
                        FileOutputStream out = new FileOutputStream(unzipFile, false);
                        BufferedOutputStream fout = new BufferedOutputStream(out, BUFFER_SIZE);
                        try {
                            while ((size = zin.read(buffer, 0, BUFFER_SIZE)) != -1) {
                                fout.write(buffer, 0, size);
                            }

                            zin.closeEntry();
                        } finally {
                            fout.flush();
                            fout.close();
                        }
                    }
                }
            } finally {
                zin.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Unzip exception", e);
        }
    }

    public static boolean checkMD5(String md5, File updateFile) {
        if (TextUtils.isEmpty(md5) || updateFile == null) {
            Log.e(TAG, "MD5 string empty or updateFile null");
            return false;
        }

        String calculatedDigest = calculateMD5(updateFile);
        if (calculatedDigest == null) {
            Log.e(TAG, "calculatedDigest null");
            return false;
        }

        Log.v(TAG, "Calculated digest: " + calculatedDigest);
        Log.v(TAG, "Provided digest: " + md5);

        return calculatedDigest.equalsIgnoreCase(md5);
    }

    public static String calculateMD5(File updateFile) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "Exception while getting digest", e);
            return null;
        }

        InputStream is;
        try {
            is = new FileInputStream(updateFile);
        } catch (FileNotFoundException e) {
            Log.e(TAG, "Exception while getting FileInputStream", e);
            return null;
        }

        byte[] buffer = new byte[8192];
        int read;
        try {
            while ((read = is.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
            byte[] md5sum = digest.digest();
            BigInteger bigInt = new BigInteger(1, md5sum);
            String output = bigInt.toString(16);
            // Fill to 32 chars
            output = String.format("%32s", output).replace(' ', '0');
            return output;
        } catch (IOException e) {
            throw new RuntimeException("Unable to process file for MD5", e);
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                Log.e(TAG, "Exception on closing MD5 input stream", e);
            }
        }
    }

    public static boolean isEmpty(CharSequence str) {
        return str == null || str.length() == 0;
    }

    public static String fixNumber(String number) {
        if (isEmpty(number)) {
            return number;
        }
        // 国际号码判定：以 + 或 00 开头，且非国内(86) / 特服(400)；长度>9 防误伤短特服号。
        // 国际号保留 + / 00 前缀，供 AreaCodeHandler 识别国家。
        boolean isIntl = number.length() > 9
                && ((number.startsWith("+") && !number.startsWith("+86") && !number.startsWith("+400"))
                    || (number.startsWith("00") && !number.startsWith("0086")));
        if (isIntl) {
            return number;
        }

        // 以下为国内 / 特服处理（原逻辑）
        String fixedNumber = number;

        if (fixedNumber.startsWith("+86")) {
            fixedNumber = fixedNumber.replace("+86", "");
        }
        if (fixedNumber.startsWith("0086")) {
            fixedNumber = fixedNumber.replaceFirst("^0086", "");
        }

        if (fixedNumber.startsWith("86") && fixedNumber.length() > 9) {
            fixedNumber = fixedNumber.replaceFirst("^86", "");
        }

        if (fixedNumber.startsWith("+400")) {
            fixedNumber = fixedNumber.replace("+", "");
        }

        if (fixedNumber.startsWith("12583")) {
            fixedNumber = fixedNumber.replaceFirst("^12583.", "");
        }

        if (fixedNumber.startsWith("1259023")) {
            fixedNumber = fixedNumber.replaceFirst("^1259023", "");
        }

        // 去除可能残留的 + 号（如 +852xxx 但已剥离 00 场景）
        if (fixedNumber.startsWith("+")) {
            fixedNumber = fixedNumber.replace("+", "");
        }

        return fixedNumber;
    }

    public static String fixNumberPlus(String number) {
        if (isEmpty(number)) {
            return number;
        }
        // 国际号码判定：以 + 或 00 开头，且非国内(86) / 特服(400)；长度>9 防误伤短特服号。
        // 国际号保留 + / 00 前缀，供 AreaCodeHandler 识别国家。
        boolean isIntl = number.length() > 9
                && ((number.startsWith("+") && !number.startsWith("+86") && !number.startsWith("+400"))
                    || (number.startsWith("00") && !number.startsWith("0086")));
        if (isIntl) {
            return number;
        }

        // 以下为国内 / 特服处理（原逻辑）
        String fixedNumber = number;

        if (fixedNumber.startsWith("+86")) {
            fixedNumber = fixedNumber.replace("+86", "");
        }
        if (fixedNumber.startsWith("0086")) {
            fixedNumber = fixedNumber.replaceFirst("^0086", "");
        }

        if (fixedNumber.startsWith("+400")) {
            fixedNumber = fixedNumber.replace("+", "");
        }

        if (fixedNumber.startsWith("+")) {
            fixedNumber = fixedNumber.replace("+", "");
        }

        if (fixedNumber.startsWith("86") && fixedNumber.length() > 9) {
            fixedNumber = fixedNumber.replaceFirst("^86", "");
        }

        if (fixedNumber.startsWith("12583")) {
            fixedNumber = fixedNumber.replaceFirst("^12583.", "");
        }

        if (fixedNumber.startsWith("1259023")) {
            fixedNumber = fixedNumber.replaceFirst("^1259023", "");
        }

        return fixedNumber;
    }

    /**
     * 号码类型分类（基于原始号码判定，不依赖归一化）。
     *
     * <p>用于数据源路由：不同号码类型走不同检索列 / 数据源。</p>
     */
    public static NumberType analyzeNumber(String number) {
        if (isEmpty(number)) {
            return NumberType.UNKNOWN;
        }
        String raw = number.trim();

        // 国际号码：+ 或 00 前缀，且非中国(86)
        if (raw.startsWith("+") && !raw.startsWith("+86")) {
            return NumberType.INTL;
        }
        if (raw.startsWith("00") && !raw.startsWith("0086")) {
            return NumberType.INTL;
        }

        // 国内语境：去掉 +86 / 0086 / 86 前缀
        String n = raw;
        if (raw.startsWith("+86")) {
            n = raw.substring(3);
        } else if (raw.startsWith("0086")) {
            n = raw.substring(4);
        } else if (raw.startsWith("86") && raw.length() > 9) {
            n = raw.substring(2);
        }

        // 特服/服务号：短号，或以 400/800 开头
        if (n.startsWith("400") || n.startsWith("800")) {
            return NumberType.CN_SPECIAL;
        }
        if (n.length() >= 3 && n.length() <= 8 && isAllDigits(n)) {
            return NumberType.CN_SPECIAL;
        }

        // 大陆手机号：11 位，1[3-9] 开头
        if (isCnMobile(n)) {
            return NumberType.CN_MOBILE;
        }

        // 大陆座机：
        //  带0区号：0 开头，010/02x/0xyz + 7~8 位号码（10~12 位）
        //  去0区号：国际来电去0，如北京 1050... (10位, 10开头) 或 2x 区号
        if (n.startsWith("0") && n.length() >= 10 && n.length() <= 12) {
            return NumberType.CN_FIXED;
        }
        if (n.length() == 10 && (n.startsWith("10") || n.startsWith("2"))) {
            return NumberType.CN_FIXED;
        }
        if (n.length() == 11 && !n.startsWith("1")
                && (n.startsWith("2") || n.startsWith("3") || n.startsWith("5"))) {
            // 4位区号去0(3位) + 8位号码，如 0531→531 形式
            return NumberType.CN_FIXED;
        }

        return NumberType.UNKNOWN;
    }

    private static boolean isCnMobile(String n) {
        return n.length() == 11 && n.charAt(0) == '1'
                && "3456789".indexOf(n.charAt(1)) >= 0
                && isAllDigits(n);
    }

    private static boolean isAllDigits(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    /**
     * 国际号码的国家码归一化：+ 前缀 → 00 前缀，便于与 area_code.db 的
     * cityCode(如 001/001242) 前缀匹配。
     *
     * <p>例：+1 → 001，+1242 → 001242，00852 → 00852（原样）。仅保留数字字符。</p>
     */
    public static String normalizeAreaCode(String number) {
        if (isEmpty(number)) {
            return null;
        }
        String s = number.trim();
        if (s.startsWith("+")) {
            s = "00" + s.substring(1);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static INumber pathGeo(List<INumber> numberList) {
        INumber iNumber = null;

        Collections.sort(numberList, new Comparator<INumber>() {
            @Override
            public int compare(INumber o1, INumber o2) {
                return o1.getApiId() - o2.getApiId();
            }
        });

        for (INumber i : numberList) {
            if (i != null && i.isValid()) {
                if (i.hasGeo()) {
                    if (iNumber == null) { // return result
                        return i;
                    } else { // patch geo info to previous result
                        iNumber.patch(i);
                        return iNumber;
                    }
                } else { // continue for geo info
                    iNumber = i;
                }
            }
        }
        return iNumber;
    }

    public static INumber mostCount(List<INumber> numberList) {
        INumber iNumber = null;

        Collections.sort(numberList, new Comparator<INumber>() {
            @Override
            public int compare(INumber o1, INumber o2) {
                return (o2 == null ? 0 : o2.getCount()) - (o1 == null ? 0 : o1.getCount());
            }
        });

        for (INumber i : numberList) {
            if (i != null && i.isValid()) {
                iNumber = i;
                break;
            }
        }
        return iNumber;
    }
}
