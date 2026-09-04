package com.yeowool.community.display;

import com.yeowool.community.chat.ChatChannelService;
import com.yeowool.community.title.TitleManager;
import com.yeowool.core.api.YeowoolCoreAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Sets the tab-list header/footer for every online player from config
 * templates, refreshed on the same cadence as {@link SidebarScoreboardTask}.
 */
public final class TablistTask extends BukkitRunnable {

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private final TitleManager titleManager;
    private final PlayerIdentityService identityService;
    private final ChatChannelService channelService;
    private final String header;
    private final String footer;
    private final String serverName;

    public TablistTask(JavaPlugin plugin, YeowoolCoreAPI core, TitleManager titleManager, PlayerIdentityService identityService,
                        ChatChannelService channelService, String header, String footer, String serverName) {
        this.plugin = plugin;
        this.core = core;
        this.titleManager = titleManager;
        this.identityService = identityService;
        this.channelService = channelService;
        this.header = header;
        this.footer = footer;
        this.serverName = serverName;
    }

    @Override
    public void run() {
        // <servername>은 이 서버 인스턴스 고정값이라 플레이어별 토큰이 아니므로
        // PlaceholderTokens에 넣지 않고 미리 문자열로 치환해둔다.
        String headerTemplate = header.replace("<servername>", serverName);
        String footerTemplate = footer.replace("<servername>", serverName);
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                player.sendPlayerListHeaderAndFooter(
                        PlaceholderTokens.render(core, titleManager, identityService, channelService, player, headerTemplate),
                        PlaceholderTokens.render(core, titleManager, identityService, channelService, player, footerTemplate)
                );
            } catch (Exception e) {
                // Same reasoning as SidebarScoreboardTask: one player's bad
                // render must not stop the tab list from updating for
                // everyone else on the same tick.
                plugin.getLogger().warning("탭리스트 갱신 실패 (" + player.getName() + "): " + e.getMessage());
            }
        }
    }
}
