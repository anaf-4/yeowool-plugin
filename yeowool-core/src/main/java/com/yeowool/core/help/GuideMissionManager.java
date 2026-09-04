package com.yeowool.core.help;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Numbered onboarding missions shown in {@link GuideGui} via {@code /길라잡이
 * 추가|제거|수정|목록}. Unlike the static category descriptions
 * (config.yml's {@code guide.categories}), these are meant to be edited
 * often at runtime by staff, so they persist to their own {@value
 * #MISSIONS_FILE} in the plugin data folder rather than config.yml —
 * {@code FileConfiguration#save} rewrites the whole file and would wipe
 * config.yml's comments, the same reason {@code TitleManager} keeps
 * {@code /칭호생성} output in a separate {@code created-titles.yml}.
 */
public final class GuideMissionManager {

    private static final String MISSIONS_FILE = "guide-missions.yml";

    private final JavaPlugin plugin;
    private List<GuideMission> missions = List.of();

    public GuideMissionManager(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), MISSIONS_FILE);
        if (!file.exists()) {
            missions = List.of();
            return;
        }
        List<Map<?, ?>> raw = YamlConfiguration.loadConfiguration(file).getMapList("missions");
        List<GuideMission> parsed = new ArrayList<>();
        for (Map<?, ?> entry : raw) {
            try {
                int number = ((Number) entry.get("number")).intValue();
                String title = entry.get("title").toString();
                String action = entry.get("action").toString();
                parsed.add(new GuideMission(number, title, action));
            } catch (Exception e) {
                plugin.getLogger().warning(MISSIONS_FILE + " 항목이 잘못되었습니다: " + entry);
            }
        }
        parsed.sort(Comparator.comparingInt(GuideMission::number));
        missions = List.copyOf(parsed);
    }

    public List<GuideMission> all() {
        return missions;
    }

    public Optional<GuideMission> find(int number) {
        return missions.stream().filter(m -> m.number() == number).findFirst();
    }

    public enum AddResult { SUCCESS, ALREADY_EXISTS }

    public AddResult add(int number, String title, String action) {
        if (find(number).isPresent()) {
            return AddResult.ALREADY_EXISTS;
        }
        List<GuideMission> updated = new ArrayList<>(missions);
        updated.add(new GuideMission(number, title, action));
        persist(updated);
        return AddResult.SUCCESS;
    }

    public enum UpdateResult { SUCCESS, NOT_FOUND }

    public UpdateResult update(int number, String title, String action) {
        if (find(number).isEmpty()) {
            return UpdateResult.NOT_FOUND;
        }
        List<GuideMission> updated = missions.stream()
                .map(m -> m.number() == number ? new GuideMission(number, title, action) : m)
                .toList();
        persist(updated);
        return UpdateResult.SUCCESS;
    }

    public enum RemoveResult { SUCCESS, NOT_FOUND }

    public RemoveResult remove(int number) {
        if (find(number).isEmpty()) {
            return RemoveResult.NOT_FOUND;
        }
        persist(missions.stream().filter(m -> m.number() != number).toList());
        return RemoveResult.SUCCESS;
    }

    private void persist(List<GuideMission> updated) {
        List<GuideMission> sorted = new ArrayList<>(updated);
        sorted.sort(Comparator.comparingInt(GuideMission::number));
        missions = List.copyOf(sorted);

        YamlConfiguration config = new YamlConfiguration();
        List<Map<String, Object>> serialized = new ArrayList<>();
        for (GuideMission mission : missions) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("number", mission.number());
            entry.put("title", mission.title());
            entry.put("action", mission.action());
            serialized.add(entry);
        }
        config.set("missions", serialized);

        try {
            plugin.getDataFolder().mkdirs();
            config.save(new File(plugin.getDataFolder(), MISSIONS_FILE));
        } catch (IOException e) {
            plugin.getLogger().warning(MISSIONS_FILE + " 저장 실패: " + e.getMessage());
        }
    }
}
