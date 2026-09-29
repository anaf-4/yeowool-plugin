package com.yeowool.market.exchange;

import com.yeowool.core.api.gui.ItemGridEditorGui;
import com.yeowool.core.api.service.MessageService;
import com.yeowool.market.exchange.ExchangeRepository.Entry;
import com.yeowool.market.exchange.ExchangeRepository.Limit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.logging.Level;

/** {@code /교환소관리 추가|목록|삭제|열기|npc} — staff only. */
public final class ExchangeCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final MessageService messages;
    private final ExchangeService service;
    private final Executor executor;

    public ExchangeCommand(JavaPlugin plugin, MessageService messages, ExchangeService service, Executor executor) {
        this.plugin = plugin;
        this.messages = messages;
        this.service = service;
        this.executor = executor;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return true;
        }
        switch (args.length == 0 ? "" : args[0]) {
            case "추가" -> add(player, args);
            case "목록" -> list(player);
            case "삭제" -> delete(player, args);
            case "열기" -> service.open(player, 0);
            case "npc", "NPC" -> bindNpc(player);
            default -> messages.send(player, "exchange.usage");
        }
        return true;
    }

    /** {@code 추가 <온> <별조각> [없음|일일|주간] [횟수]} — held item is the reward; cost items go in the editor that opens. */
    private void add(Player player, String[] args) {
        ItemStack reward = player.getInventory().getItemInMainHand();
        if (reward.getType().isAir()) {
            messages.send(player, "exchange.hold-reward");
            return;
        }
        long money;
        long stardust;
        Limit limit;
        int limitCount;
        try {
            money = Long.parseLong(args[1]);
            stardust = Long.parseLong(args[2]);
            limit = args.length > 3 ? Limit.byLabel(args[3]) : Limit.NONE;
            limitCount = args.length > 4 ? Integer.parseInt(args[4]) : 1;
        } catch (ArrayIndexOutOfBoundsException | NumberFormatException e) {
            messages.send(player, "exchange.usage");
            return;
        }
        if (money < 0 || stardust < 0 || limit == null || limitCount < 1) {
            messages.send(player, "exchange.usage");
            return;
        }
        ItemStack rewardCopy = reward.clone();
        messages.send(player, "exchange.put-costs");
        new ItemGridEditorGui("교환 재료를 넣고 닫으세요", List.of(), costs -> {
            // hand the placed materials back — only copies are stored
            player.getInventory().addItem(costs.stream().map(ItemStack::clone).toArray(ItemStack[]::new))
                    .values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
            if (costs.isEmpty() && money == 0 && stardust == 0) {
                messages.send(player, "exchange.no-cost");
                return;
            }
            executor.execute(() -> {
                try {
                    int id = service.repository().insert(rewardCopy, costs, money, stardust, limit, limit == Limit.NONE ? 0 : limitCount);
                    reply(player, "exchange.added", Placeholder.unparsed("id", String.valueOf(id)));
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "교환소 항목 추가 실패", e);
                    reply(player, "exchange.error");
                }
            });
        }).open(player);
    }

    private void list(Player player) {
        executor.execute(() -> {
            try {
                List<Entry> entries = service.repository().list();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (entries.isEmpty()) {
                        messages.send(player, "exchange.list-empty");
                    }
                    for (Entry entry : entries) {
                        Component costs = Component.empty();
                        for (ExchangeService.Cost cost : ExchangeService.mergedCosts(entry)) {
                            costs = costs.append(cost.sample().effectiveName()).append(Component.text(" ×" + cost.amount() + ", "));
                        }
                        messages.send(player, "exchange.list-line",
                                Placeholder.unparsed("id", String.valueOf(entry.id())),
                                Placeholder.component("item", entry.reward().effectiveName()),
                                Placeholder.unparsed("amount", String.valueOf(entry.reward().getAmount())),
                                Placeholder.component("costs", costs),
                                Placeholder.unparsed("money", String.format("%,d", entry.costMoney())),
                                Placeholder.unparsed("stardust", String.format("%,d", entry.costStardust())),
                                Placeholder.unparsed("limit", entry.limit() == Limit.NONE ? "제한 없음"
                                        : entry.limit().label() + " " + entry.limitCount() + "회"));
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "교환소 목록 조회 실패", e);
                reply(player, "exchange.error");
            }
        });
    }

    private void delete(Player player, String[] args) {
        int id;
        try {
            id = Integer.parseInt(args[1]);
        } catch (ArrayIndexOutOfBoundsException | NumberFormatException e) {
            messages.send(player, "exchange.usage");
            return;
        }
        executor.execute(() -> {
            try {
                boolean deleted = service.repository().delete(id);
                reply(player, deleted ? "exchange.deleted" : "exchange.not-found", Placeholder.unparsed("id", String.valueOf(id)));
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "교환소 항목 삭제 실패", e);
                reply(player, "exchange.error");
            }
        });
    }

    private void bindNpc(Player player) {
        if (!Bukkit.getPluginManager().isPluginEnabled("Citizens")) {
            messages.send(player, "exchange.no-citizens");
            return;
        }
        Integer npcId = ExchangeNpcListener.selectedNpcId(player);
        if (npcId == null) {
            messages.send(player, "exchange.select-npc");
            return;
        }
        boolean bound = service.toggleNpc(npcId);
        messages.send(player, bound ? "exchange.npc-bound" : "exchange.npc-unbound", Placeholder.unparsed("id", String.valueOf(npcId)));
    }

    private void reply(Player player, String key, net.kyori.adventure.text.minimessage.tag.resolver.TagResolver... placeholders) {
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, () -> messages.send(player, key, placeholders));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("추가", "목록", "삭제", "열기", "npc").stream().filter(s -> s.startsWith(args[0])).toList();
        }
        if (args.length == 4 && args[0].equals("추가")) {
            return List.of("없음", "일일", "주간");
        }
        return List.of();
    }
}
