package com.yeowool.life.dex;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.model.PlayerData;
import com.yeowool.core.api.service.MessageService;
import com.yeowool.life.fishing.FishRarity;
import com.yeowool.life.fishing.FishSpecies;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Pays dex completion milestones (e.g. 25/50/75/100% of a category) once per
 * player. Checked for every online player each minute and shortly after join;
 * a granted milestone is flagged in PlayerData statistics
 * ({@code dex.reward.<category>.<percent>}) so it is never paid twice, and a
 * player already past several milestones gets them all on the next check.
 */
public final class DexRewardService {

    public record Milestone(int percent, long money, List<String> commands) {
    }

    public record Progress(int owned, int total, int percent, int nextMilestone, int remaining) {
    }

    /** One dex category: config/flag key, Korean label, and per entry the statistic key(s) that count it as collected. */
    private record Category(String key, String label, List<List<String>> entryKeys) {
    }

    private static final String SOURCE = "YeowoolLife";

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private final MessageService messages;
    private final List<Milestone> milestones;
    private final List<Integer> milestonePercents;
    private final Supplier<List<FishRarity>> fishRarities;
    private final BooleanSupplier fishListReady;
    private final List<DexEntry> mining;
    private final List<DexEntry> hunting;
    private final List<DexEntry> farming;

    public DexRewardService(JavaPlugin plugin, YeowoolCoreAPI core, MessageService messages, List<Milestone> milestones,
                            Supplier<List<FishRarity>> fishRarities, BooleanSupplier fishListReady,
                            List<DexEntry> mining, List<DexEntry> hunting, List<DexEntry> farming) {
        this.plugin = plugin;
        this.core = core;
        this.messages = messages;
        this.milestones = milestones.stream().sorted((a, b) -> Integer.compare(a.percent(), b.percent())).toList();
        this.milestonePercents = this.milestones.stream().map(Milestone::percent).toList();
        this.fishRarities = fishRarities;
        this.fishListReady = fishListReady;
        this.mining = mining;
        this.hunting = hunting;
        this.farming = farming;
    }

    /** Main thread. */
    public void checkAll() {
        List<Category> categories = categories();
        for (Player player : Bukkit.getOnlinePlayers()) {
            check(player, categories);
        }
    }

    /** Main thread. */
    public void check(Player player) {
        check(player, categories());
    }

    /** Main thread: progress per category key (fishing/mining/hunting/farming); empty if the player's data isn't loaded. */
    public Map<String, Progress> progressFor(Player player) {
        Map<String, Progress> result = new LinkedHashMap<>();
        Optional<PlayerData> data = core.playerData().getIfLoaded(player.getUniqueId());
        if (data.isEmpty()) {
            return result;
        }
        for (Category category : categories()) {
            result.put(category.key(), progress(data.get(), category));
        }
        return result;
    }

    private void check(Player player, List<Category> categories) {
        Optional<PlayerData> loaded = core.playerData().getIfLoaded(player.getUniqueId());
        if (loaded.isEmpty()) {
            return;
        }
        PlayerData data = loaded.get();
        for (Category category : categories) {
            int percent = progress(data, category).percent();
            for (Milestone milestone : milestones) {
                if (percent < milestone.percent()) {
                    break;
                }
                String flag = "dex.reward." + category.key() + "." + milestone.percent();
                if (data.getStatistic(flag) > 0) {
                    continue;
                }
                data.addStatistic(flag, 1);
                grant(player, category, milestone);
            }
        }
    }

    private void grant(Player player, Category category, Milestone milestone) {
        if (milestone.money() <= 0 && milestone.commands().isEmpty()) {
            return; // milestone switched off in config (money: 0, commands: [])
        }
        String reason = "도감 보상 (" + category.label() + " " + milestone.percent() + "%)";
        if (milestone.money() > 0) {
            core.economyData().modifyBalance(player.getUniqueId(), milestone.money(), SOURCE, reason);
        }
        for (String command : milestone.commands()) {
            String resolved = command
                    .replace("{player}", player.getName())
                    .replace("{category}", category.label())
                    .replace("{category_key}", category.key());
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved);
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "도감 보상 명령어 실행 실패 (" + player.getName() + "): " + resolved, e);
            }
        }
        messages.send(player, milestone.money() > 0 ? "dex.reward" : "dex.reward-no-money",
                Placeholder.unparsed("category", category.label()),
                Placeholder.unparsed("percent", String.valueOf(milestone.percent())),
                Placeholder.unparsed("money", String.format("%,d", milestone.money())));
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
    }

    private Progress progress(PlayerData data, Category category) {
        int total = category.entryKeys().size();
        int owned = 0;
        for (List<String> keys : category.entryKeys()) {
            if (keys.stream().anyMatch(key -> data.getStatistic(key) > 0)) {
                owned++;
            }
        }
        int percent = DexRewardRules.percent(owned, total);
        int next = DexRewardRules.nextMilestone(percent, milestonePercents);
        return new Progress(owned, total, percent, next, next < 0 ? 0 : DexRewardRules.remainingFor(next, owned, total));
    }

    private List<Category> categories() {
        List<Category> categories = new ArrayList<>();
        // While CustomFishing hasn't finished registering its loot, the fish list is only our own
        // species - counting against that short list would pay high milestones early and forever.
        if (fishListReady.getAsBoolean()) {
            List<List<String>> fishKeys = new ArrayList<>();
            for (FishRarity rarity : fishRarities.get()) {
                for (FishSpecies species : rarity.species()) {
                    fishKeys.add(List.of(species.statisticKey(), species.legacyStatisticKey()));
                }
            }
            categories.add(new Category("fishing", "물고기", fishKeys));
        }
        categories.add(new Category("mining", "광물", entryKeys("dex.mining.", mining)));
        categories.add(new Category("hunting", "사냥", entryKeys("dex.hunting.", hunting)));
        categories.add(new Category("farming", "작물", entryKeys("dex.farming.", farming)));
        return categories;
    }

    private static List<List<String>> entryKeys(String prefix, List<DexEntry> entries) {
        return entries.stream().map(entry -> List.of(prefix + entry.id())).toList();
    }
}
