package com.yeowool.market.casino;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
import com.yeowool.market.exchange.ExchangeNpcListener;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.logging.Level;

import static com.yeowool.market.casino.CasinoService.fmt;

/** {@code /카지노} (chip exchange) and {@code /카지노관리 칩|기록|npc|리로드} (staff). */
public final class CasinoCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private final MessageService messages;
    private final CasinoService service;
    private final Executor executor;

    public CasinoCommand(JavaPlugin plugin, YeowoolCoreAPI core, MessageService messages, CasinoService service, Executor executor) {
        this.plugin = plugin;
        this.core = core;
        this.messages = messages;
        this.service = service;
        this.executor = executor;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equals("카지노")) {
            if (!(sender instanceof Player player)) {
                messages.send(sender, "general.player-only");
            } else if (!service.settings().exchangeAnywhere()) {
                messages.send(player, "casino.npc-only");
            } else {
                service.openExchange(player);
            }
            return true;
        }
        switch (args.length == 0 ? "" : args[0]) {
            case "칩" -> chips(sender, args);
            case "기록" -> history(sender, args);
            case "npc", "NPC" -> bindNpc(sender);
            case "리로드" -> reload(sender);
            default -> messages.send(sender, "casino.usage");
        }
        return true;
    }

    private void chips(CommandSender sender, String[] args) {
        OfflinePlayer target = args.length >= 3 ? find(args[1]) : null;
        if (target == null) {
            messages.send(sender, args.length >= 3 ? "casino.unknown-player" : "casino.usage");
            return;
        }
        String action = args[2];
        long amount;
        try {
            amount = args.length >= 4 ? Long.parseLong(args[3]) : 0;
        } catch (NumberFormatException e) {
            amount = 0;
        }
        if (!action.equals("확인") && amount <= 0) {
            messages.send(sender, "casino.usage");
            return;
        }
        UUID uuid = target.getUniqueId();
        TagResolver who = Placeholder.unparsed("player", target.getName() == null ? args[1] : target.getName());
        long chips = amount;
        executor.execute(() -> {
            try {
                String key;
                long balance;
                switch (action) {
                    case "확인" -> {
                        key = "casino.admin-balance";
                        balance = service.repository().balance(uuid);
                    }
                    case "지급" -> {
                        key = "casino.admin-given";
                        balance = service.repository().give(uuid, chips);
                    }
                    case "회수" -> {
                        var left = service.repository().take(uuid, chips);
                        key = left.isPresent() ? "casino.admin-taken" : "casino.admin-short";
                        balance = left.isPresent() ? left.getAsLong() : service.repository().balance(uuid);
                    }
                    default -> {
                        reply(sender, "casino.usage");
                        return;
                    }
                }
                if (key.equals("casino.admin-given") || key.equals("casino.admin-taken")) {
                    core.logs().log(CasinoService.SOURCE, "casino", uuid, "관리진 칩 " + action + " (" + sender.getName() + ")",
                            Map.of("chips", String.valueOf(action.equals("지급") ? chips : -chips)));
                }
                reply(sender, key, who, Placeholder.unparsed("amount", fmt(chips)), Placeholder.unparsed("balance", fmt(balance)));
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "카지노 칩 관리 실패", e);
                reply(sender, "casino.error");
            }
        });
    }

    private void history(CommandSender sender, String[] args) {
        OfflinePlayer target = args.length >= 2 ? find(args[1]) : null;
        if (target == null) {
            messages.send(sender, args.length >= 2 ? "casino.unknown-player" : "casino.usage");
            return;
        }
        TagResolver who = Placeholder.unparsed("player", target.getName() == null ? args[1] : target.getName());
        executor.execute(() -> {
            try {
                List<CasinoRepository.LogRow> rows = service.repository().recent(target.getUniqueId(), 20);
                if (!plugin.isEnabled()) {
                    return;
                }
                SimpleDateFormat time = new SimpleDateFormat("MM-dd HH:mm");
                Bukkit.getScheduler().runTask(plugin, () -> {
                    messages.send(sender, rows.isEmpty() ? "casino.history-empty" : "casino.history-header", who);
                    for (CasinoRepository.LogRow row : rows) {
                        messages.send(sender, "casino.history-line",
                                Placeholder.unparsed("time", time.format(new Date(row.createdAt()))),
                                Placeholder.unparsed("game", CasinoService.Game.labelOf(row.game())),
                                Placeholder.unparsed("bet", fmt(row.bet())), Placeholder.unparsed("payout", fmt(row.payout())),
                                Placeholder.unparsed("detail", row.detail()));
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "카지노 기록 조회 실패", e);
                reply(sender, "casino.error");
            }
        });
    }

    private void bindNpc(CommandSender sender) {
        if (!Bukkit.getPluginManager().isPluginEnabled("Citizens")) {
            messages.send(sender, "casino.no-citizens");
            return;
        }
        Integer npcId = ExchangeNpcListener.selectedNpcId(sender);
        if (npcId == null) {
            messages.send(sender, "casino.select-npc");
            return;
        }
        boolean bound = service.toggleNpc(npcId);
        messages.send(sender, bound ? "casino.npc-bound" : "casino.npc-unbound", Placeholder.unparsed("id", String.valueOf(npcId)));
    }

    private void reload(CommandSender sender) {
        plugin.reloadConfig();
        try {
            service.reload(CasinoService.Settings.load(plugin.getConfig().getConfigurationSection("casino")));
            messages.send(sender, "casino.reloaded");
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "카지노 설정 리로드 실패", e);
            messages.send(sender, "casino.reload-failed", Placeholder.unparsed("error", String.valueOf(e.getMessage())));
        }
    }

    private static OfflinePlayer find(String name) {
        OfflinePlayer target = Bukkit.getPlayerExact(name);
        return target != null ? target : Bukkit.getOfflinePlayerIfCached(name);
    }

    private void reply(CommandSender sender, String key, TagResolver... placeholders) {
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, () -> messages.send(sender, key, placeholders));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equals("카지노")) {
            return List.of();
        }
        List<String> options = switch (args.length) {
            case 1 -> List.of("칩", "기록", "npc", "리로드");
            case 2 -> args[0].equals("칩") || args[0].equals("기록")
                    ? Bukkit.getOnlinePlayers().stream().map(Player::getName).toList() : List.of();
            case 3 -> args[0].equals("칩") ? List.of("지급", "회수", "확인") : List.of();
            default -> List.of();
        };
        return options.stream().filter(s -> s.startsWith(args[args.length - 1])).toList();
    }
}
