package com.yeowool.raid;

import org.junit.jupiter.api.Test;

import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.*;

class RaidInstanceAllocatorTest {

    @Test
    void allocatesSlotsInOrderAndTracksFullness() {
        RaidInstanceAllocator allocator = new RaidInstanceAllocator(2);
        assertFalse(allocator.isFull());

        OptionalInt first = allocator.allocate();
        assertEquals(OptionalInt.of(0), first);

        OptionalInt second = allocator.allocate();
        assertEquals(OptionalInt.of(1), second);
        assertTrue(allocator.isFull());

        assertEquals(OptionalInt.empty(), allocator.allocate());
    }

    @Test
    void releasingFreesTheSlotForReuse() {
        RaidInstanceAllocator allocator = new RaidInstanceAllocator(1);
        int slot = allocator.allocate().orElseThrow();
        assertTrue(allocator.isFull());

        allocator.release(slot);
        assertFalse(allocator.isFull());
        assertEquals(OptionalInt.of(0), allocator.allocate());
    }
}
