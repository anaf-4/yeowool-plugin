package com.yeowool.core.help;

import com.yeowool.core.util.TabCompletions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.List;

/**
 * {@code /길라잡이} with no arguments opens {@link GuideGui} for anyone.
 * {@code 추가|제거|수정|목록} manage {@link GuideMissionManager}'s numbered
 * onboarding missions and require {@value #MANAGE_PERMISSION} — same split
 * as {@code /칭호}'s player subcommands vs. its permission-gated 지급/회수,
 * so the plugin.yml entry itself carries no blanket permission.
 */
public final class GuideCommand implements CommandExecutor, TabCompleter {

    private static final String MANAGE_PERMISSION = "yeowool.core.guide.manage";

    private final JavaPlugin plugin;
    private final GuideMissionManager missionManager;

    public GuideCommand(JavaPlugin plugin, GuideMissionManager missionManager) {
        this.plugin = plugin;
        this.missionManager = missionManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("플레이어만 사용할 수 있는 명령어입니다.", NamedTextColor.RED));
                return true;
            }
            new GuideGui(plugin, missionManager).open(player);
            return true;
        }
        switch (args[0]) {
            case "추가" -> add(sender, args);
            case "제거" -> remove(sender, args);
            case "수정" -> update(sender, args);
            case "목록" -> list(sender);
            default -> sender.sendMessage(Component.text("사용법: /길라잡이 [추가|제거|수정|목록]", NamedTextColor.RED));
        }
        return true;
    }

    private boolean requirePermission(CommandSender sender) {
        if (!sender.hasPermission(MANAGE_PERMISSION)) {
            sender.sendMessage(Component.text("이 명령어를 사용할 권한이 없습니다.", NamedTextColor.RED));
            return false;
        }
        return true;
    }

    /** 제목은 공백 없는 한 단어여야 함(다음 단어부터는 전부 "해야할 행동"에 포함) — /칭호생성 <id> <표시>가 id를 한 단어로 요구하는 것과 같은 이유. */
    private void add(CommandSender sender, String[] args) {
        if (!requirePermission(sender)) {
            return;
        }
        if (args.length < 4) {
            sender.sendMessage(Component.text("사용법: /길라잡이 추가 [번호] [제목(공백없이)] [해야할 행동]", NamedTextColor.RED));
            return;
        }
        Integer number = parseNumber(sender, args[1]);
        if (number == null) {
            return;
        }
        String title = args[2];
        String action = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
        if (missionManager.add(number, title, action) == GuideMissionManager.AddResult.ALREADY_EXISTS) {
            sender.sendMessage(Component.text("이미 존재하는 번호입니다: " + number + " (수정하려면 /길라잡이 수정 사용)", NamedTextColor.RED));
            return;
        }
        sender.sendMessage(Component.text(number + "번 미션을 추가했습니다: " + title, NamedTextColor.GREEN));
    }

    private void update(CommandSender sender, String[] args) {
        if (!requirePermission(sender)) {
            return;
        }
        if (args.length < 4) {
            sender.sendMessage(Component.text("사용법: /길라잡이 수정 [번호] [제목(공백없이)] [해야할 행동]", NamedTextColor.RED));
            return;
        }
        Integer number = parseNumber(sender, args[1]);
        if (number == null) {
            return;
        }
        String title = args[2];
        String action = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
        if (missionManager.update(number, title, action) == GuideMissionManager.UpdateResult.NOT_FOUND) {
            sender.sendMessage(Component.text("존재하지 않는 번호입니다: " + number, NamedTextColor.RED));
            return;
        }
        sender.sendMessage(Component.text(number + "번 미션을 수정했습니다: " + title, NamedTextColor.GREEN));
    }

    private void remove(CommandSender sender, String[] args) {
        if (!requirePermission(sender)) {
            return;
        }
        if (args.length != 2) {
            sender.sendMessage(Component.text("사용법: /길라잡이 제거 [번호]", NamedTextColor.RED));
            return;
        }
        Integer number = parseNumber(sender, args[1]);
        if (number == null) {
            return;
        }
        if (missionManager.remove(number) == GuideMissionManager.RemoveResult.NOT_FOUND) {
            sender.sendMessage(Component.text("존재하지 않는 번호입니다: " + number, NamedTextColor.RED));
            return;
        }
        sender.sendMessage(Component.text(number + "번 미션을 제거했습니다.", NamedTextColor.GREEN));
    }

    private void list(CommandSender sender) {
        if (!requirePermission(sender)) {
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("플레이어만 사용할 수 있는 명령어입니다.", NamedTextColor.RED));
            return;
        }
        new GuideMissionListGui(missionManager).open(player);
    }

    private Integer parseNumber(CommandSender sender, String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("번호는 숫자여야 합니다.", NamedTextColor.RED));
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(MANAGE_PERMISSION)) {
            return List.of();
        }
        if (args.length == 1) {
            return TabCompletions.filter(List.of("추가", "제거", "수정", "목록"), args[0]);
        }
        if (args.length == 2 && (args[0].equals("제거") || args[0].equals("수정"))) {
            return TabCompletions.filter(missionManager.all().stream().map(m -> String.valueOf(m.number())).toList(), args[1]);
        }
        return List.of();
    }
}
