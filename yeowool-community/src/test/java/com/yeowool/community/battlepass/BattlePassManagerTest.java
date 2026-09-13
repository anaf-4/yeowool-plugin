package com.yeowool.community.battlepass;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers {@link BattlePassManager}'s CSV encoding of the claimed-tier set —
 * the only pure logic on this class, everything else touches {@code
 * PlayerData}/mailbox/economy. A round-trip bug here would silently forget
 * or duplicate claimed rewards on reload.
 */
class BattlePassManagerTest {

    @Test
    void splitCsvParsesEachIntegerInOrder() {
        assertEquals(new LinkedHashSet<>(Set.of(1, 2, 3)), BattlePassManager.splitCsv("1,2,3"));
        assertEquals(Set.of(5), BattlePassManager.splitCsv("5"));
    }

    @Test
    void splitCsvOfBlankOrNullIsEmpty() {
        assertTrue(BattlePassManager.splitCsv("").isEmpty());
        assertTrue(BattlePassManager.splitCsv("   ").isEmpty());
        assertTrue(BattlePassManager.splitCsv(null).isEmpty());
    }

    @Test
    void splitCsvSkipsCorruptEntriesInsteadOfFailing() {
        assertEquals(Set.of(1, 3), BattlePassManager.splitCsv("1,oops,3"));
    }

    @Test
    void splitCsvTrimsWhitespaceAroundEachValue() {
        assertEquals(new LinkedHashSet<>(Set.of(1, 2, 3)), BattlePassManager.splitCsv(" 1 , 2 ,3 "));
    }

    @Test
    void joinCsvRoundTripsThroughSplitCsv() {
        Set<Integer> original = new LinkedHashSet<>(Set.of(4, 1, 7));
        String csv = BattlePassManager.joinCsv(original);
        assertEquals(original, BattlePassManager.splitCsv(csv));
    }

    @Test
    void joinCsvOfEmptySetIsEmptyString() {
        assertEquals("", BattlePassManager.joinCsv(Set.of()));
    }
}
