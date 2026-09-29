package com.yeowool.market.exchange;

import com.yeowool.core.api.service.MessageService;
import com.yeowool.market.exchange.ExchangeRepository.Entry;
import com.yeowool.market.exchange.ExchangeRepository.Limit;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.LongConsumer;
import java.util.logging.Level;

/** {@code /교환소관리 추가|수정|목록|삭제|열기|npc} — staff only; also answers the editor's chat prompts. */
public final class ExchangeCommand implements CommandExecutor, TabCompleter, Listener {

    private final JavaPlugin plugin;
    private final MessageService messages;
    private final ExchangeService service;
    private final Executor executor;
    private record Prompt(LongConsumer onValue, long createdAt) {
    }

    private static final long PROMPT_TTL_MILLIS = 60_000;

    private final Map<UUID, Prompt> prompts = new ConcurrentHashMap<>();

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
            case "추가" -> add(player);
            case "수정" -> edit(player, args);
            case "목록" -> list(player);
            case "삭제" -> delete(player, args);
            case "열기" -> service.open(player, 0);
            case "npc", "NPC" -> bindNpc(player);
            default -> messages.send(player, "exchange.usage");
        }
        return true;
    }

    /** Held item becomes the reward of a new entry; everything else is set in the editor. */
    private void add(Player player) {
        ItemStack reward = player.getInventory().getItemInMainHand();
        if (reward.getType().isAir()) {
            messages.send(player, "exchange.hold-reward");
            return;
        }
        new ExchangeEditGui(messages, this::ask, this::save, null, reward, List.of(), 0, 0, Limit.NONE, 1).open(player);
    }

    private void edit(Player player, String[] args) {
        Integer id = parseId(player, args);
        if (id == null) {
            return;
        }
        executor.execute(() -> {
            try {
                Optional<Entry> entry = service.repository().get(id);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (entry.isEmpty()) {
                        messages.send(player, "exchange.not-found", Placeholder.unparsed("id", String.valueOf(id)));
                    } else if (player.isOnline()) {
                        ExchangeEditGui.forEntry(messages, this::ask, this::save, entry.get()).open(player);
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "교환소 항목 불러오기 실패", e);
                reply(player, "exchange.error");
            }
        });
    }

    private void save(Player player, Integer id, ItemStack reward, List<ItemStack> costStacks, long money, long stardust,
                      Limit limit, int limitCount) {
        executor.execute(() -> {
            try {
                if (id == null) {
                    int newId = service.repository().insert(reward, costStacks, money, stardust, limit, limitCount);
                    reply(player, "exchange.added", Placeholder.unparsed("id", String.valueOf(newId)));
                } else {
                    boolean updated = service.repository().update(id, reward, costStacks, money, stardust, limit, limitCount);
                    reply(player, updated ? "exchange.updated" : "exchange.not-found", Placeholder.unparsed("id", String.valueOf(id)));
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "교환소 항목 저장 실패", e);
                reply(player, "exchange.error");
            }
        });
    }

    private void ask(Player player, String messageKey, LongConsumer onValue) {
        prompts.put(player.getUniqueId(), new Prompt(onValue, System.currentTimeMillis()));
        messages.send(player, messageKey);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        Prompt prompt = prompts.remove(player.getUniqueId());
        if (prompt == null || System.currentTimeMillis() - prompt.createdAt() > PROMPT_TTL_MILLIS) {
            return; // none, or a forgotten one — let the chat line through
        }
        event.setCancelled(true);
        String raw = PlainTextComponentSerializer.plainText().serialize(event.message()).trim().replace(",", "");
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            long value = -1; // negative = keep the old value
            if (!raw.equals("취소")) {
                try {
                    value = Long.parseLong(raw);
                } catch (NumberFormatException ignored) {
                    // falls through to the message below
                }
                if (value < 0) {
                    messages.send(player, "exchange.bad-number");
                }
            }
            prompt.onValue().accept(value);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        prompts.remove(event.getPlayer().getUniqueId());
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
        Integer id = parseId(player, args);
        if (id == null) {
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

    private Integer parseId(Player player, String[] args) {
        try {
            return Integer.parseInt(args[1]);
        } catch (ArrayIndexOutOfBoundsException | NumberFormatException e) {
            messages.send(player, "exchange.usage");
            return null;
        }
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

    private void reply(Player player, String key, TagResolver... placeholders) {
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, () -> messages.send(player, key, placeholders));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("추가", "수정", "목록", "삭제", "열기", "npc").stream().filter(s -> s.startsWith(args[0])).toList();
        }
        return List.of();
    }
}
