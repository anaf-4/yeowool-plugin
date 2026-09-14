package com.yeowool.admin.cashpackage;

import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * One named cash-shop bundle — {@code /패키지지급} hands its whole
 * {@code items} list to a player at once (mailed if they're offline, see
 * {@link CashPackageGiveCommand}). Meant for selling a set (e.g. a
 * deepsea_relics weapon+armor+tool set) as a single Tebex package instead of
 * per-item products.
 */
public record CashPackage(String name, List<ItemStack> items, long createdAt) {

    public CashPackage withItems(List<ItemStack> newItems) {
        return new CashPackage(name, newItems, createdAt);
    }
}
