package com.yeowool.admin.restart;

import com.yeowool.core.api.service.MessageService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * {@code /서버재부팅설정 [HH:mm[,HH:mm...]|해제]} — sets (or clears) this
 * server's daily auto-restart times; no argument reports the current
 * schedule. Console/RCON-friendly (no GUI needed for a once-in-a-while
 * setting like this).
 */
public final class RestartScheduleCommand implements CommandExecutor {

    private final RestartScheduleStore store;
    private final MessageService messages;

    public RestartScheduleCommand(RestartScheduleStore store, MessageService messages) {
        this.store = store;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            List<LocalTime> times = store.times();
            if (times.isEmpty()) {
                messages.send(sender, "restart.status-none");
            } else {
                messages.send(sender, "restart.status", Placeholder.unparsed("times", format(times)));
            }
            return true;
        }
        if (args[0].equals("해제")) {
            store.clear();
            messages.send(sender, "restart.cleared");
            return true;
        }
        RestartScheduleStore.SetResult result = store.set(args[0]);
        if (result == RestartScheduleStore.SetResult.INVALID_FORMAT) {
            messages.send(sender, "restart.invalid-time");
            return true;
        }
        messages.send(sender, "restart.set", Placeholder.unparsed("times", format(store.times())));
        return true;
    }

    private String format(List<LocalTime> times) {
        return times.stream().map(LocalTime::toString).collect(Collectors.joining(", "));
    }
}
