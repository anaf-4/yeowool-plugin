package com.yeowool.market.npcshop;

import java.util.List;

/**
 * The exact item-icon positions the "Spectra ShopGUI+" background images are
 * drawn with slot outlines for — the outer border row/column and the
 * 4/45/53 button slots are left bare so the background art shows through
 * untouched instead of a real ItemStack boxing over it where the image has
 * no slot drawn.
 */
public final class ShopLayout {

    /**
     * {@code shop_item_display} (per-shop item browsing, see {@link NPCShopGui})
     * and the admin editor ({@link com.yeowool.market.adminshop.AdminShopEditorGui})
     * — the pack's own {@code ShopGUIPlus/shops/*.yml} 28-slot grid; none of
     * it overlaps the 4/45/53 button slots.
     */
    public static final List<Integer> USABLE_SLOTS = List.of(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    );

    /**
     * {@code shop_gui_menu} (the main category menu, see {@link ShopMainMenuGui})
     * — a sparser centered 3×4 grid, distinct from {@link #USABLE_SLOTS}.
     */
    public static final List<Integer> MAIN_MENU_SLOTS = List.of(
            12, 13, 14,
            21, 22, 23,
            30, 31, 32,
            39, 40, 41
    );

    private ShopLayout() {
    }
}
