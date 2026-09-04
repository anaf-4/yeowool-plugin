package com.yeowool.community.title;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.model.PlayerData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Section 9.3/9.4 of the plugin plan (Title + Achievement, merged into one
 * mechanic here): titles are configured in {@code config.yml}, auto-unlock
 * against a statistic threshold, and carry no combat/economic power per the
 * plan's "전투력이나 경제적 우위를 제공하지 않는다".
 * <p>
 * {@code /칭호생성} lets an OP add further admin-only titles (no
 * requirement-stat, same as the config-defined "staff" example) at runtime.
 * Those go in a separate {@link #CREATED_TITLES_FILE} rather than being
 * written back into {@code config.yml} — {@code FileConfiguration#save}
 * rewrites the whole file and drops every comment, which would wipe out
 * config.yml's extensive documentation comments elsewhere in this module.
 */
public final class TitleManager {

    private static final String UNLOCKED_SETTING = "titles.unlocked";
    private static final String EQUIPPED_SETTING = "title.equipped";
    private static final String CREATED_TITLES_FILE = "created-titles.yml";

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private List<TitleDefinition> definitions = List.of();

    public TitleManager(JavaPlugin plugin, YeowoolCoreAPI core) {
        this.plugin = plugin;
        this.core = core;
        reload();
    }

    public void reload() {
        List<TitleDefinition> parsed = new ArrayList<>();
        parseInto(parsed, plugin.getConfig().getMapList("titles"), "config.yml의 titles");
        parseInto(parsed, loadCreatedTitles(), CREATED_TITLES_FILE);
        this.definitions = List.copyOf(parsed);
    }

    private void parseInto(List<TitleDefinition> target, List<Map<?, ?>> entries, String sourceLabel) {
        for (Map<?, ?> entry : entries) {
            try {
                String id = entry.get("id").toString();
                String display = entry.get("display").toString();
                String requirementStat = entry.containsKey("requirement-stat") ? entry.get("requirement-stat").toString() : null;
                long requirementValue = entry.containsKey("requirement-value") ? ((Number) entry.get("requirement-value")).longValue() : 0L;
                long rewardOn = entry.containsKey("reward-on") ? ((Number) entry.get("reward-on")).longValue() : 0L;
                target.add(new TitleDefinition(id, display, requirementStat, requirementValue, rewardOn));
            } catch (Exception e) {
                plugin.getLogger().warning(sourceLabel + " 항목이 잘못되었습니다: " + entry);
            }
        }
    }

    private List<Map<?, ?>> loadCreatedTitles() {
        File file = new File(plugin.getDataFolder(), CREATED_TITLES_FILE);
        if (!file.exists()) {
            return List.of();
        }
        return YamlConfiguration.loadConfiguration(file).getMapList("titles");
    }

    /**
     * {@code /칭호생성 <id> <표시>} — creates a new admin-only title (no
     * auto-unlock condition, just like {@code /칭호 지급}'s targets) and
     * persists it to {@link #CREATED_TITLES_FILE}. Returns false if the id
     * is already used by either a config-defined or a previously created
     * title.
     */
    public boolean createTitle(String id, String display) {
        if (find(id).isPresent()) {
            return false;
        }
        File file = new File(plugin.getDataFolder(), CREATED_TITLES_FILE);
        YamlConfiguration config = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();

        List<Map<?, ?>> existing = new ArrayList<>(config.getMapList("titles"));
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", id);
        entry.put("display", display);
        existing.add(entry);
        config.set("titles", existing);

        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning(CREATED_TITLES_FILE + " 저장 실패: " + e.getMessage());
            return false;
        }
        reload();
        return true;
    }

    public enum DeleteResult { SUCCESS, NOT_FOUND, CONFIG_DEFINED, FAILED }

    /**
     * {@code /칭호삭제} — only removes titles that live in
     * {@link #CREATED_TITLES_FILE} (i.e. ones {@link #createTitle} added).
     * A title defined in {@code config.yml} can't be deleted this way for
     * the same comment-preservation reason {@link #createTitle} doesn't
     * write there — an operator has to edit that file by hand instead.
     */
    public DeleteResult deleteTitle(String id) {
        if (find(id).isEmpty()) {
            return DeleteResult.NOT_FOUND;
        }
        File file = new File(plugin.getDataFolder(), CREATED_TITLES_FILE);
        if (!file.exists()) {
            return DeleteResult.CONFIG_DEFINED;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        List<Map<?, ?>> existing = new ArrayList<>(config.getMapList("titles"));
        boolean removed = existing.removeIf(entry -> id.equalsIgnoreCase(String.valueOf(entry.get("id"))));
        if (!removed) {
            return DeleteResult.CONFIG_DEFINED;
        }
        config.set("titles", existing);

        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning(CREATED_TITLES_FILE + " 저장 실패: " + e.getMessage());
            return DeleteResult.FAILED;
        }
        reload();
        return DeleteResult.SUCCESS;
    }

    public List<TitleDefinition> all() {
        return definitions;
    }

    public Optional<TitleDefinition> find(String id) {
        return definitions.stream().filter(t -> t.id().equalsIgnoreCase(id)).findFirst();
    }

    public Set<String> unlockedIds(PlayerData data) {
        String raw = data.getSetting(UNLOCKED_SETTING, "");
        if (raw.isBlank()) {
            return Set.of();
        }
        return new LinkedHashSet<>(Arrays.asList(raw.split(",")));
    }

    public boolean isUnlocked(PlayerData data, String id) {
        return unlockedIds(data).contains(id);
    }

    public Optional<String> equippedId(PlayerData data) {
        String equipped = data.getSetting(EQUIPPED_SETTING, "");
        return equipped.isBlank() ? Optional.empty() : Optional.of(equipped);
    }

    public boolean equip(Player player, String id) {
        PlayerData data = core.playerData().getOnline(player.getUniqueId());
        if (!isUnlocked(data, id)) {
            return false;
        }
        data.setSetting(EQUIPPED_SETTING, id);
        return true;
    }

    /**
     * Admin-only path for titles with no {@code requirement-stat} (see
     * {@link TitleDefinition#isAutoUnlock()}) — there's no threshold for
     * those to ever satisfy in {@link #checkUnlocks}, so this is the only
     * way they ever reach a player. Only unlocks; the target still equips
     * it themselves via {@code /칭호 장착}, same as an auto-unlocked one.
     * {@code data} works for an offline target too — see
     * {@link com.yeowool.core.util.PlayerDataResolver}.
     */
    public boolean grant(PlayerData data, String id) {
        var title = find(id);
        if (title.isEmpty()) {
            return false;
        }
        Set<String> unlocked = new LinkedHashSet<>(unlockedIds(data));
        unlocked.add(title.get().id());
        data.setSetting(UNLOCKED_SETTING, String.join(",", unlocked));
        return true;
    }

    /** Revokes a previously granted/unlocked title, unequipping it first if it's currently equipped. {@code data} works for an offline target too. */
    public boolean revoke(PlayerData data, String id) {
        Set<String> unlocked = new LinkedHashSet<>(unlockedIds(data));
        if (!unlocked.remove(id)) {
            return false;
        }
        data.setSetting(UNLOCKED_SETTING, String.join(",", unlocked));
        if (equippedId(data).map(id::equals).orElse(false)) {
            data.setSetting(EQUIPPED_SETTING, "");
        }
        return true;
    }

    private void unlock(Player player, TitleDefinition title) {
        PlayerData data = core.playerData().getOnline(player.getUniqueId());
        Set<String> unlocked = new LinkedHashSet<>(unlockedIds(data));
        if (!unlocked.add(title.id())) {
            return;
        }
        data.setSetting(UNLOCKED_SETTING, String.join(",", unlocked));
        if (title.rewardOn() > 0) {
            core.economyData().modifyBalance(player.getUniqueId(), title.rewardOn(), "YeowoolCommunity", "칭호 달성 보상: " + title.id());
        }
    }

    /**
     * Checks every auto-unlock title against this player's current stats and
     * unlocks any newly-met ones. Returns the ones unlocked just now, so the
     * caller (see {@link AchievementCheckTask}) can announce them.
     */
    public List<TitleDefinition> checkUnlocks(Player player) {
        var loaded = core.playerData().getIfLoaded(player.getUniqueId());
        if (loaded.isEmpty()) {
            // Called from a periodic task over every online player
            // (AchievementCheckTask) — a player whose data hasn't finished
            // loading yet just gets skipped this cycle instead of throwing
            // and cancelling the whole batch for everyone else.
            return List.of();
        }
        PlayerData data = loaded.get();
        Set<String> unlocked = unlockedIds(data);
        List<TitleDefinition> newlyUnlocked = new ArrayList<>();

        for (TitleDefinition title : definitions) {
            if (!title.isAutoUnlock() || unlocked.contains(title.id())) {
                continue;
            }
            long current = "land.level".equals(title.requirementStat())
                    ? core.landStats().getLandLevel(player.getUniqueId())
                    : data.getStatistic(title.requirementStat());
            if (current >= title.requirementValue()) {
                unlock(player, title);
                newlyUnlocked.add(title);
            }
        }
        return newlyUnlocked;
    }
}
