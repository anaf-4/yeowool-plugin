package com.yeowool.discord.verify;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.regex.Pattern;

/**
 * {@code /인증코드 <4자리>} — consumes a code the bot's {@code /인증} slash
 * command generated, links this player's account in {@code yw_account_links},
 * and queues the configured verify role for the JS bot to actually grant
 * (only it can call the Discord API — see {@code DiscordSchemaInitializer}).
 */
public final class VerifyCodeCommand implements CommandExecutor {

    private static final Pattern CODE_PATTERN = Pattern.compile("\\d{4}");

    private final JavaPlugin plugin;
    private final VerifyRepository repository;
    private final ExecutorService executor;
    private final String verifyRoleId;

    public VerifyCodeCommand(JavaPlugin plugin, VerifyRepository repository, ExecutorService executor, String verifyRoleId) {
        this.plugin = plugin;
        this.repository = repository;
        this.executor = executor;
        this.verifyRoleId = verifyRoleId;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c플레이어만 사용할 수 있습니다.");
            return true;
        }
        if (args.length != 1 || !CODE_PATTERN.matcher(args[0]).matches()) {
            sender.sendMessage("§c사용법: /인증코드 <디스코드에서 /인증으로 받은 4자리 코드>");
            return true;
        }
        if (verifyRoleId.isBlank()) {
            sender.sendMessage("§c서버에 인증 역할이 설정되어 있지 않습니다. 관리자에게 문의하세요.");
            return true;
        }
        String code = args[0];
        executor.execute(() -> {
            try {
                var pending = repository.findValidCode(code);
                if (pending.isEmpty()) {
                    Bukkit.getScheduler().runTask(plugin, () ->
                            player.sendMessage("§c유효하지 않거나 만료된 코드입니다. 디스코드에서 /인증을 다시 실행해주세요."));
                    return;
                }
                var existingOwner = repository.uuidLinkedTo(pending.get().discordId());
                if (existingOwner.isPresent() && !existingOwner.get().equals(player.getUniqueId())) {
                    Bukkit.getScheduler().runTask(plugin, () ->
                            player.sendMessage("§c이 디스코드 계정은 이미 다른 플레이어와 연동되어 있습니다."));
                    return;
                }
                repository.link(player.getUniqueId(), pending.get().discordId(), player.getName());
                repository.markCodeUsed(code);
                repository.enqueueRoleGrant(pending.get().discordId(), verifyRoleId, player.getName());
                Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(
                        "§a디스코드 계정(§f" + pending.get().discordUsername() + "§a) 연동이 완료되었습니다! 잠시 후 역할이 지급됩니다."));
            } catch (SQLException e) {
                plugin.getLogger().severe("계정 연동 처리 실패: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage("§c처리 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요."));
            }
        });
        return true;
    }
}
