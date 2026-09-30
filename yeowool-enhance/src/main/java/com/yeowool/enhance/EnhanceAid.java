package com.yeowool.enhance;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * The /강화 보조 재료 칸 item: 강화 촉진제 (+N% 성공률) or 파괴 방지 부적, both vanilla items tagged by
 * YeowoolLife's 대장간 (spec 2026-09-30-fantasy-metals §4). Identified only by PDC — the key strings must match
 * {@code com.yeowool.life.metals.MetalItems}. {@link #NONE} = empty slot or anything else.
 */
public record EnhanceAid(int boosterPercent, boolean charm) {

    public static final EnhanceAid NONE = new EnhanceAid(0, false);
    static final NamespacedKey BOOSTER_KEY = new NamespacedKey("yeowool", "enhance_booster");
    static final NamespacedKey CHARM_KEY = new NamespacedKey("yeowool", "enhance_charm");

    /** How a risky failure ends. */
    public enum Fail { SAFE, DOWNGRADE, DESTROY, CHARMED }

    public static EnhanceAid of(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return NONE;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return NONE;
        }
        var pdc = meta.getPersistentDataContainer();
        if (pdc.has(CHARM_KEY, PersistentDataType.BYTE)) {
            return new EnhanceAid(0, true);
        }
        Integer percent = pdc.get(BOOSTER_KEY, PersistentDataType.INTEGER);
        return percent == null || percent <= 0 ? NONE : new EnhanceAid(percent, false);
    }

    public boolean isBooster() {
        return boosterPercent > 0;
    }

    /** Base success rate plus the booster, capped at 100%. */
    public static double boostedRate(double base, int boosterPercent) {
        return Math.min(100.0, base + Math.max(0, boosterPercent));
    }

    /**
     * Outcome of a failed attempt at a risky level: {@code roll} in [0,100) below destroy% destroys, below
     * destroy%+downgrade% downgrades; transcended gear may turn a destroy into a downgrade; a charm turns either into
     * {@link Fail#CHARMED} (nothing lost).
     */
    public static Fail failOutcome(double roll, double destroyPercent, double downgradePercent, boolean destroyBecomesDowngrade, boolean charm) {
        Fail fail = roll < destroyPercent ? Fail.DESTROY : roll < destroyPercent + downgradePercent ? Fail.DOWNGRADE : Fail.SAFE;
        if (fail == Fail.DESTROY && destroyBecomesDowngrade) {
            fail = Fail.DOWNGRADE;
        }
        return charm && fail != Fail.SAFE ? Fail.CHARMED : fail;
    }
}
