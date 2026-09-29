package com.yeowool.market.exchange;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
import com.yeowool.market.exchange.ExchangeRepository.Entry;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.logging.Level;

/**
 * 교환소: pay items + 온 + 별조각 for an admin-set reward. Opened only from bound NPCs.
 * An exchange claims the usage limit and spends 별조각 on the worker (both atomic in the DB), then takes
 * items and 온 on the main thread; if that last step fails, the 별조각 and the use are given back.
 */
public final class ExchangeService {

    private static final String SOURCE = "YeowoolMarket";
    private static final int PLAYER_STORAGE_SLOTS = 36;

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private final MessageService messages;
    private final ExchangeRepository repository;
    private final Executor executor;
    private final File npcFile;
    private final Set<Integer> npcIds = new LinkedHashSet<>();
    // main thread only
    private final Set<UUID> busy = new HashSet<>();

    public ExchangeService(JavaPlugin plugin, YeowoolCoreAPI core, MessageService messages, ExchangeRepository repository, Executor executor) {
        this.plugin = plugin;
        this.core = core;
        this.messages = messages;
        this.repository = repository;
        this.executor = executor;
        this.npcFile = new File(plugin.getDataFolder(), "exchange-npcs.yml");
        npcIds.addAll(YamlConfiguration.loadConfiguration(npcFile).getIntegerList("npc-ids"));
    }

    public ExchangeRepository repository() {
        return repository;
    }

    public boolean isExchangeNpc(int npcId) {
        return npcIds.contains(npcId);
    }

    /** Binds or unbinds a Citizens NPC on this server; returns whether it is now bound. */
    public boolean toggleNpc(int npcId) {
        boolean bound = npcIds.add(npcId) || !npcIds.remove(npcId);
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("npc-ids", new ArrayList<>(npcIds));
        try {
            yaml.save(npcFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "exchange-npcs.yml 저장 실패", e);
        }
        return bound;
    }

