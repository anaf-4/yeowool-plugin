package com.yeowool.community.quest;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** {@code /출석체크} — opens {@link AttendanceGui} (일일/주간/월간 claim buttons). */
public final class AttendanceCheckCommand implements CommandExecutor {

    private final YeowoolCoreAPI core;
    private final AttendanceManager attendanceManager;
    private final MessageService messages;

    public AttendanceCheckCommand(YeowoolCoreAPI core, AttendanceManager attendanceManager, MessageService messages) {
        this.core = core;
        this.attendanceManager = attendanceManager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return true;
        }
        new AttendanceGui(core, attendanceManager, messages, player).open(player);
        return true;
    }
}
