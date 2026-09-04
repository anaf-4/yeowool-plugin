package com.yeowool.admin.coupon;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/** Parses coupon expiry dates in {@code yyyy-MM-dd} form (e.g. {@code 2026-08-23}). */
public final class CouponDateParser {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private CouponDateParser() {
    }

    /** Returns the epoch millisecond one tick past the end of the given date (so the coupon stays valid through all of that day), or -1 if unparseable. */
    public static long parseExpiryMillis(String input) {
        try {
            LocalDate date = LocalDate.parse(input.trim(), FORMAT);
            return date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (DateTimeParseException e) {
            return -1;
        }
    }
}
