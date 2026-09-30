package com.yeowool.market.exchange;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/** Citizens-only: right-clicking a bound NPC opens the 교환소 (the only way players can open it). */
public final class ExchangeNpcListener implements Listener {

    private final ExchangeService service;

    public ExchangeNpcListener(ExchangeService service) {
        this.service = service;
    }

    @EventHandler
    public void onRightClick(NPCRightClickEvent event) {
        if (service.isExchangeNpc(event.getNPC().getId())) {
            service.open(event.getClicker(), 0);
        }
    }

    /** The NPC {@code sender} selected with {@code /npc select}, or null. */
    public static Integer selectedNpcId(CommandSender sender) {
        NPC npc = CitizensAPI.getDefaultNPCSelector().getSelected(sender);
        return npc == null ? null : npc.getId();
    }
}
