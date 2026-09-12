package com.yeowool.community.placeholder;

import com.yeowool.community.party.PartyManager;
import com.yeowool.community.profile.PlaytimeTracker;
import com.yeowool.community.title.TitleDefinition;
import com.yeowool.community.title.TitleManager;
import com.yeowool.core.api.YeowoolCoreAPI;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

/**
 * Registers {@code %yeowool_...%} placeholders (only when PlaceholderAPI is
 * installed — see {@code YeowoolCommunity}). Economy/land placeholders only
 * resolve for a player whose data is currently cached (online, or recently
 * online) since reading it for anyone else would mean a blocking DB call,
 * which placeholder resolution can't afford.
 */
public final class YeowoolPlaceholderExpansion extends PlaceholderExpansion {

    private final YeowoolCoreAPI core;
    private final TitleManager titleManager;
    private final PartyManager partyManager;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public YeowoolPlaceholderExpansion(YeowoolCoreAPI core, TitleManager titleManager, PartyManager partyManager) {
        this.core = core;
        this.titleManager = titleManager;
        this.partyManager = partyManager;
    }

    @Override
    public String getIdentifier() {
        return "yeowool";
    }

    @Override
    public String getAuthor() {
        return "Yeowool";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null) {
            return switch (params) {
                case "online" -> String.valueOf(Bukkit.getOnlinePlayers().size());
                case "max_players" -> String.valueOf(Bukkit.getMaxPlayers());
                case "tps" -> String.format("%.2f", Bukkit.getServer().getTPS()[0]);
                default -> null;
            };
        }

        if (params.startsWith("party_slot_")) {
            return partySlotPlaceholder(player, params);
        }
        if (params.equals("party_total")) {
            return String.valueOf(partyManager.hudSlots(player.getUniqueId()).size());
        }

        return switch (params) {
            case "online" -> String.valueOf(Bukkit.getOnlinePlayers().size());
            case "max_players" -> String.valueOf(Bukkit.getMaxPlayers());
            case "tps" -> String.format("%.2f", Bukkit.getServer().getTPS()[0]);
            case "on" -> core.playerData().getIfLoaded(player.getUniqueId())
                    .map(data -> String.format("%,d", data.getOnBalance())).orElse("0");
            case "bank" -> core.playerData().getIfLoaded(player.getUniqueId())
                    .map(data -> String.format("%,d", data.getBankBalance())).orElse("0");
            case "landlevel" -> String.valueOf(core.landStats().getLandLevel(player.getUniqueId()));
            case "landxp" -> String.valueOf(core.landStats().getLandXp(player.getUniqueId()));
            case "playtime" -> core.playerData().getIfLoaded(player.getUniqueId())
                    .map(data -> String.valueOf(data.getStatistic(PlaytimeTracker.STAT_KEY))).orElse("0");
            case "title" -> core.playerData().getIfLoaded(player.getUniqueId())
                    .flatMap(titleManager::equippedId)
                    .flatMap(titleManager::find)
                    .map(TitleDefinition::display)
                    .map(display -> PlainTextComponentSerializer.plainText().serialize(miniMessage.deserialize(display)))
                    .orElse("");
            case "title_formatted" -> core.playerData().getIfLoaded(player.getUniqueId())
                    .flatMap(titleManager::equippedId)
                    .flatMap(titleManager::find)
                    .map(TitleDefinition::display)
                    .orElse("");
            default -> null;
        };
    }

    /**
     * {@code %yeowool_party_slot_<1-4>_<name|health|maxhealth|leader|low>%} — reads
     * {@link PartyManager}'s ~1s-fresh HUD cache (see its class doc), never
     * blocks. An out-of-range slot (party smaller than 4, or player not in a
     * party at all) resolves to {@code ""}/{@code "0"} so a HUD layout can
     * safely reference all 4 slots unconditionally.
     */
    private String partySlotPlaceholder(OfflinePlayer player, String params) {
        String[] parts = params.split("_", 4);
        if (parts.length != 4) {
            return null;
        }
        int slotIndex;
        try {
            slotIndex = Integer.parseInt(parts[2]) - 1;
        } catch (NumberFormatException e) {
            return null;
        }
        var slots = partyManager.hudSlots(player.getUniqueId());
        if (slotIndex < 0 || slotIndex >= slots.size()) {
            return switch (parts[3]) {
                case "name" -> "";
                case "leader", "low" -> "false";
                default -> "0";
            };
        }
        var slot = slots.get(slotIndex);
        return switch (parts[3]) {
            case "name" -> slot.name();
            case "health" -> String.valueOf(Math.round(slot.health()));
            case "maxhealth" -> String.valueOf(Math.round(slot.maxHealth()));
            case "low" -> String.valueOf(slot.maxHealth() > 0 && slot.health() / slot.maxHealth() < 0.25);
            case "leader" -> String.valueOf(slot.leader());
            default -> null;
        };
    }
}