    /** Loads entries, this player's usage and 별조각 off the main thread, then opens the GUI. */
    public void open(Player player, int page) {
        UUID uuid = player.getUniqueId();
        LocalDate today = LocalDate.now();
        executor.execute(() -> {
            try {
                List<Entry> entries = repository.list();
                Map<String, Integer> usage = repository.usage(uuid, today);
                long stardust = core.stardust().balance(uuid).join();
                runMain(() -> {
                    Player online = Bukkit.getPlayer(uuid);
                    if (online != null) {
                        new ExchangeGui(this, entries, usage, stardust, today, page).open(online);
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "교환소 불러오기 실패", e);
                runMain(() -> sendIfOnline(uuid, "exchange.error"));
            }
        });
    }

    /** Main thread: one GUI click. */
    public void exchange(Player player, Entry entry, int page) {
        UUID uuid = player.getUniqueId();
        if (freeAndUnlimited(entry)) {
            // never hand out a reward for nothing, even if such a row got into the DB some other way
            messages.send(player, "exchange.free-blocked");
            return;
        }
        if (!busy.add(uuid)) {
            return;
        }
        String missing = missingCost(player, entry);
        if (missing != null) {
            busy.remove(uuid);
            messages.send(player, missing);
            return;
        }
        String period = ExchangeRepository.periodKey(entry.limit(), LocalDate.now());
        String reason = "교환소 #" + entry.id();
        executor.execute(() -> {
            String fail = null;
            boolean claimed = false;
            try {
                // the GUI may be older than an edit or delete — only trade on exactly what the player saw
                if (!repository.get(entry.id()).equals(Optional.of(entry))) {
                    fail = "exchange.gone";
                } else if (period != null && !(claimed = repository.claimUse(uuid, entry.id(), period, entry.limitCount()))) {
                    fail = "exchange.limit-reached";
                } else if (entry.costStardust() > 0 && !core.stardust().spend(uuid, entry.costStardust(), SOURCE, reason).join()) {
                    fail = "exchange.no-stardust";
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "교환 처리 실패: " + uuid + " " + reason, e);
                fail = "exchange.error";
            }
            if (fail != null) {
                if (claimed) {
                    releaseUse(uuid, entry, period);
                }
                String key = fail;
                runMain(() -> {
                    busy.remove(uuid);
                    sendIfOnline(uuid, key);
                });
                return;
            }
            // ponytail: a server stop between here and complete() loses the spent 별조각/use (no reward either); refund by hand from the log.
            runMain(() -> complete(uuid, entry, period, page, reason));
        });
    }

    private void complete(UUID uuid, Entry entry, String period, int page, String reason) {
        busy.remove(uuid);
        Player player = Bukkit.getPlayer(uuid);
        String missing = player == null ? "exchange.error" : missingCost(player, entry);
        if (missing == null && entry.costMoney() > 0
                && !core.economyData().modifyBalance(uuid, -entry.costMoney(), SOURCE, reason)) {
            missing = "exchange.no-money";
        }
        if (missing != null) {
            // Items or 온 went away while the DB step ran — undo it.
            core.stardust().grant(uuid, entry.costStardust(), SOURCE, reason + " 취소 환불");
            if (period != null) {
                executor.execute(() -> releaseUse(uuid, entry, period));
            }
            if (player != null) {
                messages.send(player, missing);
            }
            return;
        }
        removeCosts(player.getInventory(), entry);
        core.mailbox().deliverOrStore(uuid, entry.reward().clone(), SOURCE, reason);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.4f);
        messages.send(player, "exchange.done", Placeholder.component("item", entry.reward().effectiveName()),
                Placeholder.unparsed("amount", String.valueOf(entry.reward().getAmount())));
        open(player, page);
    }

    /** The message key for what the player lacks (온 or items), or null if they can pay. 별조각 is checked by the DB. */
    private String missingCost(Player player, Entry entry) {
        if (entry.costMoney() > 0 && !core.economyData().hasBalance(player.getUniqueId(), entry.costMoney())) {
            return "exchange.no-money";
        }
        PlayerInventory inventory = player.getInventory();
        for (Cost cost : mergedCosts(entry)) {
            int have = 0;
            for (int slot = 0; slot < PLAYER_STORAGE_SLOTS; slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (stack != null && stack.isSimilar(cost.sample())) {
                    have += stack.getAmount();
                }
            }
            if (have < cost.amount()) {
                return "exchange.no-items";
            }
        }
        return null;
    }

    private static void removeCosts(PlayerInventory inventory, Entry entry) {
        for (Cost cost : mergedCosts(entry)) {
            int remaining = cost.amount();
            for (int slot = 0; slot < PLAYER_STORAGE_SLOTS && remaining > 0; slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (stack != null && stack.isSimilar(cost.sample())) {
                    int take = Math.min(remaining, stack.getAmount());
                    remaining -= take;
                    stack.setAmount(stack.getAmount() - take);
                    inventory.setItem(slot, stack.getAmount() > 0 ? stack : null);
                }
            }
        }
    }

    /** No items, 온 or 별조각 asked and no daily/weekly limit — an endless free reward. */
    static boolean freeAndUnlimited(Entry entry) {
        return entry.costItems().isEmpty() && entry.costMoney() <= 0 && entry.costStardust() <= 0 && entry.limit() == ExchangeRepository.Limit.NONE;
    }

    /** One kind of cost item; {@code amount} may be more than a stack. */
    record Cost(ItemStack sample, int amount) {
    }

    /** Cost stacks with identical items summed. */
    static List<Cost> mergedCosts(Entry entry) {
        List<Cost> merged = new ArrayList<>();
        outer:
        for (ItemStack cost : entry.costItems()) {
            for (int i = 0; i < merged.size(); i++) {
                if (merged.get(i).sample().isSimilar(cost)) {
                    merged.set(i, new Cost(merged.get(i).sample(), merged.get(i).amount() + cost.getAmount()));
                    continue outer;
                }
            }
            merged.add(new Cost(cost, cost.getAmount()));
        }
        return merged;
    }

    private void releaseUse(UUID uuid, Entry entry, String period) {
        try {
            repository.releaseUse(uuid, entry.id(), period);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "교환 횟수 되돌리기 실패: " + uuid + " 교환소 #" + entry.id(), e);
        }
    }

    private void sendIfOnline(UUID uuid, String key) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            messages.send(player, key);
        }
    }

    private void runMain(Runnable task) {
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }
}
