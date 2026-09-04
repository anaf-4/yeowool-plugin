package com.yeowool.market.trade;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lets a player type a money amount into chat while a {@link TradeGui} is
 * open, since a GUI has no native text field. The inventory is closed for
 * the duration (see {@link TradeGui#prepareForChatInput}) so the client can
 * actually open its chat box, then reopened once a valid amount is parsed.
 */
public final class TradeChatInputListener implements Listener {

    private record Awaiting(TradeGui gui, TradeSession session) {
    }

    private static final Map<UUID, Awaiting> AWAITING = new ConcurrentHashMap<>();

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private final MessageService messages;

    public TradeChatInputListener(JavaPlugin plugin, YeowoolCoreAPI core, MessageService messages) {
        this.plugin = plugin;
        this.core = core;
        this.messages = messages;
    }

    public static void awaitInput(TradeGui gui, TradeSession session, UUID uuid) {
        AWAITING.put(uuid, new Awaiting(gui, session));
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) {
            return;
        }
        gui.prepareForChatInput(player);
    }

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Awaiting awaiting = AWAITING.remove(event.getPlayer().getUniqueId());
        if (awaiting == null) {
            return;
        }
        event.setCancelled(true);

        String raw = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        Player player = event.getPlayer();

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (awaiting.session().isFinished()) {
                return;
            }
            long amount;
            try {
                amount = Long.parseLong(raw);
            } catch (NumberFormatException e) {
                messages.send(player, "trade.invalid-money-amount");
                awaiting.gui().reopen(player);
                return;
            }
            if (amount < 0 || !core.economyData().hasBalance(player.getUniqueId(), amount)) {
                messages.send(player, "trade.insufficient-funds");
                awaiting.gui().reopen(player);
                return;
            }
            awaiting.session().onMoneyOffered(player.getUniqueId(), amount);
            awaiting.gui().reopen(player);
        });
    }
}
