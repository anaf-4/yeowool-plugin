package com.yeowool.core.help;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@link HelpCommand}'s {@code help.categories} is a hand-curated index —
 * deliberately, since the curated text carries subcommand syntax and
 * groupings ("/경고 지급|회수|기록 ...") that a plugin.yml's one-line
 * {@code description:} could never reproduce on its own. But hand-curated
 * means it silently drifts: a brand new top-level command is easy to add to
 * a plugin.yml and just as easy to forget to also mention here (this
 * happened more than once in this project's own history). Rather than
 * replacing the curated index with a shallower auto-generated one, this
 * cross-checks it against every plugin's actual registered commands at
 * startup and logs whichever ones aren't mentioned anywhere, so a gap shows
 * up in the console immediately instead of staying invisible until a player
 * asks "how do I use the new thing".
 * <p>
 * This can only ever catch missing *top-level* commands (e.g. a whole new
 * {@code /칭호북}), not a new subcommand bolted onto an existing one (e.g.
 * {@code /경고}'s 지급/회수/기록 split) — plugin.yml has no visibility into
 * subcommands, so that half still relies on whoever adds one remembering to
 * update the text.
 */
public final class HelpIndexChecker {

    private static final Pattern COMMAND_TOKEN = Pattern.compile("/([가-힣a-zA-Z0-9_]+)");

    private HelpIndexChecker() {
    }

    /** Every {@code /command} mentioned anywhere in {@code help.categories} that has no matching registered command in any plugin. */
    public static List<String> findUnlistedCommands(JavaPlugin helpPlugin) {
        Set<String> mentioned = mentionedCommandNames(helpPlugin);
        List<String> missing = new ArrayList<>();
        for (Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
            for (String commandName : plugin.getDescription().getCommands().keySet()) {
                if (!mentioned.contains(commandName.toLowerCase(Locale.ROOT))) {
                    missing.add(plugin.getName() + ": /" + commandName);
                }
            }
        }
        return missing;
    }

    private static Set<String> mentionedCommandNames(JavaPlugin helpPlugin) {
        Set<String> mentioned = new HashSet<>();
        ConfigurationSection categories = helpPlugin.getConfig().getConfigurationSection("help.categories");
        if (categories == null) {
            return mentioned;
        }
        for (String key : categories.getKeys(false)) {
            for (String line : categories.getStringList(key)) {
                Matcher matcher = COMMAND_TOKEN.matcher(line);
                while (matcher.find()) {
                    mentioned.add(matcher.group(1).toLowerCase(Locale.ROOT));
                }
            }
        }
        return mentioned;
    }
}
