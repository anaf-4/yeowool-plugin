package com.yeowool.land.listener;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.event.PlayerLandXpChangeEvent;
import com.yeowool.core.api.service.MessageService;
import com.yeowool.land.level.LandLevelTable;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Reacts to XP changes YeowoolCore reports (see
 * {@link com.yeowool.core.api.service.LandStatService} javadoc for why level
 * thresholds live here rather than in Core). Reaching a tier's XP no longer
 * auto-applies the level — it only notifies the player that
 * {@code /토지 업그레이드} is now available (see {@link LandLevelTable} and
 * {@link com.yeowool.land.command.LandCommand#onCommand}), since upgrading
 * may also cost from the land's bank.
 */
public final class LandLevelListener implements Listener {

    private static final String NOTIFIED_SETTING = "land.upgrade-notified-level";

    private final YeowoolCoreAPI core;
    private final LandLevelTable levelTable;
    private final MessageService messages;

    public LandLevelListener(YeowoolCoreAPI core, LandLevelTable levelTable, MessageService messages) {
        this.core = core;
        this.levelTable = levelTable;
        this.messages = messages;
    }

    @EventHandler
    public void onXpChange(PlayerLandXpChangeEvent event) {
        int currentLevel = core.landStats().getLandLevel(event.getUuid());
        var nextTier = levelTable.nextTier(currentLevel);
        if (nextTier.isEmpty() || event.getNewTotal() < nextTier.get().requiredXp()) {
            return;
        }

        Player player = org.bukkit.Bukkit.getPlayer(event.getUuid());
        if (player == null) {
            return;
        }
        var data = core.playerData().getOnline(player.getUniqueId());
        int notifiedLevel = Integer.parseInt(data.getSetting(NOTIFIED_SETTING, "0"));
        if (notifiedLevel >= nextTier.get().level()) {
            return; // already told them about this tier
        }
        data.setSetting(NOTIFIED_SETTING, String.valueOf(nextTier.get().level()));

        core.sounds().play(player, "success");
        messages.send(player, "land.upgrade-available", Placeholder.unparsed("level", String.valueOf(nextTier.get().level())));
    }
}
