package com.yeowool.community.title;

import com.yeowool.community.display.PlayerIdentityService;
import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
import com.yeowool.core.util.PlayerDataResolver;
import com.yeowool.core.util.TabCompletions;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.stream.Collectors;

/**
 * {@code /칭호 목록} lists unlocked titles, {@code /칭호 장착 <id>} equips
 * one, {@code /칭호 해제} clears the equipped title. {@code /칭호 지급}/
 * {@code 회수}는 {@code yeowool.community.title.manage} 권한 전용 — 통계
 * 자동 해금이 없는(관리자 전용) 칭호를 대상에게 부여/회수한다. 대상은
 * {@link PlayerDataResolver} 덕분에 오프라인이어도 지정할 수 있다.
 */
public final class TitleCommand implements CommandExecutor, TabCompleter {

    private static final String MANAGE_PERMISSION = "yeowool.community.title.manage";

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private final TitleManager titleManager;
    private final PlayerIdentityService identityService;
    private final MessageService messages;

    public TitleCommand(JavaPlugin plugin, YeowoolCoreAPI core, TitleManager titleManager, PlayerIdentityService identityService, MessageService messages) {
        this.plugin = plugin;
        this.core = core;
        this.titleManager = titleManager;
        this.identityService = identityService;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && (args[0].equals("지급") || args[0].equals("회수"))) {
            return handleManage(sender, args);
        }

        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return true;
        }

        if (args.length == 0 || args[0].equals("목록")) {
            var data = core.playerData().getOnline(player.getUniqueId());
            var unlocked = titleManager.unlockedIds(data);
            if (unlocked.isEmpty()) {
                messages.send(player, "title.none-unlocked");
                return true;
            }
            String list = unlocked.stream()
                    .map(id -> titleManager.find(id).map(TitleDefinition::display).orElse(id))
                    .collect(Collectors.joining(", "));
            messages.send(player, "title.list", Placeholder.unparsed("titles", list));
            return true;
        }

        if (args[0].equals("해제")) {
            core.playerData().getOnline(player.getUniqueId()).setSetting("title.equipped", "");
            identityService.refresh(player);
            messages.send(player, "title.unequipped");
            return true;
        }

        if (args[0].equals("장착") && args.length == 2) {
            if (titleManager.equip(player, args[1])) {
                identityService.refresh(player);
                messages.send(player, "title.equip-success",
                        Placeholder.unparsed("title", titleManager.find(args[1]).map(TitleDefinition::display).orElse(args[1])));
            } else {
                messages.send(player, "title.not-unlocked");
            }
            return true;
        }

        messages.send(player, "title.usage");
        return true;
    }

    private boolean handleManage(CommandSender sender, String[] args) {
        if (!sender.hasPermission(MANAGE_PERMISSION)) {
            messages.send(sender, "general.no-permission");
            return true;
        }
        if (args.length != 3) {
            messages.send(sender, "title.manage-usage");
            return true;
        }

        String id = args[2];
        String targetName = args[1];
        boolean grant = args[0].equals("지급");
        PlayerDataResolver.resolve(plugin, core, targetName, (data, online) -> {
            if (grant) {
                if (titleManager.grant(data, id)) {
                    messages.send(sender, "title.grant-success",
                            Placeholder.unparsed("player", targetName),
                            Placeholder.unparsed("title", titleManager.find(id).map(TitleDefinition::display).orElse(id)));
                } else {
                    messages.send(sender, "title.unknown-title");
                }
            } else {
                if (titleManager.revoke(data, id)) {
                    if (online != null) {
                        identityService.refresh(online);
                    }
                    messages.send(sender, "title.revoke-success", Placeholder.unparsed("player", targetName));
                } else {
                    messages.send(sender, "title.not-unlocked");
                }
            }
        }, () -> messages.send(sender, "title.player-not-found"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = sender.hasPermission(MANAGE_PERMISSION)
                    ? List.of("목록", "장착", "해제", "지급", "회수")
                    : List.of("목록", "장착", "해제");
            return TabCompletions.filter(options, args[0]);
        }
        if (args.length == 2 && args[0].equals("장착") && sender instanceof Player player) {
            var data = core.playerData().getOnline(player.getUniqueId());
            return TabCompletions.filter(List.copyOf(titleManager.unlockedIds(data)), args[1]);
        }
        if (args.length == 2 && (args[0].equals("지급") || args[0].equals("회수"))) {
            return TabCompletions.filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        }
        if (args.length == 3 && (args[0].equals("지급") || args[0].equals("회수"))) {
            return TabCompletions.filter(titleManager.all().stream().map(TitleDefinition::id).toList(), args[2]);
        }
        return List.of();
    }
}
