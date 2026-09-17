package com.github.soinlee.callassistant;

import org.junit.Test;
import com.github.soinlee.callassistant.data.CallerRepository;

import static org.junit.Assert.assertEquals;

public class CallerRepositoryTest {

    @Test
    public void testFixNumber() {
        assertEquals(CallerRepository.fixNumber("+400123456"), "400123456");
        assertEquals(CallerRepository.fixNumber("+8612345678"), "12345678");
        assertEquals(CallerRepository.fixNumber("8612345678"), "12345678");
        assertEquals(CallerRepository.fixNumber("8612583112345678"), "12345678");
        assertEquals(CallerRepository.fixNumber("8612583212345678"), "12345678");
        assertEquals(CallerRepository.fixNumber("8612583312345678"), "12345678");
        assertEquals(CallerRepository.fixNumber("12583312345678"), "12345678");
        assertEquals(CallerRepository.fixNumber("125902312345678"), "12345678");
        assertEquals(CallerRepository.fixNumber("118334812345678"), "12345678");

        // 00 国际冠码：00 剥离后应正确解析（国内 +86/0086）
        assertEquals(CallerRepository.fixNumber("008617701234456"), "17701234456");
        assertEquals(CallerRepository.fixNumber("00861050836600"), "1050836600");
        assertEquals(CallerRepository.fixNumber("+861050836600"), "1050836600");
        // 非 86 国家：国际号（+/00 且非 +86/0086）保留前缀，供 AreaCodeHandler 识别
        assertEquals(CallerRepository.fixNumber("0085251234567"), "0085251234567");
        assertEquals(CallerRepository.fixNumber("+903122132965"), "+903122132965");
        assertEquals(CallerRepository.fixNumber("00903122132965"), "00903122132965");
    }

}
