package com.yeowool.community.rankicon;

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
 * {@code /랭크아이콘 목록}은 누구나, {@code 부여}/{@code 제거}는
 * {@code yeowool.community.rankicon.manage} 권한이 있어야 사용할 수 있다.
 * 칭호/코스메틱과 달리 해금 개념이 없는 관리자 전용 지급 방식이지만, 대상은
 * {@link PlayerDataResolver} 덕분에 오프라인이어도(이 서버에 접속한 적이
 * 있으면) 지정할 수 있다 — 오프라인 대상은 탭리스트/네임태그에 바로
 * 반영할 방법이 없을 뿐, 다음 접속 시 자동으로 적용된다.
 */
public final class RankIconCommand implements CommandExecutor, TabCompleter {

    private static final String MANAGE_PERMISSION = "yeowool.community.rankicon.manage";

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private final RankIconManager rankIconManager;
    private final PlayerIdentityService identityService;
    private final MessageService messages;

    public RankIconCommand(JavaPlugin plugin, YeowoolCoreAPI core, RankIconManager rankIconManager, PlayerIdentityService identityService, MessageService messages) {
        this.plugin = plugin;
        this.core = core;
        this.rankIconManager = rankIconManager;
        this.identityService = identityService;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equals("목록")) {
            String list = rankIconManager.all().stream()
                    .map(RankIconDefinition::display)
                    .collect(Collectors.joining(", "));
            messages.send(sender, "rankicon.list", Placeholder.unparsed("icons", list.isBlank() ? "없음" : list));
            return true;
        }

        if (!sender.hasPermission(MANAGE_PERMISSION)) {
            messages.send(sender, "general.no-permission");
            return true;
        }

        if (args[0].equals("부여") && args.length == 3) {
            String iconId = args[2];
            PlayerDataResolver.resolve(plugin, core, args[1], (data, online) -> {
                if (rankIconManager.assign(data, iconId)) {
                    if (online != null) {
                        identityService.refresh(online);
                    }
                    messages.send(sender, "rankicon.assign-success",
                            Placeholder.unparsed("player", args[1]),
                            Placeholder.unparsed("icon", rankIconManager.find(iconId).map(RankIconDefinition::display).orElse(iconId)));
                } else {
                    messages.send(sender, "rankicon.unknown-icon");
                }
            }, () -> messages.send(sender, "rankicon.player-not-found"));
            return true;
        }

        if (args[0].equals("제거") && args.length == 2) {
            PlayerDataResolver.resolve(plugin, core, args[1], (data, online) -> {
                rankIconManager.clear(data);
                if (online != null) {
                    identityService.refresh(online);
                }
                messages.send(sender, "rankicon.clear-success", Placeholder.unparsed("player", args[1]));
            }, () -> messages.send(sender, "rankicon.player-not-found"));
            return true;
        }

        messages.send(sender, "rankicon.usage");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = sender.hasPermission(MANAGE_PERMISSION)
                    ? List.of("목록", "부여", "제거")
                    : List.of("목록");
            return TabCompletions.filter(options, args[0]);
        }
        if (args.length == 2 && (args[0].equals("부여") || args[0].equals("제거"))) {
            List<String> names = Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
            return TabCompletions.filter(names, args[1]);
        }
        if (args.length == 3 && args[0].equals("부여")) {
            return TabCompletions.filter(rankIconManager.all().stream().map(RankIconDefinition::id).toList(), args[2]);
        }
        return List.of();
    }
}
