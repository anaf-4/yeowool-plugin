package com.yeowool.raid;

import java.util.Optional;

/** Checked in this exact order so the denial message matches the most relevant reason first. */
public final class RaidEntryValidator {

    private RaidEntryValidator() {
    }

    public static Optional<RaidEntryDenialReason> validate(
            int partySize, int minPartySize, int maxPartySize,
            boolean partyAlreadyInRaid, boolean hasEnoughTickets, boolean instanceAvailable) {
        if (partySize < minPartySize) {
            return Optional.of(RaidEntryDenialReason.PARTY_TOO_SMALL);
        }
        if (partySize > maxPartySize) {
            return Optional.of(RaidEntryDenialReason.PARTY_TOO_LARGE);
        }
        if (partyAlreadyInRaid) {
            return Optional.of(RaidEntryDenialReason.PARTY_ALREADY_IN_RAID);
        }
        if (!hasEnoughTickets) {
            return Optional.of(RaidEntryDenialReason.NOT_ENOUGH_TICKETS);
        }
        if (!instanceAvailable) {
            return Optional.of(RaidEntryDenialReason.NO_FREE_INSTANCE);
        }
        return Optional.empty();
    }
}
