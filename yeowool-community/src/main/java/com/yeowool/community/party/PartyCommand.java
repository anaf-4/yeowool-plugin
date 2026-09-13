package com.yeowool.community.party;

import com.yeowool.core.api.service.MessageService;
import com.yeowool.core.util.TabCompletions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;

/**
 * {@code /파티 생성} — 모루에서 이름을 입력하면({@link PartyCreateListener}) 이어서 최대
 * 인원(2-{@link PartyManager#HUD_SLOT_LIMIT})을 고르는 GUI({@link PartySizeGui})가 열림.
 * {@code /파티 <이름> <최대인원>}으로 인자를 바로 줘서 만드는 방법도 그대로 남아있음.
 * {@code /파티 탈퇴}, {@code /파티 삭제}(리더 전용), {@code /파티 가입 <이름>}(자유 가입,
 * 승인 불필요), {@code /파티 정보}. 파티원 알림은 지금 이 서버에 접속 중인 멤버에게만
 * 실시간으로 전달됨 — 다른 서버에 있는 멤버는 채팅 알림을 못 받지만(별도 크로스서버
 * 메시징 없이는 불가능), 파티 자체 소속/HUD 표시는 공유 DB 기반이라 항상 정확함.
 */
public final class PartyCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final PartyManager partyManager;
    private final PartyCreateListener createListener;
    private final MessageService messages;

    public PartyCommand(JavaPlugin plugin, PartyManager partyManager, PartyCreateListener createListener, MessageService messages) {
        this.plugin = plugin;
        this.partyManager = partyManager;
        this.createListener = createListener;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return true;
        }

        if (args.length == 0) {
            messages.send(player, "party.create-usage");
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "생성" -> createListener.beginCreate(player);
            case "탈퇴" -> leave(player);
            case "삭제" -> disband(player);
            case "가입" -> join(player, args);
            case "수락" -> approve(player, args);
            case "거절" -> deny(player, args);
            case "정보" -> info(player);
            default -> create(player, args);
        }
        return true;
    }

    private void create(Player player, String[] args) {
        if (args.length != 2 && args.length != 3) {
            messages.send(player, "party.create-usage");
            return;
        }
        String name = args[0];
        int maxSize;
        try {
            maxSize = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            messages.send(player, "party.create-usage");
            return;
        }
        PartyManager.JoinMode joinMode = PartyManager.JoinMode.FREE;
        if (args.length == 3) {
            if (args[2].equals("신청승인")) {
                joinMode = PartyManager.JoinMode.APPROVAL;
            } else if (!args[2].equals("자유가입")) {
                messages.send(player, "party.create-usage");
                return;
            }
        }

        partyManager.create(player.getUniqueId(), player.getName(), name, maxSize, joinMode).thenAccept(outcome ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    switch (outcome.result()) {
                        case OK -> messages.send(player, "party.create-success",
                                Placeholder.unparsed("name", name), Placeholder.unparsed("maxsize", String.valueOf(maxSize)));
                        case ALREADY_IN_PARTY -> messages.send(player, "party.already-in-party");
                        case NAME_TAKEN -> messages.send(player, "party.name-taken");
                        case INVALID_NAME -> messages.send(player, "party.invalid-name");
                        case INVALID_MAX_SIZE -> messages.send(player, "party.invalid-max-size",
                                Placeholder.unparsed("limit", String.valueOf(PartyManager.HUD_SLOT_LIMIT)));
                        case ERROR -> messages.send(player, "general.error");
                    }
                }));
    }

    private void leave(Player player) {
        partyManager.leave(player.getUniqueId()).thenAccept(outcome ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    switch (outcome.result()) {
                        case NOT_IN_PARTY -> messages.send(player, "party.leave-not-in-party");
                        case ERROR -> messages.send(player, "general.error");
                        case OK_DISBANDED -> messages.send(player, "party.leave-disbanded",
                                Placeholder.unparsed("name", outcome.partyName()));
                        case OK_LEFT -> {
                            messages.send(player, "party.leave-success", Placeholder.unparsed("name", outcome.partyName()));
                            partyManager.clearPresence(player.getUniqueId());
                            notifyOnlineMember(outcome.newLeader(), "party.member-left-announcement",
                                    Placeholder.unparsed("player", player.getName()));
                        }
                    }
                }));
    }

    private void disband(Player player) {
        partyManager.disband(player.getUniqueId()).thenAccept(outcome ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    switch (outcome.result()) {
                        case NOT_IN_PARTY -> messages.send(player, "party.disband-not-in-party");
                        case NOT_LEADER -> messages.send(player, "party.disband-not-leader");
                        case ERROR -> messages.send(player, "general.error");
                        case OK -> {
                            messages.send(player, "party.disband-success", Placeholder.unparsed("name", outcome.partyName()));
                            for (var member : outcome.members()) {
                                if (member.uuid().equals(player.getUniqueId())) {
                                    continue;
                                }
                                Player online = Bukkit.getPlayer(member.uuid());
                                if (online != null) {
                                    messages.send(online, "party.disband-announcement", Placeholder.unparsed("name", outcome.partyName()));
                                }
                                partyManager.clearPresence(member.uuid());
                            }
                        }
                    }
                }));
    }

    private void join(Player player, String[] args) {
        if (args.length != 2) {
            messages.send(player, "party.join-usage");
            return;
        }
        String name = args[1];
        partyManager.join(player.getUniqueId(), player.getName(), name).thenAccept(outcome ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    switch (outcome.result()) {
                        case ALREADY_IN_PARTY -> messages.send(player, "party.join-already-in-party");
                        case ALREADY_REQUESTED -> messages.send(player, "party.join-already-requested");
                        case PARTY_NOT_FOUND -> messages.send(player, "party.join-not-found");
                        case PARTY_FULL -> messages.send(player, "party.join-full");
                        case ERROR -> messages.send(player, "general.error");
                        case REQUEST_SENT -> {
                            messages.send(player, "party.join-request-sent", Placeholder.unparsed("name", outcome.party().name()));
                            notifyOnlineMember(outcome.party().leader(), "party.join-request-received",
                                    Placeholder.unparsed("player", player.getName()));
                        }
                        case OK -> {
                            messages.send(player, "party.join-success", Placeholder.unparsed("name", outcome.party().name()));
                            for (var member : outcome.party().members()) {
                                if (member.uuid().equals(player.getUniqueId())) {
                                    continue;
                                }
                                Player online = Bukkit.getPlayer(member.uuid());
                                if (online != null) {
                                    messages.send(online, "party.member-joined-announcement", Placeholder.unparsed("player", player.getName()));
                                }
                            }
                        }
                    }
                }));
    }

    private void approve(Player player, String[] args) {
        if (args.length != 2) {
            messages.send(player, "party.approve-usage");
            return;
        }
        partyManager.approve(player.getUniqueId(), args[1]).thenAccept(outcome ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    switch (outcome.result()) {
                        case NOT_IN_PARTY, NOT_LEADER -> messages.send(player, "party.approve-not-leader");
                        case NO_SUCH_REQUEST -> messages.send(player, "party.approve-no-such-request", Placeholder.unparsed("player", args[1]));
                        case PARTY_FULL -> messages.send(player, "party.join-full");
                        case ERROR -> messages.send(player, "general.error");
                        case OK -> {
                            messages.send(player, "party.approve-success", Placeholder.unparsed("player", args[1]));
                            for (var member : outcome.party().members()) {
                                if (member.uuid().equals(player.getUniqueId())) {
                                    continue;
                                }
                                Player online = Bukkit.getPlayer(member.uuid());
                                if (online != null) {
                                    messages.send(online, member.uuid().equals(outcome.requester())
                                            ? "party.join-approved"
                                            : "party.member-joined-announcement",
                                            Placeholder.unparsed("player", args[1]), Placeholder.unparsed("name", outcome.party().name()));
                                }
                            }
                        }
                    }
                }));
    }

    private void deny(Player player, String[] args) {
        if (args.length != 2) {
            messages.send(player, "party.deny-usage");
            return;
        }
        partyManager.deny(player.getUniqueId(), args[1]).thenAccept(outcome ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    switch (outcome.result()) {
                        case NOT_IN_PARTY, NOT_LEADER -> messages.send(player, "party.approve-not-leader");
                        case NO_SUCH_REQUEST -> messages.send(player, "party.approve-no-such-request", Placeholder.unparsed("player", args[1]));
                        case ERROR -> messages.send(player, "general.error");
                        case OK -> {
                            messages.send(player, "party.deny-success", Placeholder.unparsed("player", args[1]));
                            notifyOnlineMember(outcome.requester(), "party.join-denied", Placeholder.unparsed("name", outcome.partyName()));
                        }
                    }
                }));
    }

    private void info(Player player) {
        partyManager.info(player.getUniqueId()).thenAccept(infoOpt ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (infoOpt.isEmpty()) {
                        messages.send(player, "party.info-not-in-party");
                        return;
                    }
                    var info = infoOpt.get();
                    player.sendMessage(Component.text("=== 파티: " + info.name() + " ===", NamedTextColor.GOLD));
                    player.sendMessage(Component.text("인원: " + info.members().size() + " / " + info.maxSize()
                                    + " (" + (info.joinMode() == PartyManager.JoinMode.APPROVAL ? "신청승인" : "자유가입") + ")", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false));
                    for (var member : info.members()) {
                        boolean isLeader = member.uuid().equals(info.leader());
                        boolean online = Bukkit.getPlayer(member.uuid()) != null;
                        Component line = Component.text((isLeader ? "★ " : "- ") + member.name(),
                                        isLeader ? NamedTextColor.YELLOW : NamedTextColor.WHITE)
                                .append(Component.text(online ? " (온라인)" : " (오프라인)", NamedTextColor.DARK_GRAY))
                                .decoration(TextDecoration.ITALIC, false);
                        player.sendMessage(line);
                    }

                    if (info.joinMode() == PartyManager.JoinMode.APPROVAL && info.leader().equals(player.getUniqueId())) {
                        partyManager.pendingRequests(info.id()).thenAccept(requests ->
                                Bukkit.getScheduler().runTask(plugin, () -> {
                                    if (requests.isEmpty()) {
                                        return;
                                    }
                                    player.sendMessage(Component.text("대기 중인 가입 신청:", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
                                    for (var request : requests) {
                                        player.sendMessage(Component.text("- " + request.name() + " (/파티 수락|거절 " + request.name() + ")", NamedTextColor.WHITE)
                                                .decoration(TextDecoration.ITALIC, false));
                                    }
                                }));
                    }
                }));
    }

    private void notifyOnlineMember(java.util.UUID uuid, String key, net.kyori.adventure.text.minimessage.tag.resolver.TagResolver placeholder) {
        if (uuid == null) {
            return;
        }
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            messages.send(online, key, placeholder);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return TabCompletions.filter(List.of("생성", "탈퇴", "삭제", "가입", "수락", "거절", "정보"), args[0]);
        }
        if (args.length == 2 && (args[0].equals("가입") || args[0].equals("수락") || args[0].equals("거절"))) {
            return List.of();
        }
        if (args.length == 2) {
            return TabCompletions.filter(List.of("2", "3", "4"), args[1]);
        }
        if (args.length == 3) {
            return TabCompletions.filter(List.of("자유가입", "신청승인"), args[2]);
        }
        return List.of();
    }
}
