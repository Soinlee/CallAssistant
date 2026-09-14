package org.xdty.phone.number.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class UtilsTest {

    // ---------- analyzeNumber ----------

    @Test
    public void analyzeCnMobile() {
        assertEquals(NumberType.CN_MOBILE, Utils.analyzeNumber("17701234456"));
        assertEquals(NumberType.CN_MOBILE, Utils.analyzeNumber("+8617701234456"));
        assertEquals(NumberType.CN_MOBILE, Utils.analyzeNumber("008617701234456"));
        assertEquals(NumberType.CN_MOBILE, Utils.analyzeNumber("8617701234456"));
    }

    @Test
    public void analyzeCnFixed() {
        // 北京座机（去0区号 10）
        assertEquals(NumberType.CN_FIXED, Utils.analyzeNumber("+861050836600"));
        assertEquals(NumberType.CN_FIXED, Utils.analyzeNumber("00861050836600"));
        // 带0区号
        assertEquals(NumberType.CN_FIXED, Utils.analyzeNumber("01050836600"));
        // 4位区号(济南 0531) + 8位
        assertEquals(NumberType.CN_FIXED, Utils.analyzeNumber("053156789012"));
    }

    @Test
    public void analyzeIntl() {
        // 美国 +1
        assertEquals(NumberType.INTL, Utils.analyzeNumber("+12025550123"));
        // 香港 00852
        assertEquals(NumberType.INTL, Utils.analyzeNumber("0085251234567"));
        assertEquals(NumberType.INTL, Utils.analyzeNumber("+85251234567"));
    }

    @Test
    public void analyzeCnSpecial() {
        assertEquals(NumberType.CN_SPECIAL, Utils.analyzeNumber("10086"));
        assertEquals(NumberType.CN_SPECIAL, Utils.analyzeNumber("110"));
        assertEquals(NumberType.CN_SPECIAL, Utils.analyzeNumber("12345"));
        assertEquals(NumberType.CN_SPECIAL, Utils.analyzeNumber("400123456"));
        assertEquals(NumberType.CN_SPECIAL, Utils.analyzeNumber("800123456"));
    }

    // ---------- fixNumber ----------

    @Test
    public void fixNumber00() {
        assertEquals("17701234456", Utils.fixNumber("008617701234456"));
        assertEquals("1050836600", Utils.fixNumber("00861050836600"));
        assertEquals("1050836600", Utils.fixNumber("+861050836600"));
        // 非 86 国家：国际号（+/00 开头且非 +86/0086）保留前缀，供 AreaCodeHandler 识别
        assertEquals("0085251234567", Utils.fixNumber("0085251234567"));
        assertEquals("+903122132965", Utils.fixNumber("+903122132965"));
        assertEquals("00903122132965", Utils.fixNumber("00903122132965"));
    }

    @Test
    public void fixNumberLegacy() {
        assertEquals("400123456", Utils.fixNumber("+400123456"));
        assertEquals("12345678", Utils.fixNumber("+8612345678"));
        assertEquals("12345678", Utils.fixNumber("8612345678"));
    }

    // ---------- fixNumberPlus ----------

    @Test
    public void fixNumberPlus00() {
        assertEquals("17701234456", Utils.fixNumberPlus("008617701234456"));
        assertEquals("1050836600", Utils.fixNumberPlus("00861050836600"));
        // 非 86 国家：国际号（+/00 开头且非 +86/0086）保留前缀，供 AreaCodeHandler 识别
        assertEquals("0085251234567", Utils.fixNumberPlus("0085251234567"));
        assertEquals("+903122132965", Utils.fixNumberPlus("+903122132965"));
        assertEquals("00903122132965", Utils.fixNumberPlus("00903122132965"));
    }

    @Test
    public void fixNumberPlusLegacy() {
        assertEquals("12345678", Utils.fixNumberPlus("+8612345678"));
        assertEquals("12345678", Utils.fixNumberPlus("8612583112345678"));
    }

    // ---------- normalizeAreaCode ----------

    @Test
    public void normalizeAreaCodeTest() {
        assertEquals("001", Utils.normalizeAreaCode("+1"));
        assertEquals("001242", Utils.normalizeAreaCode("+1242"));
        assertEquals("00852", Utils.normalizeAreaCode("00852"));
        // 已是 00 形式的完整号码：仅保留数字，不截断
        assertEquals("00112025550123", Utils.normalizeAreaCode("00112025550123"));
        // + 前缀 → 00 前缀，保留后续数字
        assertEquals("0012025550123", Utils.normalizeAreaCode("+12025550123"));
    }
}
