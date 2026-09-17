package com.yeowool.raid;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RaidEntryValidatorTest {

    @Test
    void passesWhenEverythingIsFine() {
        assertEquals(Optional.empty(), RaidEntryValidator.validate(3, 1, 6, false, true, true));
    }

    @Test
    void rejectsPartyBelowMinimum() {
        assertEquals(Optional.of(RaidEntryDenialReason.PARTY_TOO_SMALL),
                RaidEntryValidator.validate(0, 1, 6, false, true, true));
    }

    @Test
    void rejectsPartyAboveMaximum() {
        assertEquals(Optional.of(RaidEntryDenialReason.PARTY_TOO_LARGE),
                RaidEntryValidator.validate(7, 1, 6, false, true, true));
    }

    @Test
    void rejectsWhenPartyAlreadyInARaid() {
        assertEquals(Optional.of(RaidEntryDenialReason.PARTY_ALREADY_IN_RAID),
                RaidEntryValidator.validate(3, 1, 6, true, true, true));
    }

    @Test
    void rejectsWhenNotEnoughTickets() {
        assertEquals(Optional.of(RaidEntryDenialReason.NOT_ENOUGH_TICKETS),
                RaidEntryValidator.validate(3, 1, 6, false, false, true));
    }

    @Test
    void rejectsWhenNoInstanceIsFree() {
        assertEquals(Optional.of(RaidEntryDenialReason.NO_FREE_INSTANCE),
                RaidEntryValidator.validate(3, 1, 6, false, true, false));
    }
}
