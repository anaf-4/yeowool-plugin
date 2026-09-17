package com.yeowool.raid;

import java.util.BitSet;
import java.util.OptionalInt;

/** Tracks which of a fixed N instance slots are occupied. Pure in-memory bookkeeping, no I/O. */
public final class RaidInstanceAllocator {

    private final int slotCount;
    private final BitSet occupied;

    public RaidInstanceAllocator(int slotCount) {
        this.slotCount = slotCount;
        this.occupied = new BitSet(slotCount);
    }

    public synchronized OptionalInt allocate() {
        int free = occupied.nextClearBit(0);
        if (free >= slotCount) {
            return OptionalInt.empty();
        }
        occupied.set(free);
        return OptionalInt.of(free);
    }

    public synchronized void release(int slotIndex) {
        occupied.clear(slotIndex);
    }

    public synchronized boolean isFull() {
        return occupied.cardinality() >= slotCount;
    }

    public int slotCount() {
        return slotCount;
    }
}
