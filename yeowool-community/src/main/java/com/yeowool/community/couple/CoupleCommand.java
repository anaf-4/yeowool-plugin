package com.yeowool.community.couple;

import com.yeowool.core.api.service.MessageService;
import com.yeowool.core.util.DurationFormat;
import com.yeowool.core.util.TabCompletions;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

/** {@code /커플 신청|수락|거절|해제|정보} — a simpler pairing than full marriage: propose/accept/breakup, no ceremony. */
public final class CoupleCommand implements CommandExecutor, TabCompleter {

    private final CoupleManager coupleManager;
    private final MessageService messages;

    public CoupleCommand(CoupleManager coupleManager, MessageService messages) {
        this.coupleManager = coupleManager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return true;
        }
        if (args.length == 0) {
            info(player, player);
            return true;
        }

        switch (args[0]) {
            case "신청" -> propose(player, args);
            case "수락" -> accept(player);
            case "거절" -> decline(player);
            case "해제" -> breakUp(player);
            case "정보" -> info(player, args);
            default -> messages.send(player, "couple.usage");
        }
        return true;
    }

    private void propose(Player player, String[] args) {
        if (args.length != 2) {
            messages.send(player, "couple.propose-usage");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            messages.send(player, "couple.player-not-found");
            return;
        }
        switch (coupleManager.propose(player.getUniqueId(), target.getUniqueId())) {
            case CANNOT_PROPOSE_SELF -> messages.send(player, "couple.cannot-propose-self");
            case ALREADY_SELF_PARTNERED -> messages.send(player, "couple.already-partnered-self");
            case ALREADY_TARGET_PARTNERED -> messages.send(player, "couple.already-partnered-target", Placeholder.unparsed("target", target.getName()));
            case OK -> {
                messages.send(player, "couple.propose-sent", Placeholder.unparsed("target", target.getName()));
                messages.send(target, "couple.propose-received", Placeholder.unparsed("requester", player.getName()));
            }
        }
    }

    private void accept(Player player) {
        var result = coupleManager.accept(player.getUniqueId(), requesterUuid -> {
            messages.send(player, "couple.accept-success");
            Player requesterPlayer = Bukkit.getPlayer(requesterUuid);
            if (requesterPlayer != null) {
                messages.send(requesterPlayer, "couple.accepted-by", Placeholder.unparsed("target", player.getName()));
            }
        });
        switch (result) {
            case NO_PENDING_REQUEST -> messages.send(player, "couple.no-pending-request");
            case REQUESTER_NOW_PARTNERED, TARGET_NOW_PARTNERED -> messages.send(player, "couple.no-longer-available");
            case OK -> {
            }
        }
    }

    private void decline(Player player) {
        coupleManager.decline(player.getUniqueId());
        messages.send(player, "couple.declined");
    }

    private void breakUp(Player player) {
        var partner = coupleManager.breakUp(player.getUniqueId());
        if (partner.isEmpty()) {
            messages.send(player, "couple.not-partnered");
            return;
        }
        OfflinePlayer partnerPlayer = Bukkit.getOfflinePlayer(partner.get());
        messages.send(player, "couple.breakup-success", Placeholder.unparsed("target", String.valueOf(partnerPlayer.getName())));
        Player onlinePartner = partnerPlayer.getPlayer();
        if (onlinePartner != null) {
            messages.send(onlinePartner, "couple.broken-up-by", Placeholder.unparsed("target", player.getName()));
        }
    }

    private void info(Player viewer, String[] args) {
        if (args.length == 1) {
            info(viewer, viewer);
            return;
        }
        if (args.length != 2) {
            messages.send(viewer, "couple.info-usage");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        info(viewer, target);
    }

    private void info(Player viewer, OfflinePlayer subject) {
        var partner = coupleManager.partnerOf(subject.getUniqueId());
        if (partner.isEmpty()) {
            messages.send(viewer, "couple.info-none", Placeholder.unparsed("target", String.valueOf(subject.getName())));
            return;
        }
        OfflinePlayer partnerPlayer = Bukkit.getOfflinePlayer(partner.get());
        long since = coupleManager.sinceOf(subject.getUniqueId()).orElse(System.currentTimeMillis());
        messages.send(viewer, "couple.info", Placeholder.unparsed("target", String.valueOf(subject.getName())),
                Placeholder.unparsed("partner", String.valueOf(partnerPlayer.getName())),
                Placeholder.unparsed("duration", DurationFormat.humanize(System.currentTimeMillis() - since)));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return TabCompletions.filter(List.of("신청", "수락", "거절", "해제", "정보"), args[0]);
        }
        if (args.length == 2 && args[0].equals("신청")) {
            return TabCompletions.filteredOnlinePlayerNames(args[1]);
        }
        return List.of();
    }
}
