package com.yeowool.life.fishing;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.event.PlayerRepeatableActionEvent;
import com.yeowool.core.api.model.PlayerData;
import com.yeowool.core.api.service.MessageService;
import dev.lone.itemsadder.api.CustomStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Section 6.4 (YeowoolFishing): rolls a rarity tier then a specific fish
 * species within it on every catch, feeding both the plan's 일반/희귀/특수/
 * 전설 rarity concept and a proper "낚시 도감" ({@link FishCatalogGui}) of
 * named fish rather than just rarity counters. The vanilla-caught {@link
 * Item} entity's stack is replaced with the rolled species' own icon (its
 * {@link FishSpecies#customIconId()} when resolvable, else plain vanilla
 * {@link FishSpecies#material()}) so what actually lands in the inventory
 * matches the "caught" message and the /도감 entry, rather than leaving
 * whatever vanilla loot (raw cod, junk, etc.) the rod would've reeled in.
 *
 * <p>A tiered {@link FishRod} in the main hand and/or an equipped {@link
 * FishBait} (loaded via right-click — see {@link BaitEquipListener} — rather
 * than held physically) shift the rarity roll toward rarer tiers (see
 * {@link #rollRarity}) — every tier except the first-listed (lowest, e.g.
 * 일반) has its weight multiplied by each active bonus, stacked together.
 * One equipped bait is consumed per successful catch when present.
 *
 * <p>Every catch also rolls a size (cm, within {@link FishSpecies#minSizeCm}/
 * {@link FishSpecies#maxSizeCm}) — shown on the item and recorded as a
 * personal-best via {@link PlayerData#recordMaxStatistic} — and, if {@link
 * #competitionManager} is currently running the daily event, counted toward
 * it.
 *
 * <p>The reel-in timing minigame ({@link #onBite}/{@link #onCaught}) shows a
 * {@link BossBar} the moment a fish bites — see {@link FishMinigameConfig}
 * for exactly how it's timed and scored.
 */
public final class FishingListener implements Listener {

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private final MessageService messages;
    private final long xpPerCatch;
    private final List<FishRarity> rarities;
    private final int baseTotalWeight;
    private final Map<String, FishRod> rodsByItemId;
    private final Map<String, FishBait> baitsByItemId;
    private final FishWaitTime waitTime;
    private final FishMinigameConfig minigame;
    private final FishStarConfig star;
    private final FishingCompetitionManager competitionManager;
    private final Map<UUID, BiteSession> biteSessions = new HashMap<>();

    /** How long a resolved (missed/timed-out) bar lingers on screen before removing itself. */
    private static final long BAR_LINGER_MS = 1000L;

    public FishingListener(JavaPlugin plugin, YeowoolCoreAPI core, MessageService messages, long xpPerCatch, List<FishRarity> rarities,
                            Map<String, FishRod> rodsByItemId, Map<String, FishBait> baitsByItemId,
                            FishWaitTime waitTime, FishMinigameConfig minigame, FishStarConfig star, FishingCompetitionManager competitionManager) {
        this.plugin = plugin;
        this.core = core;
        this.messages = messages;
        this.xpPerCatch = xpPerCatch;
        this.rarities = rarities;
        this.baseTotalWeight = rarities.stream().mapToInt(FishRarity::weight).sum();
        this.rodsByItemId = rodsByItemId;
        this.baitsByItemId = baitsByItemId;
        this.waitTime = waitTime;
        this.minigame = minigame;
        this.star = star;
        this.competitionManager = competitionManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        switch (event.getState()) {
            case FISHING -> onCast(event);
            case BITE -> onBite(event);
            case CAUGHT_FISH -> onCaught(event);
            default -> {
            }
        }
    }

    /** Prevents an orphaned repeating task/BossBar if the player logs off mid-bite. */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        endBiteSessionImmediately(event.getPlayer());
    }

    /** Fishing requires a loaded bait (see {@link BaitEquipListener}) — no free-form fishing without one. */
    private void onCast(PlayerFishEvent event) {
        Player player = event.getPlayer();
        Optional<PlayerData> loaded = core.playerData().getIfLoaded(player.getUniqueId());
        FishBait bait = loaded.map(this::findEquippedBait).orElse(null);
        if (bait == null) {
            event.setCancelled(true);
            messages.send(player, "fishing.bait-required");
            return;
        }
        if (event.getHook() instanceof FishHook hook) {
            hook.setMinWaitTime(waitTime.minTicks());
            hook.setMaxWaitTime(waitTime.maxTicks());
        }
    }

    /**
     * A "sweet spot" window is placed randomly inside the bar, then a BossBar
     * tracks + displays it in real time until the catch. If the player never
     * reels in at all (vanilla just quietly resets to waiting-for-another-bite
     * with no event of its own), the repeating task detects that itself once
     * {@code total + BAR_LINGER_MS} has passed and cleans everything up —
     * otherwise the BossBar and its task would leak forever, since nothing
     * else would ever call {@link #endBiteSessionImmediately}.
     */
    private void onBite(PlayerFishEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        long total = minigame.totalDurationMs();
        long spotWidth = ThreadLocalRandom.current().nextLong(minigame.sweetSpotMinWidthMs(), minigame.sweetSpotMaxWidthMs() + 1);
        long latestStart = Math.max(0, total - spotWidth);
        long spotStart = latestStart <= 0 ? 0 : ThreadLocalRandom.current().nextLong(0, latestStart);
        long spotEnd = spotStart + spotWidth;
        long startMillis = System.currentTimeMillis();

        BossBar bar = Bukkit.createBossBar("낚시!", BarColor.RED, BarStyle.SOLID);
        bar.addPlayer(player);
        bar.setProgress(0.0);

        BukkitTask[] taskHolder = new BukkitTask[1];
        taskHolder[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long elapsed = System.currentTimeMillis() - startMillis;
            if (elapsed >= total + BAR_LINGER_MS) {
                if (biteSessions.remove(playerId) != null) {
                    taskHolder[0].cancel();
                    bar.removeAll();
                    messages.send(player, "fishing.missed");
                }
                return;
            }
            double progress = Math.min(1.0, elapsed / (double) total);
            bar.setProgress(progress);
            boolean inZone = elapsed >= spotStart && elapsed <= spotEnd;
            bar.setColor(inZone ? BarColor.GREEN : BarColor.RED);
            bar.setTitle(inZone ? "지금이다! 우클릭!" : (elapsed > spotEnd ? "놓쳤다... 그래도 우클릭은 하세요" : "기다리세요..."));
        }, 0L, 1L);

        biteSessions.put(playerId, new BiteSession(startMillis, spotStart, spotEnd, bar, taskHolder[0]));
    }

    private record BiteSession(long startMillis, long sweetStartMs, long sweetEndMs, BossBar bar, BukkitTask task) {
    }

    private enum Timing {
        PERFECT, GOOD, MISS, NONE
    }

    private void onCaught(PlayerFishEvent event) {
        Player player = event.getPlayer();
        BiteSession session = biteSessions.remove(player.getUniqueId());
        if (session != null) {
            session.task().cancel();
        }
        Timing timing = resolveTiming(session);

        if (timing == Timing.MISS && ThreadLocalRandom.current().nextDouble(100) < minigame.missFailChancePercent()) {
            if (event.getCaught() instanceof Item caughtItem) {
                caughtItem.remove();
            }
            messages.send(player, "fishing.missed");
            lingerThenRemove(session);
            return;
        }
        if (session != null) {
            session.bar().removeAll();
        }

        PlayerInventory inventory = player.getInventory();
        Optional<PlayerData> loaded = core.playerData().getIfLoaded(player.getUniqueId());

        FishRod rod = findRod(inventory.getItemInMainHand());
        FishBait bait = loaded.map(this::findEquippedBait).orElse(null);
        double rareMultiplier = (rod == null ? 1.0 : rod.rareWeightMultiplier())
                * (bait == null ? 1.0 : bait.rareWeightMultiplier())
                * timingRareBonus(timing);

        FishRarity rarity = rollRarity(rareMultiplier);
        if (rarity.species().isEmpty()) {
            return;
        }
        FishSpecies species = rarity.species().get(ThreadLocalRandom.current().nextInt(rarity.species().size()));
        double sizeCm = rollSizeCm(species, timingSizeBonusPercent(timing));

        boolean isStar = ThreadLocalRandom.current().nextDouble(100) < star.chancePercent();
        String rarityLabel = isStar ? "별" : rarity.name();
        NamedTextColor rarityColor = isStar ? NamedTextColor.GOLD : rarity.color();
        if (isStar) {
            sizeCm += sizeCm * (star.sizeBonusPercent() / 100.0);
        }

        if (event.getCaught() instanceof Item caughtItem) {
            caughtItem.setItemStack(resolveItemStack(species, sizeCm, rarityLabel, rarityColor, isStar));
        }

        core.landStats().addLandXp(player.getUniqueId(), xpPerCatch);
        boolean newRecord = false;
        long sizeMm = Math.round(sizeCm * 10);
        if (loaded.isPresent()) {
            var data = loaded.get();
            data.addStatistic("life.fishing.caught", 1);
            data.addStatistic("life.fishing.catalog." + rarity.name(), 1);
            data.addStatistic(species.statisticKey(), 1);
            newRecord = data.recordMaxStatistic(species.sizeRecordStatisticKey(), sizeMm);
            if (isStar) {
                data.addStatistic("life.fishing.catalog.별", 1);
            }
            if (bait != null) {
                consumeEquippedBait(data);
            }
        }

        competitionManager.recordCatch(player, species.name(), sizeCm);

        messages.send(player, "fishing.caught",
                Placeholder.unparsed("rarity", rarityLabel),
                Placeholder.unparsed("fish", species.name()),
                Placeholder.unparsed("size", String.format("%.1f", sizeCm)));
        if (isStar) {
            messages.send(player, "fishing.star-catch", Placeholder.unparsed("fish", species.name()));
        }
        if (timing == Timing.PERFECT) {
            messages.send(player, "fishing.perfect-timing");
        } else if (timing == Timing.GOOD) {
            messages.send(player, "fishing.good-timing");
        }
        if (newRecord) {
            messages.send(player, "fishing.new-record", Placeholder.unparsed("fish", species.name()));
        }

        Bukkit.getPluginManager().callEvent(new PlayerRepeatableActionEvent(player.getUniqueId(), "fishing"));
    }

    /** Used by {@link #onQuit} to clear everything right away — no need to linger for a player who's no longer looking at the bar. */
    private void endBiteSessionImmediately(Player player) {
        BiteSession session = biteSessions.remove(player.getUniqueId());
        if (session == null) {
            return;
        }
        session.task().cancel();
        session.bar().removeAll();
    }

    /** Freezes the bar in its current (failed) state for {@link #BAR_LINGER_MS} before removing it, per the requested "1초 뒤 사라짐" behavior. */
    private void lingerThenRemove(BiteSession session) {
        if (session == null) {
            return;
        }
        BossBar bar = session.bar();
        bar.setColor(BarColor.RED);
        bar.setTitle("놓쳤습니다...");
        Bukkit.getScheduler().runTaskLater(plugin, bar::removeAll, BAR_LINGER_MS / 50L);
    }

    /** {@code session} may be null (e.g. a treasure/junk catch that skipped BITE) — treated as no bonus, never a miss. */
    private Timing resolveTiming(BiteSession session) {
        if (session == null) {
            return Timing.NONE;
        }
        long elapsed = System.currentTimeMillis() - session.startMillis();
        if (elapsed >= session.sweetStartMs() && elapsed <= session.sweetEndMs()) {
            return Timing.PERFECT;
        }
        long buffer = minigame.goodBufferMs();
        if (elapsed >= session.sweetStartMs() - buffer && elapsed <= session.sweetEndMs() + buffer) {
            return Timing.GOOD;
        }
        return Timing.MISS;
    }

    private double timingRareBonus(Timing timing) {
        return switch (timing) {
            case PERFECT -> minigame.perfectRareBonus();
            case GOOD -> minigame.goodRareBonus();
            case MISS, NONE -> 1.0;
        };
    }

    private double timingSizeBonusPercent(Timing timing) {
        return switch (timing) {
            case PERFECT -> minigame.perfectSizeBonusPercent();
            case GOOD -> minigame.goodSizeBonusPercent();
            case MISS, NONE -> 0.0;
        };
    }

    private double rollSizeCm(FishSpecies species, double bonusPercent) {
        double size = species.minSizeCm() + (species.maxSizeCm() - species.minSizeCm()) * ThreadLocalRandom.current().nextDouble();
        if (bonusPercent > 0) {
            size += (species.maxSizeCm() - size) * (bonusPercent / 100.0);
        }
        return size;
    }

    private ItemStack resolveItemStack(FishSpecies species, double sizeCm, String rarityLabel, NamedTextColor rarityColor, boolean isStar) {
        ItemStack stack;
        if (species.customIconId() != null && Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) {
            CustomStack custom = CustomStack.getInstance(species.customIconId());
            stack = custom != null ? custom.getItemStack() : new ItemStack(species.material());
        } else {
            stack = new ItemStack(species.material());
        }

        ItemMeta meta = stack.getItemMeta();
        String displayName = isStar ? "★ " + species.name() + " ★" : species.name();
        meta.displayName(Component.text(displayName, rarityColor).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        if (!species.description().isBlank()) {
            lore.add(Component.text(species.description(), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.text("등급: " + rarityLabel, rarityColor).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("크기: " + String.format("%.1f", sizeCm) + "cm", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private FishRod findRod(ItemStack heldItem) {
        String itemId = customItemId(heldItem);
        return itemId == null ? null : rodsByItemId.get(itemId);
    }

    /** Reads the "loaded magazine" set by {@link BaitEquipListener} rather than checking any held item. */
    private FishBait findEquippedBait(PlayerData data) {
        if (data.getStatistic(BaitEquipListener.STAT_BAIT_COUNT) <= 0) {
            return null;
        }
        String equippedId = data.getSetting(BaitEquipListener.SETTING_BAIT_ID, "");
        return baitsByItemId.values().stream().filter(b -> b.id().equals(equippedId)).findFirst().orElse(null);
    }

    private void consumeEquippedBait(PlayerData data) {
        long remaining = data.addStatistic(BaitEquipListener.STAT_BAIT_COUNT, -1);
        if (remaining <= 0) {
            data.setSetting(BaitEquipListener.SETTING_BAIT_ID, "");
        }
    }

    private String customItemId(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) {
            return null;
        }
        CustomStack custom = CustomStack.byItemStack(stack);
        return custom == null ? null : custom.getNamespacedID();
    }

    private FishRarity rollRarity(double rareMultiplier) {
        if (rareMultiplier == 1.0) {
            return rollRarity(baseTotalWeight, 1.0);
        }
        double total = rarities.get(0).weight()
                + rarities.stream().skip(1).mapToDouble(r -> r.weight() * rareMultiplier).sum();
        return rollRarity(total, rareMultiplier);
    }

    private FishRarity rollRarity(double totalWeight, double rareMultiplier) {
        double roll = ThreadLocalRandom.current().nextDouble(totalWeight);
        double cumulative = 0;
        for (int i = 0; i < rarities.size(); i++) {
            FishRarity rarity = rarities.get(i);
            cumulative += i == 0 ? rarity.weight() : rarity.weight() * rareMultiplier;
            if (roll < cumulative) {
                return rarity;
            }
        }
        return rarities.get(rarities.size() - 1);
    }
}
