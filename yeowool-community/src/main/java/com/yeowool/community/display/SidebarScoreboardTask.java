package com.yeowool.community.display;

import com.yeowool.community.chat.ChatChannelService;
import com.yeowool.community.title.TitleManager;
import com.yeowool.core.api.YeowoolCoreAPI;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Renders a per-player sidebar scoreboard from config-driven MiniMessage
 * line templates (see {@link PlaceholderTokens}). The objective is only
 * registered once per player and then updated in place — only the title
 * and lines whose rendered {@link Component} actually changed since the
 * last tick get re-sent (Adventure {@code Component}s compare structurally,
 * so this is a cheap check), instead of unregistering and rebuilding the
 * whole scoreboard every ~1s regardless of whether anything changed. Still
 * uses {@link org.bukkit.scoreboard.Score#customName} so lines aren't
 * limited to the old 16/40-character legacy format.
 */
public final class SidebarScoreboardTask extends BukkitRunnable {

    private static final String OBJECTIVE_NAME = "yeowool_side";

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private final TitleManager titleManager;
    private final PlayerIdentityService identityService;
    private final ChatChannelService channelService;
    private final String title;
    private final List<String> lineTemplates;
    private final Map<UUID, Component> lastTitle = new ConcurrentHashMap<>();
    private final Map<UUID, Component[]> lastLines = new ConcurrentHashMap<>();

    public SidebarScoreboardTask(JavaPlugin plugin, YeowoolCoreAPI core, TitleManager titleManager, PlayerIdentityService identityService,
                                  ChatChannelService channelService, String title, List<String> lineTemplates) {
        this.plugin = plugin;
        this.core = core;
        this.titleManager = titleManager;
        this.identityService = identityService;
        this.channelService = channelService;
        this.title = title;
        this.lineTemplates = lineTemplates;
    }

    @Override
    public void run() {
        var online = Bukkit.getOnlinePlayers();
        var onlineIds = online.stream().map(Player::getUniqueId).toList();
        lastTitle.keySet().retainAll(onlineIds);
        lastLines.keySet().retainAll(onlineIds);

        for (Player player : online) {
            try {
                update(player);
            } catch (Exception e) {
                // A bad render for one player (e.g. a stale/misconfigured
                // rank icon or title) must never take the sidebar down for
                // everyone else on the same tick.
                plugin.getLogger().warning("사이드바 스코어보드 갱신 실패 (" + player.getName() + "): " + e.getMessage());
            }
        }
    }

    private void update(Player player) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) {
            return;
        }

        Scoreboard scoreboard = player.getScoreboard();
        if (scoreboard == manager.getMainScoreboard()) {
            scoreboard = manager.getNewScoreboard();
            player.setScoreboard(scoreboard);
        }

        UUID uuid = player.getUniqueId();
        Component titleComponent = PlaceholderTokens.render(core, titleManager, identityService, channelService, player, title);
        Objective objective = scoreboard.getObjective(OBJECTIVE_NAME);
        if (objective == null) {
            // A freshly (re)created objective starts with no scores at all,
            // so any cached "previous" lines from an earlier objective on
            // this player (e.g. their scoreboard got reset externally) must
            // not be trusted — force every line to render this pass.
            objective = scoreboard.registerNewObjective(OBJECTIVE_NAME, org.bukkit.scoreboard.Criteria.DUMMY, titleComponent);
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
            lastTitle.put(uuid, titleComponent);
            lastLines.remove(uuid);
        } else if (!titleComponent.equals(lastTitle.get(uuid))) {
            objective.displayName(titleComponent);
            lastTitle.put(uuid, titleComponent);
        }

        Component[] rendered = new Component[lineTemplates.size()];
        for (int i = 0; i < lineTemplates.size(); i++) {
            rendered[i] = PlaceholderTokens.render(core, titleManager, identityService, channelService, player, lineTemplates.get(i));
        }
        Component[] previous = lastLines.get(uuid);

        int score = lineTemplates.size();
        for (int i = 0; i < rendered.length; i++) {
            if (previous == null || !rendered[i].equals(previous[i])) {
                var line = objective.getScore(lineEntry(i));
                line.customName(rendered[i]);
                line.numberFormat(NumberFormat.blank());
                line.setScore(score);
            }
            score--;
        }
        lastLines.put(uuid, rendered);
    }

    /** A unique, invisible fake "player name" per line so scores don't collide. */
    private String lineEntry(int index) {
        return org.bukkit.ChatColor.values()[index % org.bukkit.ChatColor.values().length].toString() + "§r".repeat(index / org.bukkit.ChatColor.values().length);
    }
}
