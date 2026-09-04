package com.yeowool.admin.banneditem;

/**
 * {@code POSSESSION} ("아이템밴") means the item may never exist in a player's
 * inventory at all — {@link BannedItemListener} confiscates it wherever found
 * (pickup, inventory click, join sweep) on top of blocking crafting.
 * {@code CRAFT} ("조합밴") only blocks the crafting recipe itself; an item
 * obtained some other way (e.g. {@code /관리자아이템}) is still fine to hold.
 */
public enum BanType {
    POSSESSION("아이템밴", "possession"),
    CRAFT("조합밴", "craft");

    private final String displayName;
    private final String column;

    BanType(String displayName, String column) {
        this.displayName = displayName;
        this.column = column;
    }

    public String displayName() {
        return displayName;
    }

    public String column() {
        return column;
    }
}
