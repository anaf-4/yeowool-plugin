package com.yeowool.teleport.rtp;

import dev.lone.itemsadder.api.CustomStack;
import dev.lone.itemsadder.api.FontImages.FontImageWrapper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

/**
 * {@code yeowool_rtp} 네임스페이스의 배경/아이콘 헬퍼. 배경 title()의 imageId는
 * bare font_image 키, icon()의 id는 "namespace:key" — 서로 다른 ItemsAdder API라
 * 섞어 쓰면 안 된다(배틀패스/메뉴에서 이미 겪은 문제).
 */
public final class RtpBackgroundImages {

    private RtpBackgroundImages() {
    }

    public static Component title(int offsetPx, String imageId, Component fallback) {
        if (imageId == null || imageId.isBlank() || !Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) {
            return fallback;
        }
        String offsetPlaceholder = ":offset_" + offsetPx + ":";
        String imagePlaceholder = ":" + imageId + ":";
        String legacy = FontImageWrapper.replaceFontImages(offsetPlaceholder + imagePlaceholder);
        if (legacy.contains(offsetPlaceholder) || legacy.contains(imagePlaceholder)) {
            return fallback;
        }
        return LegacyComponentSerializer.legacySection().deserialize(legacy);
    }

    public static ItemStack icon(String namespacedId) {
        if (namespacedId == null || !Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) {
            return null;
        }
        CustomStack custom = CustomStack.getInstance(namespacedId);
        return custom == null ? null : custom.getItemStack();
    }
}
