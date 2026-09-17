package com.yeowool.raid;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** One active raid run. In-memory only — never persisted, matching Scrapyard's session model. */
public final class RaidSession {

    private final long raidId;
    private final int slotIndex;
    private final Set<UUID> partyMembers;
    private final UUID bossEntityId;
    private final long startedAtMillis;
    private RaidSessionState state = RaidSessionState.IN_PROGRESS;
    private int remainingLives;

    public RaidSession(long raidId, int slotIndex, Set<UUID> partyMembers, UUID bossEntityId, int sharedLives) {
        this.raidId = raidId;
        this.slotIndex = slotIndex;
        this.partyMembers = new HashSet<>(partyMembers);
        this.bossEntityId = bossEntityId;
        this.remainingLives = sharedLives;
        this.startedAtMillis = System.currentTimeMillis();
    }

    public long getRaidId() {
        return raidId;
    }

    public int getSlotIndex() {
        return slotIndex;
    }

    public Set<UUID> getPartyMembers() {
        return partyMembers;
    }

    public UUID getBossEntityId() {
        return bossEntityId;
    }

    public RaidSessionState getState() {
        return state;
    }

    public void setState(RaidSessionState state) {
        this.state = state;
    }

    public int getRemainingLives() {
        return remainingLives;
    }

    /** Returns the new remaining-lives count. */
    public int decrementLives() {
        remainingLives = Math.max(0, remainingLives - 1);
        return remainingLives;
    }

    public long getStartedAtMillis() {
        return startedAtMillis;
    }

    public boolean isExpired(int timeLimitSeconds) {
        return System.currentTimeMillis() - startedAtMillis >= timeLimitSeconds * 1000L;
    }
}
