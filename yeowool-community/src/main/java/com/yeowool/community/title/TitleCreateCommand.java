package com.yeowool.community.title;

import com.yeowool.core.api.service.MessageService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.Arrays;

/**
 * {@code /칭호생성 <id> <표시>} — OP-only (see {@code plugin.yml}'s
 * {@code default: op}). Creates a new admin-only title with no auto-unlock
 * condition, the same kind {@code staff} in {@code config.yml} is — an OP
 * still has to {@code /칭호 지급}/{@code 장착} it afterward, this command
 * only defines it.
 */
public final class TitleCreateCommand implements CommandExecutor {

    private final TitleManager titleManager;
    private final MessageService messages;

    public TitleCreateCommand(TitleManager titleManager, MessageService messages) {
        this.titleManager = titleManager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 2) {
            messages.send(sender, "title.create-usage");
            return true;
        }
        String id = args[0];
        String display = String.join(" ", Arrays.copyOfRange(args, 1, args.length));

        if (titleManager.createTitle(id, display)) {
            messages.send(sender, "title.create-success",
                    Placeholder.unparsed("id", id), Placeholder.parsed("title", display));
        } else {
            messages.send(sender, "title.create-duplicate", Placeholder.unparsed("id", id));
        }
        return true;
    }
}
