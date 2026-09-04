package com.yeowool.core.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TabCompletionsTest {

    @Test
    void filtersByCaseInsensitivePrefix() {
        var result = TabCompletions.filter(List.of("정보", "지급", "공지"), "정");
        assertEquals(List.of("정보"), result);
    }

    @Test
    void mixedCaseAsciiPrefixIsCaseInsensitive() {
        var result = TabCompletions.filter(List.of("Warp", "warn", "West"), "WA");
        assertEquals(List.of("Warp", "warn"), result);
    }

    @Test
    void emptyPartialMatchesEverything() {
        var candidates = List.of("a", "b", "c");
        assertEquals(candidates, TabCompletions.filter(candidates, ""));
    }

    @Test
    void noMatchesReturnsEmptyList() {
        assertTrue(TabCompletions.filter(List.of("a", "b"), "z").isEmpty());
    }
}
