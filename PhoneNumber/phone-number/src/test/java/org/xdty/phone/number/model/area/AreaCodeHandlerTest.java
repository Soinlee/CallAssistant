package org.xdty.phone.number.model.area;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class AreaCodeHandlerTest {

    private static List<AreaCodeHandler.AreaCodeEntry> sample() {
        return Arrays.asList(
                new AreaCodeHandler.AreaCodeEntry("美国/加拿大", "001"),
                new AreaCodeHandler.AreaCodeEntry("巴哈马", "001242"),
                new AreaCodeHandler.AreaCodeEntry("巴巴多斯", "001246"),
                new AreaCodeHandler.AreaCodeEntry("安圭拉", "001264")
        );
    }

    @Test
    public void longestPrefixWins() {
        // 美国号码应匹配 001
        assertEquals("美国/加拿大",
                AreaCodeHandler.matchCountry(sample(), "0012025550123"));
        // 巴哈马号码应匹配更长的 001242
        assertEquals("巴哈马",
                AreaCodeHandler.matchCountry(sample(), "0012424442222"));
        // 巴巴多斯
        assertEquals("巴巴多斯",
                AreaCodeHandler.matchCountry(sample(), "0012463330000"));
    }

    @Test
    public void noMatchReturnsNull() {
        assertNull(AreaCodeHandler.matchCountry(sample(), "0085251234567"));
    }

    @Test
    public void emptyEntryList() {
        assertNull(AreaCodeHandler.matchCountry(
                java.util.Collections.<AreaCodeHandler.AreaCodeEntry>emptyList(), "001"));
    }
}
