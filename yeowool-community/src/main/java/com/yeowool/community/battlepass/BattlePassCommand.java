package com.yeowool.community.battlepass;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.model.PlayerData;
import com.yeowool.core.api.service.MessageService;
import com.yeowool.core.util.TabCompletions;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/**
 * {@code /배틀패스} - no args opens the portal for the caller; {@code [무료|유료] 보상설정}
 * (admin) opens the reward editor starting at tier 1 (tier navigation happens inside the GUI
 * itself via {@link BattlePassRewardEditorGui}'s prev/next buttons, not command args); {@code
 * 프리미엄지급 <닉네임>} (admin) grants premium without charging cash (e.g. support compensation).
 */
public final class BattlePassCommand implements CommandExecutor, TabCompleter {

    private final YeowoolCoreAPI core;
    private final BattlePassManager manager;
    private final BattlePassRewardStore rewardStore;
    private final BattlePassAmountListener amountListener;
    private final MessageService messages;

    public BattlePassCommand(YeowoolCoreAPI core, BattlePassManager manager, BattlePassRewardStore rewardStore,
                              BattlePassAmountListener amountListener, MessageService messages) {
        this.core = core;
        this.manager = manager;
        this.rewardStore = rewardStore;
        this.amountListener = amountListener;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return true;
        }

        if (args.length == 0) {
            new BattlePassPortalGui(core, manager, messages, player).open(player);
            return true;
        }

        if (args[0].equals("프리미엄지급")) {
            return handleGrantPremium(player, args);
        }

        BattlePassTrack track = parseTrack(args[0]);
        if (track != null && args.length == 2 && args[1].equals("보상설정")) {
            return handleRewardSetup(player, track);
        }

        messages.send(player, "battlepass.usage");
        return true;
    }

    private boolean handleRewardSetup(Player player, BattlePassTrack track) {
        if (!player.hasPermission("yeowool.community.battlepass.manage")) {
            messages.send(player, "general.no-permission");
            return true;
        }
        new BattlePassRewardEditorGui(rewardStore, amountListener, track, 1).open(player);
        return true;
    }

    private boolean handleGrantPremium(Player player, String[] args) {
        if (!player.hasPermission("yeowool.community.battlepass.manage")) {
            messages.send(player, "general.no-permission");
            return true;
        }
        if (args.length != 2) {
            messages.send(player, "battlepass.grant-premium-usage");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            messages.send(player, "battlepass.player-not-found");
            return true;
        }
        PlayerData data = core.playerData().getOnline(target.getUniqueId());
        manager.grantPremium(data);
        messages.send(player, "battlepass.grant-premium-success", Placeholder.unparsed("target", target.getName()));
        return true;
    }

    private BattlePassTrack parseTrack(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "무료", "free" -> BattlePassTrack.FREE;
            case "유료", "프리미엄", "premium" -> BattlePassTrack.PREMIUM;
            default -> null;
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return TabCompletions.filter(List.of("무료", "유료", "프리미엄지급"), args[0]);
        }
        if (args.length == 2 && parseTrack(args[0]) != null) {
            return TabCompletions.filter(List.of("보상설정"), args[1]);
        }
        if (args.length == 2 && args[0].equals("프리미엄지급")) {
            return TabCompletions.filteredOnlinePlayerNames(args[1]);
        }
        return List.of();
    }
}
