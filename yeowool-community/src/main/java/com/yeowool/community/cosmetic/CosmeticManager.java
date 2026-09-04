package com.yeowool.community.cosmetic;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.model.PlayerData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Purchase/equip bookkeeping for {@link CosmeticDefinition}s, following the
 * same unlocked-ids-as-a-setting pattern as {@code TitleManager} but keyed
 * per cosmetic type so a player can have one particle *and* one chat color
 * equipped at once.
 */
public final class CosmeticManager {

    private static final String UNLOCKED_SETTING = "cosmetics.unlocked";

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private List<CosmeticDefinition> definitions = List.of();

    public CosmeticManager(JavaPlugin plugin, YeowoolCoreAPI core) {
        this.plugin = plugin;
        this.core = core;
        reload();
    }

    public void reload() {
        List<CosmeticDefinition> parsed = new ArrayList<>();
        for (Map<?, ?> entry : plugin.getConfig().getMapList("cosmetics")) {
            try {
                String id = entry.get("id").toString();
                CosmeticDefinition.Type type = CosmeticDefinition.Type.valueOf(entry.get("type").toString().toUpperCase());
                String display = entry.get("display").toString();
                long price = ((Number) entry.get("price")).longValue();
                String value = entry.get("value").toString();
                parsed.add(new CosmeticDefinition(id, type, display, price, value));
            } catch (Exception e) {
                plugin.getLogger().warning("cosmetics 설정 항목이 잘못되었습니다: " + entry);
            }
        }
        this.definitions = List.copyOf(parsed);
    }

    public List<CosmeticDefinition> all() {
        return definitions;
    }

    public Optional<CosmeticDefinition> find(String id) {
        return definitions.stream().filter(c -> c.id().equalsIgnoreCase(id)).findFirst();
    }

    private String equippedSettingKey(CosmeticDefinition.Type type) {
        return "cosmetic.equipped." + type.name();
    }

    public Set<String> unlockedIds(PlayerData data) {
        String raw = data.getSetting(UNLOCKED_SETTING, "");
        return raw.isBlank() ? Set.of() : new LinkedHashSet<>(List.of(raw.split(",")));
    }

    public boolean isUnlocked(PlayerData data, String id) {
        return unlockedIds(data).contains(id);
    }

    public Optional<CosmeticDefinition> equipped(PlayerData data, CosmeticDefinition.Type type) {
        String id = data.getSetting(equippedSettingKey(type), "");
        return id.isBlank() ? Optional.empty() : find(id);
    }

    public boolean purchase(Player player, CosmeticDefinition cosmetic) {
        PlayerData data = core.playerData().getOnline(player.getUniqueId());
        if (isUnlocked(data, cosmetic.id())) {
            return false;
        }
        if (!core.economyData().hasBalance(player.getUniqueId(), cosmetic.price())) {
            return false;
        }
        core.economyData().modifyBalance(player.getUniqueId(), -cosmetic.price(), "YeowoolCommunity", "코스메틱 구매: " + cosmetic.id());

        Set<String> unlocked = new LinkedHashSet<>(unlockedIds(data));
        unlocked.add(cosmetic.id());
        data.setSetting(UNLOCKED_SETTING, String.join(",", unlocked));
        return true;
    }

    public void equip(Player player, CosmeticDefinition cosmetic) {
        core.playerData().getOnline(player.getUniqueId()).setSetting(equippedSettingKey(cosmetic.type()), cosmetic.id());
    }

    public void unequip(Player player, CosmeticDefinition.Type type) {
        core.playerData().getOnline(player.getUniqueId()).setSetting(equippedSettingKey(type), "");
    }
}
