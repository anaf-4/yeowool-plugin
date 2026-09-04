package com.yeowool.community.friend;

import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
import com.yeowool.core.api.service.MessageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * {@code /친구 목록} — paginated friend list (previously one long comma-joined
 * chat line). {@link FriendManager} keeps no in-memory friend cache — every
 * read is its own DB query — so a remove-click here mutates this GUI's own
 * copy of the list and reopens with it rather than re-querying immediately
 * after, since the DB delete happens asynchronously and might not have
 * landed yet if we asked the database again right away.
 */
public final class FriendListGui extends YeowoolGui {

    private static final int PAGE_SIZE = 45;

    public FriendListGui(FriendManager friendManager, MessageService messages, List<UUID> friends, int page) {
        super(54, Component.text("친구 목록 (페이지 " + (page + 1) + ")", NamedTextColor.DARK_AQUA));

        List<UUID> sorted = friends.stream()
                .sorted(Comparator.<UUID>comparingInt(uuid -> Bukkit.getOfflinePlayer(uuid).isOnline() ? 0 : 1)
                        .thenComparing(uuid -> nameOf(uuid), String.CASE_INSENSITIVE_ORDER))
                .toList();
        int from = page * PAGE_SIZE;
        int to = Math.min(sorted.size(), from + PAGE_SIZE);

        for (int i = from; i < to; i++) {
            UUID friendId = sorted.get(i);
            setButton(i - from, GuiButton.of(buildIcon(friendId), event -> {
                Player viewer = (Player) event.getWhoClicked();
                friendManager.removeFriend(viewer.getUniqueId(), friendId);
                List<UUID> updated = new ArrayList<>(friends);
                updated.remove(friendId);
                messages.send(viewer, "friend.remove-success", Placeholder.unparsed("target", nameOf(friendId)));
                new FriendListGui(friendManager, messages, updated, page).open(viewer);
            }));
        }

        if (sorted.isEmpty()) {
            setButton(22, GuiButton.display(navItem(Material.BARRIER, "친구가 없습니다")));
        }
        if (page > 0) {
            setButton(45, GuiButton.of(navItem(Material.ARROW, "이전 페이지"), event ->
                    new FriendListGui(friendManager, messages, friends, page - 1).open((Player) event.getWhoClicked())));
        }
        setButton(49, GuiButton.of(navItem(Material.BARRIER, "닫기"), event -> event.getWhoClicked().closeInventory()));
        if (to < sorted.size()) {
            setButton(53, GuiButton.of(navItem(Material.ARROW, "다음 페이지"), event ->
                    new FriendListGui(friendManager, messages, friends, page + 1).open((Player) event.getWhoClicked())));
        }
    }

    private ItemStack navItem(Material material, String name) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack buildIcon(UUID friendId) {
        OfflinePlayer offline = Bukkit.getOfflinePlayer(friendId);
        boolean online = offline.isOnline();
        ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) stack.getItemMeta();
        meta.setOwningPlayer(offline);
        meta.displayName(Component.text(nameOf(friendId), online ? NamedTextColor.GREEN : NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("상태: " + (online ? "온라인" : "오프라인"), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("클릭하여 친구 삭제", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private static String nameOf(UUID uuid) {
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        return name != null ? name : "알 수 없음";
    }
}
