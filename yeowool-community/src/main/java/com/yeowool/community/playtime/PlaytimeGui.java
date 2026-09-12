package com.yeowool.community.playtime;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
import com.yeowool.core.api.service.MessageService;
import com.yeowool.core.util.DurationFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * {@code /플레이타임} — a 1x9 GUI with the four milestones (1/6/12/24시간) at
 * slots 1/3/5/7, matching the exact layout requested. Each button shows
 * locked/available/claimed state and, once available, claims on click.
 */
public final class PlaytimeGui extends YeowoolGui {

    private static final int[] SLOTS = {1, 3, 5, 7};
    private static final PlaytimeRewardStore.Tier[] TIERS = {
            PlaytimeRewardStore.Tier.ONE_HOUR,
            PlaytimeRewardStore.Tier.SIX_HOURS,
            PlaytimeRewardStore.Tier.TWELVE_HOURS,
            PlaytimeRewardStore.Tier.TWENTY_FOUR_HOURS
    };

    public PlaytimeGui(YeowoolCoreAPI core, PlaytimeManager playtimeManager, MessageService messages, Player viewer) {
        super(9, Component.text("플레이타임 보상", NamedTextColor.GOLD));

        var data = core.playerData().getOnline(viewer.getUniqueId());
        for (int i = 0; i < TIERS.length; i++) {
            PlaytimeRewardStore.Tier tier = TIERS[i];
            PlaytimeManager.Status status = playtimeManager.status(data, tier);
            setButton(SLOTS[i], GuiButton.of(buildIcon(tier, status), event -> {
                Player clicker = (Player) event.getWhoClicked();
                var claim = playtimeManager.claim(clicker, tier);
                if (claim.isEmpty()) {
                    messages.send(clicker, status.claimed() ? "playtime.already-claimed" : "playtime.not-unlocked");
                } else {
                    messages.send(clicker, "playtime.claim-success",
                            Placeholder.unparsed("reward", String.format("%,d", claim.get().reward())),
                            Placeholder.unparsed("currency", claim.get().currency().displayName()));
                }
                new PlaytimeGui(core, playtimeManager, messages, clicker).open(clicker);
            }));
        }
    }

    private ItemStack buildIcon(PlaytimeRewardStore.Tier tier, PlaytimeManager.Status status) {
        Material material = status.claimed() ? Material.GRAY_DYE : status.unlocked() ? Material.LIME_DYE : Material.CLOCK;
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName((status.claimed()
                ? Component.text(label(tier) + " (완료)", NamedTextColor.GRAY)
                : status.unlocked()
                        ? Component.text(label(tier), NamedTextColor.GREEN)
                        : Component.text(label(tier), NamedTextColor.YELLOW)).decoration(TextDecoration.ITALIC, false));

        String progress = "오늘 누적 플레이타임: " + DurationFormat.humanize(status.currentMinutes() * 60_000L)
                + " / " + DurationFormat.humanize(status.requiredMinutes() * 60_000L);
        Component state;
        if (status.claimed()) {
            state = Component.text("이미 받았습니다.", NamedTextColor.DARK_GRAY);
        } else if (status.unlocked()) {
            state = Component.text("클릭하여 받기", NamedTextColor.GREEN);
        } else {
            long remainingMinutes = Math.max(0, status.requiredMinutes() - status.currentMinutes());
            state = Component.text("남은 시간: " + DurationFormat.humanize(remainingMinutes * 60_000L), NamedTextColor.RED);
        }
        meta.lore(List.of(
                Component.text(progress, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                state.decoration(TextDecoration.ITALIC, false)));
        stack.setItemMeta(meta);
        return stack;
    }

    private String label(PlaytimeRewardStore.Tier tier) {
        return switch (tier) {
            case ONE_HOUR -> "1시간 보상";
            case SIX_HOURS -> "6시간 보상";
            case TWELVE_HOURS -> "12시간 보상";
            case TWENTY_FOUR_HOURS -> "24시간 보상";
        };
    }
}
