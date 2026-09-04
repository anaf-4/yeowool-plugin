package com.yeowool.community.display;

import com.yeowool.community.chat.ChatChannelService;
import com.yeowool.community.profile.PlaytimeTracker;
import com.yeowool.community.title.TitleDefinition;
import com.yeowool.community.title.TitleManager;
import com.yeowool.core.api.YeowoolCoreAPI;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Shared {@code <token>} set for the scoreboard/tablist (see
 * {@link SidebarScoreboardTask} / {@link TablistTask}): the same on-balance,
 * land level, title, etc. data the PlaceholderAPI expansion exposes, reused
 * here so the scoreboard works even on a server without PlaceholderAPI
 * installed. If PlaceholderAPI *is* installed, raw {@code %...%} tokens in
 * the config are expanded first (see {@link #expand}), so an admin can mix
 * both styles freely.
 */
public final class PlaceholderTokens {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private PlaceholderTokens() {
    }

    public static TagResolver[] build(YeowoolCoreAPI core, TitleManager titleManager, PlayerIdentityService identityService,
                                       ChatChannelService channelService, Player player) {
        var data = core.playerData().getIfLoaded(player.getUniqueId());

        String on = data.map(d -> String.format("%,d", d.getOnBalance())).orElse("0");
        String bank = data.map(d -> String.format("%,d", d.getBankBalance())).orElse("0");
        String cash = data.map(d -> String.format("%,d", d.getCashBalance())).orElse("0");
        String playtime = data.map(d -> String.valueOf(d.getStatistic(PlaytimeTracker.STAT_KEY))).orElse("0");
        String title = data.flatMap(titleManager::equippedId)
                .flatMap(titleManager::find)
                .map(TitleDefinition::display)
                .orElse("");
        // core.landStats() throws if this player's PlayerData isn't cached
        // yet (it assumes the caller already knows it's loaded) — right
        // after PlayerJoinEvent that load can still be in flight, and this
        // renders on every scoreboard/tablist tick, so it must fall back
        // instead of letting that exception through.
        String landLevel = data.isPresent() ? String.valueOf(core.landStats().getLandLevel(player.getUniqueId())) : "0";
        String landXp = data.isPresent() ? String.valueOf(core.landStats().getLandXp(player.getUniqueId())) : "0";
        // yeowool-life의 자동줍기권/자동심기권 잔여 횟수 — PlayerData 통계라 어느 모듈에서든
        // 같은 방식으로 읽을 수 있음 (yeowool-life의 AutoFarmType.statKey()와 반드시 일치해야 함).
        String autoPickup = data.map(d -> formatAutoFarmCount(d.getStatistic("autofarm.pickup.remaining"))).orElse("0");
        String autoPlant = data.map(d -> formatAutoFarmCount(d.getStatistic("autofarm.plant.remaining"))).orElse("0");

        return new TagResolver[]{
                Placeholder.unparsed("on", on),
                Placeholder.unparsed("bank", bank),
                Placeholder.unparsed("cash", cash),
                Placeholder.unparsed("landlevel", landLevel),
                Placeholder.unparsed("landxp", landXp),
                Placeholder.unparsed("autopickup", autoPickup),
                Placeholder.unparsed("autoplant", autoPlant),
                Placeholder.unparsed("playtime", playtime),
                Placeholder.parsed("title", title),
                Placeholder.unparsed("online", String.valueOf(Bukkit.getOnlinePlayers().size())),
                Placeholder.unparsed("max_players", String.valueOf(Bukkit.getMaxPlayers())),
                Placeholder.unparsed("tps", String.format("%.1f", Bukkit.getServer().getTPS()[0])),
                Placeholder.unparsed("player", player.getName()),
                Placeholder.unparsed("channel", channelService.currentChannel(player).displayName()),
                // 랭크 아이콘 + 한글 닉네임(또는 실명) 조합 - 색상 코드가 포함된
                // Component라 unparsed가 아닌 component 리졸버로 넘겨야 그대로 렌더링됨.
                Placeholder.component("identity", identityService.nameFor(player))
        };
    }

    /** yeowool-life's {@code AutoFarmManager} stores "무제한" as a huge sentinel (half of {@code Long.MAX_VALUE}), not a literal -1. */
    private static String formatAutoFarmCount(long remaining) {
        if (remaining <= 0) {
            return "0";
        }
        return remaining >= Long.MAX_VALUE / 2 ? "무제한" : String.format("%,d", remaining);
    }

    /**
     * Expands any {@code %...%} PlaceholderAPI placeholder in {@code raw}
     * before it's parsed as MiniMessage, if PlaceholderAPI is installed;
     * otherwise returns {@code raw} unchanged.
     */
    public static String expandExternal(Player player, String raw) {
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return raw;
        }
        return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, raw);
    }

    public static net.kyori.adventure.text.Component render(YeowoolCoreAPI core, TitleManager titleManager, PlayerIdentityService identityService,
                                                              ChatChannelService channelService, Player player, String template) {
        String withExternalPlaceholders = expandExternal(player, template);
        return MINI_MESSAGE.deserialize(withExternalPlaceholders, build(core, titleManager, identityService, channelService, player));
    }
}
