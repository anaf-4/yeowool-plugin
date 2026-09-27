package com.yeowool.life.mount;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Reads MCPets' own pet files so the mount list always matches what MCPets actually has installed. */
public final class MountCatalog {

    /** Ids and permission nodes end up in a console command — only plain tokens are accepted. */
    private static final Pattern ID = Pattern.compile("[A-Za-z0-9_\\-]+");
    private static final Pattern PERMISSION = Pattern.compile("[A-Za-z0-9_.\\-]+");

    private MountCatalog() {
    }

    /** Every {@code *.yml} under {@code petsDir}, including pack subfolders, in path order. Server-side only. */
    public static Map<String, MountDefinition> load(File petsDir) {
        Map<String, MountDefinition> mounts = new LinkedHashMap<>();
        if (!petsDir.isDirectory()) {
            return mounts;
        }
        List<Path> files;
        try (Stream<Path> walk = Files.walk(petsDir.toPath())) {
            files = walk.filter(path -> path.toString().endsWith(".yml")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        for (Path file : files) {
            parse(YamlConfiguration.loadConfiguration(file.toFile())).ifPresent(mount -> mounts.put(mount.id(),
                    // isItem() needs the server registry (not available in unit tests), so it's checked here, not in parse
                    mount.icon().isItem() ? mount : new MountDefinition(mount.id(), mount.permission(), mount.displayName(),
                            Material.SADDLE, mount.customModelData())));
        }
        return mounts;
    }

    /** Empty unless the pet is {@code Mountable: true} and has both {@code Id} and {@code Permission}. */
    public static Optional<MountDefinition> parse(YamlConfiguration yaml) {
        String id = yaml.getString("Id");
        String permission = yaml.getString("Permission");
        if (!yaml.getBoolean("Mountable", false) || id == null || permission == null
                || !ID.matcher(id).matches() || !PERMISSION.matcher(permission).matches()) {
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
