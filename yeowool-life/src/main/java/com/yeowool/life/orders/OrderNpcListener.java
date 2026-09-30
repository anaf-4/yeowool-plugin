package com.yeowool.life.orders;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.function.Consumer;
import java.util.function.IntPredicate;

/**
 * Citizens-only (registered only when Citizens is on): right-clicking a bound NPC opens that system's window —
 * 식당/수산시장 ({@link OrderService}) and the 대장간 ({@code MetalService}).
 */
public final class OrderNpcListener implements Listener {

    private final IntPredicate isNpc;
    private final Consumer<Player> open;

    public OrderNpcListener(IntPredicate isNpc, Consumer<Player> open) {
        this.isNpc = isNpc;
        this.open = open;
    }

    @EventHandler
    public void onRightClick(NPCRightClickEvent event) {
        if (isNpc.test(event.getNPC().getId())) {
            open.accept(event.getClicker());
        }
    }

    /** The NPC {@code sender} selected with {@code /npc select}, or null. */
    public static Integer selectedNpcId(CommandSender sender) {
        NPC npc = CitizensAPI.getDefaultNPCSelector().getSelected(sender);
        return npc == null ? null : npc.getId();
    }
}
