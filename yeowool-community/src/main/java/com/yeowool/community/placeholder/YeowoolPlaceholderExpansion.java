package com.yeowool.community.placeholder;

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
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public YeowoolPlaceholderExpansion(YeowoolCoreAPI core, TitleManager titleManager) {
        this.core = core;
        this.titleManager = titleManager;
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
}
