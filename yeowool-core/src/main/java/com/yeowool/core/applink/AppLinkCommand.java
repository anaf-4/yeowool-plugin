package com.yeowool.core.applink;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import javax.sql.DataSource;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.concurrent.ExecutorService;

/**
 * {@code /앱연동} — generates a one-time 6-digit code the companion app's
 * login screen accepts to link that app session to this player's account.
 * The code is stashed in {@code yw_player_settings} (key {@code app.link_code},
 * value {@code <code>|<expiresAtEpochMillis>}) rather than a dedicated table,
 * since it's a single ephemeral value per player and that table already
 * exists for exactly this shape of data.
 */
public final class AppLinkCommand implements CommandExecutor {

    private static final String SETTING_KEY = "app.link_code";
    private static final long CODE_TTL_MILLIS = 5 * 60 * 1000L;
    private final SecureRandom random = new SecureRandom();

    private final JavaPlugin plugin;
    private final DataSource dataSource;
    private final ExecutorService executor;

    public AppLinkCommand(JavaPlugin plugin, DataSource dataSource, ExecutorService executor) {
        this.plugin = plugin;
        this.dataSource = dataSource;
        this.executor = executor;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c플레이어만 사용할 수 있습니다.");
            return true;
        }
        String code = String.format("%06d", random.nextInt(1_000_000));
        String value = code + "|" + (System.currentTimeMillis() + CODE_TTL_MILLIS);
        String uuid = player.getUniqueId().toString();
        executor.execute(() -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement upsert = connection.prepareStatement(
                         "INSERT INTO yw_player_settings (uuid, setting_key, setting_value) VALUES (?, ?, ?) "
                                 + "ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value)")) {
                upsert.setString(1, uuid);
                upsert.setString(2, SETTING_KEY);
                upsert.setString(3, value);
                upsert.executeUpdate();
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    player.sendMessage("§a앱 연동 코드: §f§l" + code);
                    player.sendMessage("§7앱에서 5분 안에 이 코드를 입력해주세요.");
                });
            } catch (java.sql.SQLException e) {
                plugin.getLogger().severe("앱 연동 코드 발급 실패: " + e.getMessage());
                plugin.getServer().getScheduler().runTask(plugin,
                        () -> player.sendMessage("§c코드 발급 중 오류가 발생했습니다."));
            }
        });
        return true;
    }
}
