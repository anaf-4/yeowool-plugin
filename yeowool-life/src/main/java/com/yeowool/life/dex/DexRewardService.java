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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

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

    /** One dex category: config/flag key, Korean label, and the statistic keys counted as its entries. */
    private record Category(String key, String label, List<String> statKeys) {
    }

    private static final String SOURCE = "YeowoolLife";

    private final YeowoolCoreAPI core;
    private final MessageService messages;
    private final List<Milestone> milestones;
    private final List<Integer> milestonePercents;
    private final Supplier<List<FishRarity>> fishRarities;
    private final List<DexEntry> mining;
    private final List<DexEntry> hunting;
    private final List<DexEntry> farming;

    public DexRewardService(YeowoolCoreAPI core, MessageService messages, List<Milestone> milestones,
                            Supplier<List<FishRarity>> fishRarities, List<DexEntry> mining, List<DexEntry> hunting, List<DexEntry> farming) {
        this.core = core;
        this.messages = messages;
        this.milestones = milestones.stream().sorted((a, b) -> Integer.compare(a.percent(), b.percent())).toList();
        this.milestonePercents = this.milestones.stream().map(Milestone::percent).toList();
        this.fishRarities = fishRarities;
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
        String reason = "도감 보상 (" + category.label() + " " + milestone.percent() + "%)";
        if (milestone.money() > 0) {
            core.economyData().modifyBalance(player.getUniqueId(), milestone.money(), SOURCE, reason);
        }
        for (String command : milestone.commands()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command
                    .replace("{player}", player.getName())
                    .replace("{category}", category.label())
                    .replace("{category_key}", category.key()));
        }
        messages.send(player, "dex.reward",
                Placeholder.unparsed("category", category.label()),
                Placeholder.unparsed("percent", String.valueOf(milestone.percent())),
                Placeholder.unparsed("money", String.format("%,d", milestone.money())));
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
    }

    private Progress progress(PlayerData data, Category category) {
        int total = category.statKeys().size();
        int owned = 0;
        for (String key : category.statKeys()) {
            if (data.getStatistic(key) > 0) {
                owned++;
            }
        }
        int percent = DexRewardRules.percent(owned, total);
        int next = DexRewardRules.nextMilestone(percent, milestonePercents);
        return new Progress(owned, total, percent, next, next < 0 ? 0 : DexRewardRules.remainingFor(next, owned, total));
    }

    private List<Category> categories() {
        List<String> fishKeys = new ArrayList<>();
        for (FishRarity rarity : fishRarities.get()) {
            for (FishSpecies species : rarity.species()) {
                fishKeys.add(species.statisticKey());
            }
        }
        return List.of(
                new Category("fishing", "물고기", fishKeys),
                new Category("mining", "광물", statKeys("dex.mining.", mining)),
                new Category("hunting", "사냥", statKeys("dex.hunting.", hunting)),
                new Category("farming", "작물", statKeys("dex.farming.", farming)));
    }

    private static List<String> statKeys(String prefix, List<DexEntry> entries) {
        return entries.stream().map(entry -> prefix + entry.id()).toList();
    }
}
