package com.yeowool.life.competition;

import com.yeowool.core.api.event.PlayerRepeatableActionEvent;
import com.yeowool.life.farming.LifeHarvestEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/** Mining/fishing/hunting arrive as {@link PlayerRepeatableActionEvent} (tag = activity key); harvests as {@link LifeHarvestEvent}. */
public final class LifeCompetitionListener implements Listener {

    private final LifeCompetitionService service;

    public LifeCompetitionListener(LifeCompetitionService service) {
        this.service = service;
    }

    @EventHandler
    public void onAction(PlayerRepeatableActionEvent event) {
        CompetitionActivity.byKey(event.getActionType()).ifPresent(activity -> {
            Player player = Bukkit.getPlayer(event.getUuid());
            if (player != null) {
                service.record(player, activity);
            }
        });
    }

    @EventHandler
    public void onHarvest(LifeHarvestEvent event) {
        service.record(event.getPlayer(), CompetitionActivity.FARMING);
    }
}
