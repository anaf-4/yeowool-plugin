package com.yeowool.admin.coupon;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CouponDateParserTest {

    @Test
    void validDateExpiresAtStartOfNextDay() {
        long expiresAt = CouponDateParser.parseExpiryMillis("2026-08-23");
        long expected = LocalDate.of(2026, 8, 23).plusDays(1)
                .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        assertEquals(expected, expiresAt);
    }

    @Test
    void trimsWhitespace() {
        assertEquals(CouponDateParser.parseExpiryMillis("2026-08-23"), CouponDateParser.parseExpiryMillis("  2026-08-23  "));
    }

    @Test
    void rejectsMalformedInput() {
        assertTrue(CouponDateParser.parseExpiryMillis("not-a-date") < 0);
        assertTrue(CouponDateParser.parseExpiryMillis("2026/08/23") < 0);
        assertTrue(CouponDateParser.parseExpiryMillis("2026-13-01") < 0);
        assertTrue(CouponDateParser.parseExpiryMillis("") < 0);
    }
}
