package com.yeowool.teleport.playerwarp;

import com.yeowool.core.util.TabCompletions;
import com.yeowool.teleport.util.PlayerWarpCurrency;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * {@code /플레이어워프 [목록]} opens the browse GUI, {@code 생성}/{@code 삭제}
 * manage the sender's own, {@code 내워프}/{@code 즐겨찾기} open their dedicated
 * GUIs — same 생성/삭제/목록 shape {@code /홈} already uses, just public
 * instead of private, and with the real PlayerWarps plugin's create fee.
 */
public final class PlayerWarpCommand implements CommandExecutor, TabCompleter {

    private final PlayerWarpContext ctx;
    private final PlayerWarpTextInput textInput;

    public PlayerWarpCommand(PlayerWarpContext ctx, PlayerWarpTextInput textInput) {
        this.ctx = ctx;
        this.textInput = textInput;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            ctx.messages().send(sender, "general.player-only");
            return true;
        }
        if (args.length == 0) {
            new PlayerWarpHubGui(ctx, textInput).open(player);
            return true;
        }
        if (args[0].equals("목록")) {
            new PlayerWarpBrowseGui(ctx, textInput, 0, null, null, PlayerWarpBrowseGui.SortMode.ALPHABETICAL).open(player);
            return true;
        }
        switch (args[0]) {
            case "생성" -> create(player, args);
            case "삭제" -> delete(player, args);
            case "내워프" -> new PlayerWarpMyGui(ctx, textInput, player).open(player);
            case "즐겨찾기" -> new PlayerWarpSavedGui(ctx, player).open(player);
            default -> ctx.messages().send(player, "playerwarp.usage");
        }
        return true;
    }

    private void create(Player player, String[] args) {
        if (args.length != 2) {
            ctx.messages().send(player, "playerwarp.create-usage");
            return;
        }
        String name = args[1];
        if (name.length() > ctx.config().nameMaxLength()) {
            ctx.messages().send(player, "playerwarp.name-too-long", Placeholder.unparsed("max", String.valueOf(ctx.config().nameMaxLength())));
            return;
        }
        long fee = ctx.config().createFee();
        if (!PlayerWarpCurrency.has(ctx, player.getUniqueId(), fee)) {
            ctx.messages().send(player, "playerwarp.insufficient-funds", Placeholder.unparsed("cost", String.format("%,d", fee)));
            return;
        }
        var result = ctx.warps().create(player.getUniqueId(), name, player.getLocation());
        switch (result) {
            case LIMIT_REACHED -> ctx.messages().send(player, "playerwarp.limit-reached");
            case NAME_TAKEN -> ctx.messages().send(player, "playerwarp.create-name-taken", Placeholder.unparsed("name", name));
            case SUCCESS -> {
                PlayerWarpCurrency.charge(ctx, player.getUniqueId(), fee, "플레이어 워프 생성 (" + name + ")");
                ctx.messages().send(player, "playerwarp.create-success", Placeholder.unparsed("name", name));
            }
        }
    }

    private void delete(Player player, String[] args) {
        if (args.length != 2) {
            ctx.messages().send(player, "playerwarp.delete-usage");
            return;
        }
        if (!ctx.warps().delete(player.getUniqueId(), args[1])) {
            ctx.messages().send(player, "playerwarp.delete-not-found", Placeholder.unparsed("name", args[1]));
            return;
        }
        PlayerWarpCurrency.grant(ctx, player.getUniqueId(), ctx.config().deleteRefund(), "플레이어 워프 삭제 환불 (" + args[1] + ")");
        ctx.messages().send(player, "playerwarp.delete-success", Placeholder.unparsed("name", args[1]));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return TabCompletions.filter(List.of("목록", "생성", "삭제", "내워프", "즐겨찾기"), args[0]);
        }
        if (args.length == 2 && args[0].equals("삭제") && sender instanceof Player player) {
            return TabCompletions.filter(ctx.warps().ownedBy(player.getUniqueId()).values().stream()
                    .map(w -> w.name()).toList(), args[1]);
        }
        return List.of();
    }
}
