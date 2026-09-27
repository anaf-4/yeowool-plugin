package com.yeowool.life.mount;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Reads MCPets' own pet files so the mount list always matches what MCPets actually has installed. */
public final class MountCatalog {

    private MountCatalog() {
    }

    public static Map<String, MountDefinition> load(File petsDir) {
        Map<String, MountDefinition> mounts = new LinkedHashMap<>();
        File[] files = petsDir.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return mounts;
        }
        Arrays.sort(files, Comparator.comparing(File::getName));
        for (File file : files) {
            parse(YamlConfiguration.loadConfiguration(file)).ifPresent(mount -> mounts.put(mount.id(), mount));
        }
        return mounts;
    }

    /** Empty unless the pet is {@code Mountable: true} and has both {@code Id} and {@code Permission}. */
    public static Optional<MountDefinition> parse(YamlConfiguration yaml) {
        String id = yaml.getString("Id");
        String permission = yaml.getString("Permission");
        if (!yaml.getBoolean("Mountable", false) || id == null || id.isBlank() || permission == null || permission.isBlank()) {
            return Optional.empty();
        }
        String name = stripColors(yaml.getString("Icon.Name", id)).trim();
        Material icon = Material.matchMaterial(yaml.getString("Icon.Material", "SADDLE"));
        return Optional.of(new MountDefinition(id, permission, name.isEmpty() ? id : name,
                icon == null ? Material.SADDLE : icon, yaml.getInt("Icon.CustomModelData", 0)));
    }

    public static String stripColors(String text) {
        return text.replaceAll("(?i)[§&][0-9A-FK-ORX]", "");
    }
}
