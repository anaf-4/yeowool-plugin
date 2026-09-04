package com.yeowool.admin.raffle;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Runs one {@code /추첨} draw end-to-end: for each of {@code winnerCount}
 * prizes, flickers random candidate names as a center-screen title for
 * {@link #CYCLE_DURATION_TICKS} (the "추첨 진행중..." suspense beat), then
 * reveals the actual winner (already chosen before the flicker started, so
 * the reveal is just cosmetic suspense, not a live re-roll) and moves on to
 * the next prize. Winners within a single draw are always distinct — the
 * remaining pool shrinks by one each pick — but that's separate from
 * {@link RaffleManager}'s cross-draw repeat-winner weight decay.
 */
public final class RaffleSession {

    private static final long CYCLE_INTERVAL_TICKS = 3L;
    private static final long CYCLE_DURATION_TICKS = 50L;
    private static final long REVEAL_DURATION_TICKS = 40L;
    private static final Title.Times CYCLE_TIMES = Title.Times.times(Duration.ZERO, Duration.ofMillis(200), Duration.ZERO);
    private static final Title.Times REVEAL_TIMES = Title.Times.times(Duration.ofMillis(100), Duration.ofSeconds(2), Duration.ofMillis(300));

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private final MessageService messages;
    private final RaffleManager manager;
    private final String itemId;
    private final ItemStack prizeTemplate;
    private final String prizeDisplayName;
    private final List<UUID> eligiblePool;
    private final int winnerCount;

    private final List<UUID> pickedThisDraw = new ArrayList<>();
    private final List<String> winnerNames = new ArrayList<>();

    public RaffleSession(JavaPlugin plugin, YeowoolCoreAPI core, MessageService messages, RaffleManager manager,
                          String itemId, ItemStack prizeTemplate, String prizeDisplayName, List<UUID> eligiblePool, int winnerCount) {
        this.plugin = plugin;
        this.core = core;
        this.messages = messages;
        this.manager = manager;
        this.itemId = itemId;
        this.prizeTemplate = prizeTemplate;
        this.prizeDisplayName = prizeDisplayName;
        this.eligiblePool = eligiblePool;
        this.winnerCount = winnerCount;
    }

    public void start() {
        broadcastMessage("raffle.broadcast-start",
                Placeholder.unparsed("item", prizeDisplayName), Placeholder.unparsed("count", String.valueOf(winnerCount)));
        drawNext();
    }

    private void drawNext() {
        List<UUID> remaining = eligiblePool.stream().filter(uuid -> !pickedThisDraw.contains(uuid)).toList();
        if (remaining.isEmpty()) {
            finish();
            return;
        }
        UUID winnerId = weightedPick(remaining);

        // Only the cosmetic flicker needs an online name to cycle through - the winner is
        // already chosen and mailbox delivery works fine offline, so a remaining pool that's
        // entirely offline right now must still reveal a winner, not end the draw short of
        // winnerCount just because there's nothing to flicker.
        List<String> flickerNames = remaining.stream()
                .map(Bukkit::getPlayer).filter(Objects::nonNull).map(Player::getName).toList();
        if (flickerNames.isEmpty()) {
            reveal(winnerId);
            return;
        }

        new BukkitRunnable() {
            long elapsed = 0L;

            @Override
            public void run() {
                if (elapsed >= CYCLE_DURATION_TICKS) {
                    cancel();
                    reveal(winnerId);
                    return;
                }
                String flicker = flickerNames.get(ThreadLocalRandom.current().nextInt(flickerNames.size()));
                broadcastTitle(Component.text("추첨 진행중...", NamedTextColor.GOLD, TextDecoration.BOLD),
                        Component.text(flicker, NamedTextColor.YELLOW), CYCLE_TIMES);
                elapsed += CYCLE_INTERVAL_TICKS;
            }
        }.runTaskTimer(plugin, 0L, CYCLE_INTERVAL_TICKS);
    }

    private void reveal(UUID winnerId) {
        Player winnerPlayer = Bukkit.getPlayer(winnerId);
        String winnerName = winnerPlayer != null ? winnerPlayer.getName() : "?";

        pickedThisDraw.add(winnerId);
        winnerNames.add(winnerName);
        manager.recordWin(itemId, winnerId);
        core.mailbox().deliverOrStore(winnerId, prizeTemplate.clone(), "YeowoolAdmin",
                "추첨 당첨 (" + prizeDisplayName + ")");

        broadcastTitle(Component.text(winnerName + "님 당첨!", NamedTextColor.GREEN, TextDecoration.BOLD),
                Component.text("(" + pickedThisDraw.size() + " / " + winnerCount + ")", NamedTextColor.GRAY), REVEAL_TIMES);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        }
        broadcastMessage("raffle.broadcast-winner",
                Placeholder.unparsed("winner", winnerName), Placeholder.unparsed("item", prizeDisplayName));

        if (pickedThisDraw.size() >= winnerCount) {
            Bukkit.getScheduler().runTaskLater(plugin, this::finish, REVEAL_DURATION_TICKS);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, this::drawNext, REVEAL_DURATION_TICKS);
        }
    }

    private void finish() {
        broadcastMessage("raffle.broadcast-finish",
                Placeholder.unparsed("item", prizeDisplayName), Placeholder.unparsed("winners", String.join(", ", winnerNames)));
    }

    private UUID weightedPick(List<UUID> pool) {
        Map<UUID, Double> weights = new LinkedHashMap<>();
        double total = 0;
        for (UUID uuid : pool) {
            double weight = manager.weightFor(itemId, uuid);
            weights.put(uuid, weight);
            total += weight;
        }
        double roll = ThreadLocalRandom.current().nextDouble(total);
        double cumulative = 0;
        for (Map.Entry<UUID, Double> entry : weights.entrySet()) {
            cumulative += entry.getValue();
            if (roll < cumulative) {
                return entry.getKey();
            }
        }
        return pool.get(pool.size() - 1);
    }

    private void broadcastTitle(Component main, Component sub, Title.Times times) {
        Title title = Title.title(main, sub, times);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showTitle(title);
        }
    }

    private void broadcastMessage(String key, net.kyori.adventure.text.minimessage.tag.resolver.TagResolver... placeholders) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            messages.send(player, key, placeholders);
        }
    }
}
