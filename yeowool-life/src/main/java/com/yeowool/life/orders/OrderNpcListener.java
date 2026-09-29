package com.yeowool.life.orders;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/** Citizens-only (registered only when Citizens is on): right-clicking a bound NPC opens that system's window. */
public final class OrderNpcListener implements Listener {

    private final OrderService service;

    public OrderNpcListener(OrderService service) {
        this.service = service;
    }

    @EventHandler
    public void onRightClick(NPCRightClickEvent event) {
        if (service.isNpc(event.getNPC().getId())) {
            service.open(event.getClicker());
        }
    }

    /** The NPC {@code sender} selected with {@code /npc select}, or null. */
    static Integer selectedNpcId(CommandSender sender) {
        NPC npc = CitizensAPI.getDefaultNPCSelector().getSelected(sender);
        return npc == null ? null : npc.getId();
    }
}
