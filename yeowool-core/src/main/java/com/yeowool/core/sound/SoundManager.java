package com.yeowool.core.sound;

import com.yeowool.core.api.service.SoundService;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Reads named sound cues from {@code config.yml}'s {@code sounds:} section,
 * e.g.:
 * <pre>{@code
 * sounds:
 *   success: { sound: ENTITY_EXPERIENCE_ORB_PICKUP, volume: 1.0, pitch: 1.2 }
 * }</pre>
 */
public final class SoundManager implements SoundService {

    private record SoundCue(Sound sound, float volume, float pitch) {
    }

    private final JavaPlugin plugin;
    private final Map<String, SoundCue> cues = new HashMap<>();
    private final Set<String> warnedMissingKeys = new HashSet<>();

    public SoundManager(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        cues.clear();
        warnedMissingKeys.clear();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("sounds");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection cueSection = section.getConfigurationSection(key);
            if (cueSection == null) {
                continue;
            }
            try {
                Sound sound = Sound.valueOf(cueSection.getString("sound", ""));
                float volume = (float) cueSection.getDouble("volume", 1.0);
                float pitch = (float) cueSection.getDouble("pitch", 1.0);
                cues.put(key, new SoundCue(sound, volume, pitch));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("잘못된 사운드 설정: sounds." + key);
            }
        }
    }

    @Override
    public void play(Player player, String key) {
        SoundCue cue = cues.get(key);
        if (cue == null) {
            if (warnedMissingKeys.add(key)) {
                plugin.getLogger().warning("등록되지 않은 사운드 키: " + key);
            }
            return;
        }
        player.playSound(player.getLocation(), cue.sound(), cue.volume(), cue.pitch());
    }
}
