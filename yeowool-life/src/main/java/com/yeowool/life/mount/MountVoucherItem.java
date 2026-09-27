package com.yeowool.life.mount;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Optional;

/** A tradeable voucher; the mount id lives in PDC so renaming the item can't change what it unlocks. */
public final class MountVoucherItem {

    private final NamespacedKey key;

    public MountVoucherItem(JavaPlugin plugin) {
        this.key = new NamespacedKey(plugin, "mount_voucher");
    }

    public ItemStack create(MountDefinition mount) {
        ItemStack stack = new ItemStack(mount.icon());
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(mount.displayName() + " 탈것 이용권", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("우클릭하면 이 탈것을 영구히 해금합니다", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("해금 후 /탈것 으로 소환", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)));
        if (mount.customModelData() != 0) {
            meta.setCustomModelData(mount.customModelData());
        }
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, mount.id());
        stack.setItemMeta(meta);
        return stack;
    }

    public Optional<String> read(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return Optional.empty();
        }
        return Optional.ofNullable(stack.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING));
    }
}
