package com.yeowool.life.bag;

import org.bukkit.inventory.ItemStack;

/**
 * One player's bag of one {@link BagType} — a plain slot array (not backed
 * by a live Bukkit {@link org.bukkit.inventory.Inventory} outside of an open
 * {@code BagGui}), so it can be mutated off-GUI by the auto-collect listener
 * at any time. {@link #dirty()} tracks whether {@link BagManager} still owes
 * the database a write.
 */
final class PlayerBag {

    static final int DEFAULT_CAPACITY = 18;
    static final int MAX_CAPACITY = 54;

    private ItemStack[] contents;
    private boolean dirty;

    PlayerBag(int capacity, ItemStack[] loaded) {
        this.contents = new ItemStack[Math.max(capacity, DEFAULT_CAPACITY)];
        if (loaded != null) {
            System.arraycopy(loaded, 0, contents, 0, Math.min(loaded.length, contents.length));
        }
    }

    int capacity() {
        return contents.length;
    }

    ItemStack[] contents() {
        return contents;
    }

    boolean dirty() {
        return dirty;
    }

    void markClean() {
        dirty = false;
    }

    void markDirty() {
        dirty = true;
    }

    /** Replaces the whole contents array (e.g. after a {@code BagGui} close) with the same capacity. */
    void setContents(ItemStack[] newContents) {
        ItemStack[] resized = new ItemStack[contents.length];
        System.arraycopy(newContents, 0, resized, 0, Math.min(newContents.length, resized.length));
        contents = resized;
        dirty = true;
    }

    boolean expand(int by) {
        int newCapacity = Math.min(contents.length + by, MAX_CAPACITY);
        if (newCapacity == contents.length) {
            return false;
        }
        ItemStack[] resized = new ItemStack[newCapacity];
        System.arraycopy(contents, 0, resized, 0, contents.length);
        contents = resized;
        dirty = true;
        return true;
    }

    /** Merges as much of {@code item} as fits (existing stacks first, then empty slots); returns any leftover, or {@code null} if all of it fit. */
    ItemStack addItem(ItemStack item) {
        ItemStack remaining = item.clone();
        int maxStack = remaining.getMaxStackSize();

        for (int i = 0; i < contents.length && remaining.getAmount() > 0; i++) {
            ItemStack slot = contents[i];
            if (slot != null && slot.getAmount() < maxStack && slot.isSimilar(remaining)) {
                int space = maxStack - slot.getAmount();
                int move = Math.min(space, remaining.getAmount());
                slot.setAmount(slot.getAmount() + move);
                remaining.setAmount(remaining.getAmount() - move);
                dirty = true;
            }
        }
        for (int i = 0; i < contents.length && remaining.getAmount() > 0; i++) {
            if (contents[i] == null) {
                int move = Math.min(maxStack, remaining.getAmount());
                ItemStack placed = remaining.clone();
                placed.setAmount(move);
                contents[i] = placed;
                remaining.setAmount(remaining.getAmount() - move);
                dirty = true;
            }
        }
        return remaining.getAmount() > 0 ? remaining : null;
    }
}
