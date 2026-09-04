package com.yeowool.core.help;

import com.yeowool.core.util.TabCompletions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * {@code /여울도움말 [카테고리]} — a hand-curated index of every command
 * across all 10 plugins in the suite (config.yml's {@code help.categories}),
 * since there's no other way for a player to discover the ~40 commands that
 * have accumulated across separate plugins with no shared command registry.
 * Lives in core rather than a specific feature plugin since it needs to
 * describe every plugin without taking a compile dependency on any of them.
 * <p>
 * Categories named in {@code help.admin-only-categories} (currently just
 * "운영(관리자)") stay in {@code help.categories} itself — so {@link
 * HelpIndexChecker} still counts the admin commands they mention as
 * documented — but are left out of the plain {@code /여울도움말}
 * category list and refuse to open for a non-op sender, so regular players
 * only ever see player-facing commands by default.
 */
public final class HelpCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;

    public HelpCommand(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        ConfigurationSection categories = plugin.getConfig().getConfigurationSection("help.categories");
        if (categories == null) {
            sender.sendMessage(Component.text("도움말 설정이 없습니다.", NamedTextColor.RED));
            return true;
        }
        List<String> adminOnly = plugin.getConfig().getStringList("help.admin-only-categories");
        if (args.length == 0) {
            List<String> visible = categories.getKeys(false).stream()
                    .filter(key -> sender.isOp() || !adminOnly.contains(key))
                    .toList();
            sender.sendMessage(Component.text("=== 여울 도움말 — 카테고리 ===", NamedTextColor.GOLD));
            sender.sendMessage(Component.text(String.join(", ", visible), NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("/여울도움말 <카테고리> 로 자세히 보기", NamedTextColor.GRAY));
            return true;
        }
        if (adminOnly.contains(args[0]) && !sender.isOp()) {
            sender.sendMessage(Component.text("이 카테고리를 볼 권한이 없습니다.", NamedTextColor.RED));
            return true;
        }
        List<String> lines = categories.getStringList(args[0]);
        if (lines.isEmpty()) {
            sender.sendMessage(Component.text("존재하지 않는 카테고리입니다: " + args[0], NamedTextColor.RED));
            return true;
        }
        sender.sendMessage(Component.text("=== " + args[0] + " ===", NamedTextColor.GOLD));
        for (String line : lines) {
            sender.sendMessage(Component.text(line, NamedTextColor.GRAY));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        ConfigurationSection categories = plugin.getConfig().getConfigurationSection("help.categories");
        if (categories == null) {
            return List.of();
        }
        List<String> adminOnly = plugin.getConfig().getStringList("help.admin-only-categories");
        List<String> visible = categories.getKeys(false).stream()
                .filter(key -> sender.isOp() || !adminOnly.contains(key))
                .toList();
        return TabCompletions.filter(visible, args[0]);
    }
}
