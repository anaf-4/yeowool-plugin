package com.yeowool.core.util;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Predicate;

/**
 * {@link JavaPlugin#saveDefaultConfig()} / {@code saveResource(name, false)}
 * only ever write the bundled default file when nothing exists on disk yet
 * — once a server has its own {@code config.yml}/{@code messages.yml}, new
 * top-level keys added in a later plugin update never reach it, so a fresh
 * feature's settings/messages silently show up as "missing message: ..." or
 * empty lists until someone copies the new keys in by hand. This adds only
 * the keys the live file is missing (by dotted path), leaving every existing
 * value — including ones an operator hand-edited — untouched.
 */
public final class ConfigMerger {

    private ConfigMerger() {
    }

    /** Merges missing default keys into {@code plugin.getDataFolder()/resourceName}, writing the bundled file as-is if it doesn't exist yet. */
    public static void mergeDefaults(JavaPlugin plugin, String resourceName) {
        File file = new File(plugin.getDataFolder(), resourceName);
        if (!file.exists()) {
            plugin.saveResource(resourceName, false);
            return;
        }
        if (plugin.getResource(resourceName) == null) {
            return;
        }

        YamlConfiguration existing = YamlConfiguration.loadConfiguration(file);
        YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                new InputStreamReader(plugin.getResource(resourceName), StandardCharsets.UTF_8));

        boolean changed = false;
        for (String key : defaults.getKeys(true)) {
            if (!existing.contains(key)) {
                existing.set(key, defaults.get(key));
                changed = true;
            }
        }

        if (changed) {
            try {
                existing.save(file);
            } catch (IOException e) {
                plugin.getLogger().warning(resourceName + "에 새로운 기본값을 병합하지 못했습니다: " + e.getMessage());
            }
        }
    }

    /**
     * {@link #mergeDefaults} only ever adds keys that are completely
     * missing — it never touches a key that already exists, even if this
     * plugin's shipped default *value* for it changed in a later update
     * (e.g. a template string that got reworded). That's deliberate: an
     * operator's hand-edited value must never be silently overwritten. But
     * it also means a server still sitting on the *old* shipped default
     * never picks up the new one on its own.
     * <p>
     * This runs {@code migration} against the live file (a no-op if the
     * file doesn't exist yet — {@link #mergeDefaults} will lay down the
     * current defaults for a fresh install) and saves only if it reports a
     * change. Pass a predicate built from {@link #migrateScalarIfDefault}/
     * {@link #migrateListElementIfPresent} calls — each only changes a
     * value that still exactly matches the *specific old default* it
     * names, so a value the operator already customized (including to the
     * new default themselves) is never touched.
     */
    public static void migrate(JavaPlugin plugin, String resourceName, Predicate<YamlConfiguration> migration) {
        File file = new File(plugin.getDataFolder(), resourceName);
        if (!file.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        if (migration.test(config)) {
            try {
                config.save(file);
            } catch (IOException e) {
                plugin.getLogger().warning(resourceName + " 마이그레이션 저장 실패: " + e.getMessage());
            }
        }
    }

    /** For use inside a {@link #migrate} predicate: sets {@code path} to {@code newValue} only if it's currently exactly {@code oldValue}. Returns whether it changed anything. */
    public static boolean migrateScalarIfDefault(YamlConfiguration config, String path, String oldValue, String newValue) {
        if (oldValue.equals(config.getString(path))) {
            config.set(path, newValue);
            return true;
        }
        return false;
    }

    /** For use inside a {@link #migrate} predicate: replaces one element of the string list at {@code path} if {@code oldElement} is present in it, leaving the rest of the list untouched. Returns whether it changed anything. */
    public static boolean migrateListElementIfPresent(YamlConfiguration config, String path, String oldElement, String newElement) {
        List<String> list = config.getStringList(path);
        int index = list.indexOf(oldElement);
        if (index < 0) {
            return false;
        }
        list.set(index, newElement);
        config.set(path, list);
        return true;
    }

    /** For use inside a {@link #migrate} predicate: removes one element of the string list at {@code path} if present, leaving the rest of the list untouched. Returns whether it changed anything. */
    public static boolean removeListElementIfPresent(YamlConfiguration config, String path, String element) {
        List<String> list = config.getStringList(path);
        if (!list.remove(element)) {
            return false;
        }
        config.set(path, list);
        return true;
    }

    /**
     * For use inside a {@link #migrate} predicate: appends {@code newElement}
     * to the string list at {@code path} unless some element already
     * contains {@code marker} — for adding a brand-new line (e.g. a new
     * scoreboard row) to a list an operator may have already reordered or
     * customized, without duplicating it if they already added their own
     * version (or a previous run of this migration already did). Returns
     * whether it changed anything.
     */
    public static boolean appendListElementIfMissing(YamlConfiguration config, String path, String marker, String newElement) {
        List<String> list = config.getStringList(path);
        if (list.stream().anyMatch(line -> line.contains(marker))) {
            return false;
        }
        list.add(newElement);
        config.set(path, list);
        return true;
    }
}
