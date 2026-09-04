package com.yeowool.admin.banneditem;

import com.yeowool.core.api.service.MessageService;
import com.yeowool.core.util.TabCompletions;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Backs both {@code /아이템밴} ({@link BanType#POSSESSION}) and {@code /조합밴}
 * ({@link BanType#CRAFT}) — same 추가/제거/목록 shape, only the ban type and
 * display name differ, so one class handles both (constructed twice in
 * {@code YeowoolAdmin}).
 */
public final class BannedItemCommand implements CommandExecutor, TabCompleter {

    private final MessageService messages;
    private final BannedItemManager manager;
    private final BannedItemListener listener;
    private final BanType type;

    public BannedItemCommand(MessageService messages, BannedItemManager manager, BannedItemListener listener, BanType type) {
        this.messages = messages;
        this.manager = manager;
        this.listener = listener;
        this.type = type;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return true;
        }
        if (args.length == 0) {
            messages.send(player, "banneditem.usage", Placeholder.unparsed("command", label));
            return true;
        }

        switch (args[0]) {
            case "추가" -> add(player);
            case "제거" -> remove(player);
            case "목록" -> list(player);
            default -> messages.send(player, "banneditem.usage", Placeholder.unparsed("command", label));
        }
        return true;
    }

    private void add(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            messages.send(player, "banneditem.hand-empty");
            return;
        }
        Material material = hand.getType();
        if (!manager.ban(material, type, player.getName())) {
            messages.send(player, "banneditem.already-banned",
                    Placeholder.unparsed("type", type.displayName()), Placeholder.unparsed("item", material.name()));
            return;
        }
        if (type == BanType.POSSESSION) {
            listener.confiscateFromAllOnline(material);
        }
        messages.send(player, "banneditem.added",
                Placeholder.unparsed("type", type.displayName()), Placeholder.unparsed("item", material.name()));
    }

    private void remove(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            messages.send(player, "banneditem.hand-empty");
            return;
        }
        Material material = hand.getType();
        if (!manager.unban(material, type)) {
            messages.send(player, "banneditem.not-banned",
                    Placeholder.unparsed("type", type.displayName()), Placeholder.unparsed("item", material.name()));
            return;
        }
        messages.send(player, "banneditem.removed",
                Placeholder.unparsed("type", type.displayName()), Placeholder.unparsed("item", material.name()));
    }

    private void list(Player player) {
        var banned = manager.list(type);
        if (banned.isEmpty()) {
            messages.send(player, "banneditem.list-empty", Placeholder.unparsed("type", type.displayName()));
            return;
        }
        String joined = banned.stream().map(Material::name).sorted().collect(Collectors.joining(", "));
        messages.send(player, "banneditem.list",
                Placeholder.unparsed("type", type.displayName()), Placeholder.unparsed("items", joined));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return TabCompletions.filter(List.of("추가", "제거", "목록"), args[0]);
        }
        return List.of();
    }
}
