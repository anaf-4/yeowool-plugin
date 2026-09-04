package com.yeowool.teleport.rtp;

import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
import com.yeowool.core.api.service.MessageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * {@code /rtp} — 현재 차원(오버월드/네더/엔드)에 맞는 배경을 보여주고, 가운데
 * 버튼으로 무작위 순간이동을 시작한다. 쿨다운 중이면 잠금 아이콘으로 남은
 * 시간을 보여준다.
 */
public final class RtpGui extends YeowoolGui {

    private static final int CENTER_SLOT = 13;

    public RtpGui(RtpManager manager, RtpConfig config, MessageService messages, Player player) {
        super(27, RtpBackgroundImages.title(config.backgroundOffsetPx(), backgroundImageId(player.getWorld().getEnvironment()),
                Component.text("무작위 순간이동", NamedTextColor.DARK_AQUA)));

        RtpManager.CooldownStatus status = manager.cooldownStatus(player);
        if (status.available()) {
            setButton(CENTER_SLOT, GuiButton.of(readyIcon(), event -> {
                Player clicker = (Player) event.getWhoClicked();
                clicker.closeInventory();
                begin(manager, messages, clicker);
            }));
        } else {
            setButton(CENTER_SLOT, GuiButton.display(lockedIcon(status.remainingSeconds())));
        }
    }

    public static void begin(RtpManager manager, MessageService messages, Player player) {
        RtpManager.CooldownStatus status = manager.cooldownStatus(player);
        if (!status.available()) {
            messages.send(player, "general.cooldown", Placeholder.unparsed("seconds", String.valueOf(status.remainingSeconds())));
            return;
        }
        manager.findRandomSafeLocation(player.getWorld())
                .ifPresentOrElse(
                        location -> manager.teleport(player, location),
                        () -> messages.send(player, "rtp.no-safe-location"));
    }

    private static String backgroundImageId(World.Environment environment) {
        return switch (environment) {
            case NETHER -> "rtp_bg_nether";
            case THE_END -> "rtp_bg_end";
            default -> "rtp_bg_overworld";
        };
    }

    private ItemStack readyIcon() {
        ItemStack stack = RtpBackgroundImages.icon("yeowool_rtp:rtp_pin");
        if (stack == null) {
            stack = new ItemStack(Material.ENDER_PEARL);
        }
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text("무작위 순간이동", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("클릭하여 무작위 위치로 이동", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack lockedIcon(long remainingSeconds) {
        ItemStack stack = RtpBackgroundImages.icon("yeowool_rtp:rtp_lock");
        if (stack == null) {
            stack = new ItemStack(Material.BARRIER);
        }
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text("쿨다운 중", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("남은 시간: " + remainingSeconds + "초", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        stack.setItemMeta(meta);
        return stack;
    }
}
