package com.yeowool.community.quest;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
import com.yeowool.core.api.service.MessageService;
import com.yeowool.core.util.DurationFormat;
import dev.lone.itemsadder.api.CustomStack;
import dev.lone.itemsadder.api.FontImages.FontImageWrapper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * {@code /출석체크} — daily/weekly/monthly claim buttons laid out exactly
 * like the "Rewards UI by MCMobs" ItemsAdder asset pack the user provided
 * (slots 19/22/25 of a 36-slot menu, matching that pack's reference
 * {@code DailyRewards} plugin config). The custom icons
 * ({@code rewards:rewards_daily}/{@code _dailye} etc.) and the GUI
 * background (one of {@code rewards_blue/brown/green/purple/red}, a
 * font-image rendered into the inventory title — same technique
 * {@code RankIconManager} uses for chat rank icons) both come from that
 * pack's {@code contents/rewards} folder; without ItemsAdder installed (or
 * without that pack imported), this still works with a plain title and
 * vanilla paper icons.
 */
public final class AttendanceGui extends YeowoolGui {

    private static final String NAMESPACE = "rewards";

    public AttendanceGui(YeowoolCoreAPI core, AttendanceManager attendanceManager, MessageService messages, Player viewer) {
        super(36, backgroundTitle(attendanceManager.guiBackground()));

        var data = core.playerData().getOnline(viewer.getUniqueId());

        var dailyStatus = attendanceManager.status(data);
        setButton(19, GuiButton.of(buildDailyIcon(dailyStatus), event -> {
            Player clicker = (Player) event.getWhoClicked();
            var claim = attendanceManager.claim(clicker);
            if (claim.isEmpty()) {
                messages.send(clicker, "attendance.already");
            } else {
                messages.send(clicker, "attendance.success",
                        Placeholder.unparsed("streak", String.valueOf(claim.get().streak())),
                        Placeholder.unparsed("reward", String.format("%,d", claim.get().reward())),
                        Placeholder.unparsed("currency", claim.get().currency().displayName()));
                if (claim.get().streakBonus() > 0) {
                    messages.send(clicker, "attendance.streak-bonus",
                            Placeholder.unparsed("streak", String.valueOf(claim.get().streak())),
                            Placeholder.unparsed("bonus", String.format("%,d", claim.get().streakBonus())),
                            Placeholder.unparsed("currency", claim.get().currency().displayName()));
                }
            }
            new AttendanceGui(core, attendanceManager, messages, clicker).open(clicker);
        }));

        var weeklyStatus = attendanceManager.weeklyStatus(data);
        setButton(22, GuiButton.of(buildCooldownIcon("weekly", "주간 보상", weeklyStatus), event -> {
            Player clicker = (Player) event.getWhoClicked();
            var claim = attendanceManager.claimWeekly(clicker);
            if (claim.isEmpty()) {
                messages.send(clicker, "attendance.on-cooldown");
            } else {
                messages.send(clicker, "attendance.tier-success",
                        Placeholder.unparsed("reward", String.format("%,d", claim.get().reward())),
                        Placeholder.unparsed("currency", claim.get().currency().displayName()));
            }
            new AttendanceGui(core, attendanceManager, messages, clicker).open(clicker);
        }));

        var monthlyStatus = attendanceManager.monthlyStatus(data);
        setButton(25, GuiButton.of(buildCooldownIcon("monthly", "월간 보상", monthlyStatus), event -> {
            Player clicker = (Player) event.getWhoClicked();
            var claim = attendanceManager.claimMonthly(clicker);
            if (claim.isEmpty()) {
                messages.send(clicker, "attendance.on-cooldown");
            } else {
                messages.send(clicker, "attendance.tier-success",
                        Placeholder.unparsed("reward", String.format("%,d", claim.get().reward())),
                        Placeholder.unparsed("currency", claim.get().currency().displayName()));
            }
            new AttendanceGui(core, attendanceManager, messages, clicker).open(clicker);
        }));
    }

    private ItemStack buildDailyIcon(AttendanceManager.Status status) {
        ItemStack stack = resolveCustomItem(status.claimedToday() ? "rewards_dailye" : "rewards_daily");
        ItemMeta meta = stack.getItemMeta();
        meta.displayName((status.claimedToday()
                ? Component.text("일일 보상 (완료)", NamedTextColor.GRAY)
                : Component.text("일일 보상", NamedTextColor.GREEN)).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("연속 출석: " + status.streak() + "일", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                (status.claimedToday()
                        ? Component.text("내일 다시 받을 수 있습니다.", NamedTextColor.DARK_GRAY)
                        : Component.text("클릭하여 받기", NamedTextColor.GREEN)).decoration(TextDecoration.ITALIC, false)));
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack buildCooldownIcon(String idPrefix, String name, AttendanceManager.CooldownStatus status) {
        ItemStack stack = resolveCustomItem(status.available() ? "rewards_" + idPrefix : "rewards_" + idPrefix + "e");
        ItemMeta meta = stack.getItemMeta();
        meta.displayName((status.available()
                ? Component.text(name, NamedTextColor.GREEN)
                : Component.text(name + " (대기 중)", NamedTextColor.GRAY)).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of((status.available()
                ? Component.text("클릭하여 받기", NamedTextColor.GREEN)
                : Component.text("남은 시간: " + DurationFormat.humanize(status.remainingMillis()), NamedTextColor.DARK_GRAY))
                .decoration(TextDecoration.ITALIC, false)));
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack resolveCustomItem(String id) {
        if (Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) {
            CustomStack custom = CustomStack.getInstance(NAMESPACE + ":" + id);
            if (custom != null) {
                return custom.getItemStack();
            }
        }
        return new ItemStack(Material.PAPER);
    }

    /**
     * Falls back to a plain text title if ItemsAdder isn't installed or the
     * pack's font_images aren't loaded. The {@code :offset_-16:} spacer glyph
     * must come right before the background image id — without it the panel
     * renders shifted ~16px to the right of the inventory's left edge (this
     * exact prefix comes from the pack's own {@code DailyRewards/lang.yml}:
     * {@code menu-title: '&f:offset_-16::rewards_purple:'}).
     */
    private static Component backgroundTitle(String background) {
        if (background == null || background.isBlank() || !Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) {
            return Component.text("출석 보상", NamedTextColor.GOLD);
        }
        String offsetPlaceholder = ":offset_-16:";
        String imagePlaceholder = ":" + background + ":";
        String legacy = FontImageWrapper.replaceFontImages(offsetPlaceholder + imagePlaceholder);
        if (legacy.contains(offsetPlaceholder) || legacy.contains(imagePlaceholder)) {
            return Component.text("출석 보상", NamedTextColor.GOLD);
        }
        return LegacyComponentSerializer.legacySection().deserialize(legacy);
    }
}
