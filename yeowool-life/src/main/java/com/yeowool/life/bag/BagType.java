package com.yeowool.life.bag;

import org.bukkit.Material;

/** The 4 auto-collect bags: {@code /가방}, and the {@code moafarm_items:ticket_chest} expansion ticket. */
public enum BagType {
    CROP("작물 가방", Material.WHEAT),
    ORE("광물 가방", Material.RAW_IRON),
    FISH("낚시 가방", Material.COD),
    LIVESTOCK("목축 가방", Material.BEEF);

    private final String label;
    private final Material menuIcon;

    BagType(String label, Material menuIcon) {
        this.label = label;
        this.menuIcon = menuIcon;
    }

    public String label() {
        return label;
    }

    /** Plain vanilla icon for the hub/expand-select menus — no custom pack was supplied for the bag system itself. */
    public Material menuIcon() {
        return menuIcon;
    }
}
