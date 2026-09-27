package com.yeowool.life.mount;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MountCatalogTest {

    private static YamlConfiguration yaml(String text) {
        return YamlConfiguration.loadConfiguration(new StringReader(text));
    }

    @Test
    void parsesMountablePet() {
        Optional<MountDefinition> mount = MountCatalog.parse(yaml("""
                Id: AmethystDragon
                Permission: mcpets.amethystdragonpet
                Mountable: true
                Icon:
                  Name: §5Amethyst Dragon
                  Material: AMETHYST_SHARD
                  CustomModelData: 2000006
                """));
        assertEquals(Optional.of(new MountDefinition("AmethystDragon", "mcpets.amethystdragonpet", "Amethyst Dragon",
                Material.AMETHYST_SHARD, 2000006)), mount);
    }

    @Test
    void skipsNonMountableOrIncompletePets() {
        assertTrue(MountCatalog.parse(yaml("Id: Cat\nPermission: mcpets.cat\nMountable: false\n")).isEmpty());
        assertTrue(MountCatalog.parse(yaml("Id: Cat\nPermission: mcpets.cat\n")).isEmpty());
        assertTrue(MountCatalog.parse(yaml("Permission: mcpets.x\nMountable: true\n")).isEmpty());
        assertTrue(MountCatalog.parse(yaml("Id: X\nMountable: true\n")).isEmpty());
    }

    @Test
    void fallsBackForMissingIconFields() {
        MountDefinition mount = MountCatalog.parse(yaml("Id: Bike\nPermission: mcpets.bike\nMountable: true\nIcon:\n  Material: NOT_A_MATERIAL\n")).orElseThrow();
        assertEquals("Bike", mount.displayName());
        assertEquals(Material.SADDLE, mount.icon());
        assertEquals(0, mount.customModelData());
    }

    @Test
    void stripsLegacyColorCodes() {
        assertEquals("Hover-Ride Angle Red", MountCatalog.stripColors("§6§nHover-Ride§r§b Angle Red"));
        assertEquals("Plain", MountCatalog.stripColors("&aPlain"));
    }
}
