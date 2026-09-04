package com.yeowool.community.title;

import com.yeowool.core.api.service.MessageService;
import com.yeowool.core.util.TabCompletions;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;

/**
 * {@code /칭호삭제 <id>} — OP-only. Only deletes titles {@code /칭호생성}
 * created; a {@code config.yml}-defined title has to be removed from that
 * file by hand (see {@link TitleManager#deleteTitle}).
 */
public final class TitleDeleteCommand implements CommandExecutor, TabCompleter {

    private final TitleManager titleManager;
    private final MessageService messages;

    public TitleDeleteCommand(TitleManager titleManager, MessageService messages) {
        this.titleManager = titleManager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) {
            messages.send(sender, "title.delete-usage");
            return true;
        }
        String id = args[0];
        switch (titleManager.deleteTitle(id)) {
            case SUCCESS -> messages.send(sender, "title.delete-success", Placeholder.unparsed("id", id));
            case NOT_FOUND -> messages.send(sender, "title.unknown-title");
            case CONFIG_DEFINED -> messages.send(sender, "title.delete-config-defined", Placeholder.unparsed("id", id));
            case FAILED -> messages.send(sender, "title.delete-failed");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return TabCompletions.filter(titleManager.all().stream().map(TitleDefinition::id).toList(), args[0]);
        }
        return List.of();
    }
}
