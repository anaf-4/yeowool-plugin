package com.yeowool.market.npcshop;

import com.yeowool.core.api.model.CurrencyType;
import org.bukkit.Material;

/**
 * One row of an NPC shop's fixed price list (plugin plan 7.1: "관리진이
 * 지정한 물품만 구매/판매"). A price of 0 or -1 disables that direction for
 * the item (both fail {@link #isBuyable()}/{@link #isSellable()}'s
 * {@code > 0} check; {@code -1} is the explicit "구매불가"/"판매불가" sentinel
 * {@code /상점아이템설정} accepts, since its two price args are always
 * required positionally and can't just be omitted like a YAML key can) —
 * e.g. sell-price only means the NPC buys it but never sells it — and
 * {@link ShopDefinition#mode()} can additionally disable a whole direction
 * for every item in the shop at once.
 *
 * <p>Exactly one of {@code material} or {@code itemId} is set: a plain
 * vanilla item uses {@code material}, an ItemsAdder custom item uses
 * {@code itemId} (its namespaced id, e.g. {@code "yeowool:golden_wheat"}) —
 * see {@link com.yeowool.market.util.ItemResolver} for how both are turned
 * into an actual {@link org.bukkit.inventory.ItemStack}.
 *
 * <p>{@code slot} is which GUI slot this item sits in; -1 means "place me
 * in the next free slot" so simple configs don't need to hand-place every
 * item. {@code customModelData} is optional (-1 = none) for vanilla items
 * that want a resource pack's "도트" (pixel-art) icon texture; it's ignored
 * for ItemsAdder items since those already carry their own model.
 *
 * <p>{@code currency} picks which balance {@code buyPrice}/{@code sellPrice}
 * are denominated in ("온" or "캐시") — set independently per item, so one
 * shop can mix regular-currency and premium-currency listings.
 *
 * <p>{@code page} (0-indexed) is which page of the shop this item shows on
 * (YAML's {@code page:} key is 1-indexed to match the "Spectra ShopGUI+"
 * reference pack's shop yaml format — {@code page: 2} in config becomes
 * {@code page=1} here); see {@link ShopDefinition#pageCount()}.
 *
 * <p>{@code strictMatch}, when selling, additionally requires the item be
 * "plain" (no custom name/lore/enchantments/damage/other meta) to match —
 * off by default (same as the reference pack's own {@code compareMeta:
 * false}), since most shops don't need it, but stops e.g. an enchanted or
 * renamed item quietly selling for the same price as a plain one.
 *
 * <p>{@code customDisplayName} (nullable, {@link net.kyori.adventure.text.serializer.gson.GsonComponentSerializer}-encoded)
 * carries over a colored/styled name from the item that was placed via
 * {@code /상점아이템설정} — e.g. a fish given through {@code /낚시관리}, whose
 * name is colored by rarity. Without this, {@link com.yeowool.market.util.ItemResolver}
 * would only ever have the item's bare material/ItemsAdder name to show,
 * losing that styling. {@code null} means "just use the item's own name",
 * which is what every config.yml-defined shop item still does.
 */
public record ShopItem(Material material, String itemId, long buyPrice, long sellPrice, int slot, int customModelData,
                        CurrencyType currency, int page, boolean strictMatch, String customDisplayName) {

    public boolean isCustomItem() {
        return itemId != null;
    }

    public boolean isBuyable() {
        return buyPrice > 0;
    }

    public boolean isSellable() {
        return sellPrice > 0;
    }

    public boolean hasCustomModelData() {
        return customModelData >= 0;
    }

    public String displayId() {
        return isCustomItem() ? itemId : material.name();
    }
}
