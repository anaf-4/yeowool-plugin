package com.yeowool.admin.motd;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.yeowool.core.api.service.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.List;

/**
 * {@code /motd} — edits the proxy's server-list MOTD live. The proxy (YeowoolProxy MotdManager) owns and
 * saves the list and answers the sender itself; this only validates and forwards over {@code yeowool:motd}.
 */
public final class MotdCommand implements CommandExecutor, TabCompleter {

    public static final String CHANNEL = "yeowool:motd";

    private final JavaPlugin plugin;
    private final MessageService messages;

    public MotdCommand(JavaPlugin plugin, MessageService messages) {
        this.plugin = plugin;
        this.messages = messages;
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "" : args[0];
        switch (sub) {
            case "보기" -> send(sender, "LIST", 0, "", "");
            case "추가" -> {
                String[] lines = lines(args, 1);
                if (lines == null) {
                    messages.send(sender, "motd.usage");
                    return true;
                }
                send(sender, "ADD", 0, lines[0], lines[1]);
            }
            case "수정" -> {
                Integer number = number(args);
                String[] lines = lines(args, 2);
                if (number == null || lines == null) {
                    messages.send(sender, "motd.usage");
                    return true;
                }
                send(sender, "EDIT", number, lines[0], lines[1]);
            }
            case "삭제" -> {
                Integer number = number(args);
                if (number == null) {
                    messages.send(sender, "motd.usage");
                    return true;
                }
                send(sender, "REMOVE", number, "", "");
            }
            case "간격" -> {
                Integer seconds = number(args);
                if (seconds == null || seconds < 1) {
                    messages.send(sender, "motd.usage");
                    return true;
                }
                send(sender, "INTERVAL", seconds, "", "");
            }
            case "초기화" -> send(sender, "CLEAR", 0, "", "");
            default -> messages.send(sender, "motd.usage");
        }
        return true;
    }

    /** {@code 첫 줄 | 둘째 줄} from {@code args[from..]}; the second line is optional. */
    private static String[] lines(String[] args, int from) {
        if (args.length <= from) {
            return null;
        }
        String joined = String.join(" ", Arrays.copyOfRange(args, from, args.length));
        int bar = joined.indexOf('|');
        String first = (bar < 0 ? joined : joined.substring(0, bar)).trim();
        String second = bar < 0 ? "" : joined.substring(bar + 1).trim();
        return first.isEmpty() ? null : new String[]{first, second};
    }

    private static Integer number(String[] args) {
        try {
            return Integer.parseInt(args[1]);
        } catch (ArrayIndexOutOfBoundsException | NumberFormatException e) {
            return null;
        }
    }

    /** Write order must match YeowoolProxy MotdManager#handle: sender, action, number, line 1, line 2. */
    private void send(CommandSender sender, String action, int number, String line1, String line2) {
        Player via = sender instanceof Player player ? player : Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
        if (via == null) {
            messages.send(sender, "motd.no-carrier");
            return;
        }
        if (!via.getListeningPluginChannels().contains(CHANNEL)) {
            messages.send(sender, "motd.proxy-missing");
            return;
        }
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF(sender instanceof Player player ? player.getUniqueId().toString() : "console");
        out.writeUTF(action);
        out.writeInt(number);
        out.writeUTF(line1);
        out.writeUTF(line2);
        via.sendPluginMessage(plugin, CHANNEL, out.toByteArray());
        if (!(sender instanceof Player)) {
            messages.send(sender, "motd.sent-console");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("보기", "추가", "수정", "삭제", "간격", "초기화").stream().filter(s -> s.startsWith(args[0])).toList();
        }
        return List.of();
    }
}
