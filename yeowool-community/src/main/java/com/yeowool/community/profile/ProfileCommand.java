package com.yeowool.community.profile;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
import com.yeowool.core.util.TabCompletions;
import com.yeowool.community.title.TitleDefinition;
import com.yeowool.community.title.TitleManager;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * {@code /프로필 [닉네임]} — section 9.2 of the plugin plan: nickname, land
 * level, wallet balance, playtime, equipped title.
 */
public final class ProfileCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private final TitleManager titleManager;
    private final MessageService messages;

    public ProfileCommand(JavaPlugin plugin, YeowoolCoreAPI core, TitleManager titleManager, MessageService messages) {
        this.plugin = plugin;
        this.core = core;
        this.titleManager = titleManager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player viewer)) {
            messages.send(sender, "general.player-only");
            return true;
        }

        if (args.length == 0) {
            show(viewer, viewer.getUniqueId(), viewer.getName());
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (target.getUniqueId() == null) {
            messages.send(viewer, "profile.player-not-found");
            return true;
        }

        core.playerData().load(target.getUniqueId(), args[0]).whenComplete((data, error) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (error != null) {
                        messages.send(viewer, "profile.player-not-found");
                        return;
                    }
                    show(viewer, target.getUniqueId(), args[0]);
                }));
        return true;
    }

    private void show(Player viewer, java.util.UUID uuid, String name) {
        var data = core.playerData().getIfLoaded(uuid).orElse(null);
        if (data == null) {
            messages.send(viewer, "profile.player-not-found");
            return;
        }

        long playtimeMinutes = data.getStatistic(PlaytimeTracker.STAT_KEY);
        String title = titleManager.equippedId(data)
                .flatMap(titleManager::find)
                .map(TitleDefinition::display)
                .orElse("-");

        messages.send(viewer, "profile.display",
                Placeholder.unparsed("name", name),
                Placeholder.unparsed("title", title),
                Placeholder.unparsed("on", String.format("%,d", data.getOnBalance())),
                Placeholder.unparsed("landlevel", String.valueOf(core.landStats().getLandLevel(uuid))),
                Placeholder.unparsed("playtime", String.valueOf(playtimeMinutes)));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return TabCompletions.filteredOnlinePlayerNames(args[0]);
        }
        return List.of();
    }
}
