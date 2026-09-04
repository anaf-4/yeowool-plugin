package com.yeowool.market.npcshop;

import org.bukkit.Material;

import java.util.List;

/**
 * One admin-configured NPC shop (config.yml's {@code npc-shop.shops.<id>}):
 * its own size, buy/sell mode, item list, and an optional decoration/border
 * item ("도트" GUI styling) filling every slot no item was placed in.
 *
 * <p>{@code rotationPool}/{@code rotationSlots}/{@code rotationIntervalMinutes}
 * are an optional "한정 판매" layer on top of the always-shown {@code items}:
 * every interval, {@link com.yeowool.market.npcshop.ShopRotationManager}
 * picks a random subset of {@code rotationPool} (as many as there are
 * {@code rotationSlots}) and places them into those slots. Empty pool/slots
 * or a non-positive interval means rotation is disabled for this shop.
 *
 * <p>{@code pageCount} backs the multi-page "Spectra ShopGUI+"-style layout:
 * each {@link ShopItem} declares its own 0-indexed {@link ShopItem#page()},
 * and a single {@code ShopDefinition} covers every page of the shop — the
 * GUI ({@link NPCShopGui}) is handed which page to render separately and
 * filters {@code items} down to that page itself. {@code pageCount} is
 * normally {@code 1 + max(item page)} (see {@link ShopConfigLoader}), except
 * for in-game-created shops ({@code com.yeowool.market.adminshop}) where an
 * admin may have declared an extra still-empty page via 상점페이지추가.
 */
public record ShopDefinition(
        String id,
        String title,
        int size,
        ShopMode mode,
        List<ShopItem> items,
        Decoration decoration,
        List<ShopItem> rotationPool,
        List<Integer> rotationSlots,
        int rotationIntervalMinutes,
        int pageCount
) {
    public boolean hasRotation() {
        return !rotationPool.isEmpty() && !rotationSlots.isEmpty() && rotationIntervalMinutes > 0;
    }

    /**
     * Border/background filler. {@code customModelData} of -1 means plain
     * vanilla texture; set it to point at a resource pack's "도트" item.
     */
    public record Decoration(Material material, int customModelData) {
        public boolean hasCustomModelData() {
            return customModelData >= 0;
        }
    }
}
