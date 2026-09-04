package com.yeowool.core.command;

import com.yeowool.core.config.CoreConfig;
import com.yeowool.core.message.MessageManager;
import com.yeowool.core.sound.SoundManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;

/**
 * {@code /여울코어} — reloads config.yml, messages.yml, and sound cues without
 * a full server restart.
 */
public final class CoreCommand implements CommandExecutor, TabCompleter {

    private final CoreConfig config;
    private final MessageManager messages;
    private final SoundManager sounds;

    public CoreCommand(CoreConfig config, MessageManager messages, SoundManager sounds) {
        this.config = config;
        this.messages = messages;
        this.sounds = sounds;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("yeowool.core.admin")) {
            messages.send(sender, "general.no-permission");
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            config.reload();
            messages.reload();
            sounds.reload();
            messages.send(sender, "general.reload-success");
            return true;
        }

        sender.sendMessage("/" + label + " reload");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("reload");
        }
        return List.of();
    }
}
