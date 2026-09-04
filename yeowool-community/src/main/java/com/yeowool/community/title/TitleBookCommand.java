package com.yeowool.community.title;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
import com.yeowool.core.util.TabCompletions;
import com.yeowool.community.display.PlayerIdentityService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * {@code /칭호북 생성|제거|목록} — a GUI-first alternative surface over the
 * same {@link TitleManager} that {@code /칭호생성}/{@code /칭호삭제}/{@code
 * /칭호 지급} already use (those keep working as before). {@code 생성} takes
 * just the display text as one argument and auto-derives a plain id from it
 * (strips MiniMessage tags/brackets, matching how {@code /칭호생성}'s
 * separate id/display split would otherwise need a second argument).
 * {@code 목록} opens a two-step GUI: pick a title, then pick an online
 * player to grant it to. Offline targets aren't supported by the GUI — a
 * grant made through {@link com.yeowool.core.util.PlayerDataResolver}'s
 * offline path is saved and unloaded again almost immediately, so a GUI
 * click that could happen much later would silently mutate data nobody
 * saves again. Offline grants should still go through {@code /칭호 지급}.
 */
public final class TitleBookCommand implements CommandExecutor, TabCompleter {

    private static final String MANAGE_PERMISSION = "yeowool.community.title.manage";

    private final YeowoolCoreAPI core;
    private final TitleManager titleManager;
    private final PlayerIdentityService identityService;
    private final MessageService messages;

    public TitleBookCommand(YeowoolCoreAPI core, TitleManager titleManager, PlayerIdentityService identityService, MessageService messages) {
        this.core = core;
        this.titleManager = titleManager;
        this.identityService = identityService;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(MANAGE_PERMISSION)) {
            messages.send(sender, "general.no-permission");
            return true;
        }
        if (args.length == 0) {
            messages.send(sender, "titlebook.usage");
            return true;
        }
        switch (args[0]) {
            case "생성" -> create(sender, args);
            case "제거" -> delete(sender, args);
            case "목록" -> list(sender);
            default -> messages.send(sender, "titlebook.usage");
        }
        return true;
    }

    private void create(CommandSender sender, String[] args) {
        if (args.length < 2) {
            messages.send(sender, "titlebook.create-usage");
            return;
        }
        String display = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        String id = slugify(display);
        if (!titleManager.createTitle(id, display)) {
            messages.send(sender, "titlebook.create-duplicate", Placeholder.parsed("name", display));
            return;
        }
        messages.send(sender, "titlebook.create-success",
                Placeholder.parsed("name", display),
                Placeholder.unparsed("id", id));
    }

    private void delete(CommandSender sender, String[] args) {
        if (args.length != 2) {
            messages.send(sender, "titlebook.delete-usage");
            return;
        }
        String id = args[1];
        var display = titleManager.find(id).map(TitleDefinition::display).orElse(id);
        var result = titleManager.deleteTitle(id);
        switch (result) {
            case SUCCESS -> messages.send(sender, "titlebook.delete-success", Placeholder.parsed("name", display));
            case NOT_FOUND -> messages.send(sender, "titlebook.delete-not-found", Placeholder.unparsed("name", id));
            case CONFIG_DEFINED -> messages.send(sender, "titlebook.delete-config-defined", Placeholder.unparsed("name", id));
            case FAILED -> messages.send(sender, "titlebook.delete-failed");
        }
    }

    private void list(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return;
        }
        if (titleManager.all().isEmpty()) {
            messages.send(sender, "titlebook.list-empty");
            return;
        }
        new TitleBookGui(core, titleManager, identityService, messages, 0).open(player);
    }

    /** Strips MiniMessage tags/brackets/spacing from a display string to get a plain lookup id, e.g. {@code "<red>[STAFF]</red>"} → {@code "staff"}. */
    private static String slugify(String display) {
        String withoutTags = display.replaceAll("<[^>]+>", "");
        String withoutBrackets = withoutTags.replaceAll("[\\[\\]()]", "").trim();
        String withUnderscores = withoutBrackets.replaceAll("\\s+", "_");
        return withUnderscores.isBlank() ? display : withUnderscores.toLowerCase(Locale.ROOT);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(MANAGE_PERMISSION)) {
            return List.of();
        }
        if (args.length == 1) {
            return TabCompletions.filter(List.of("생성", "제거", "목록"), args[0]);
        }
        if (args.length == 2 && args[0].equals("제거")) {
            return TabCompletions.filter(titleManager.all().stream().map(TitleDefinition::id).toList(), args[1]);
        }
        return List.of();
    }
}
