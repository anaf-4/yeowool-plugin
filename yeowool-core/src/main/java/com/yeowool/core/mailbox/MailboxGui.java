package com.yeowool.core.mailbox;

import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
import com.yeowool.core.api.model.MailboxEntry;
import com.yeowool.core.api.service.MailboxService;
import com.yeowool.core.api.service.MessageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code /우편함} — claim items a plugin couldn't hand over directly (offline
 * recipient, full inventory). Every entry shows as a closed
 * {@code yeowool_mailbox:unopened_box} (converted from the "DailyRewards-0.2.3"
 * pack) rather than the real item, so it stays a surprise until opened —
 * right-click peeks at the contents via {@link MailboxPreviewGui} without
 * claiming it, left-click claims it. Background {@code yeowool_mailbox:mailbox_bg}.
 */
public final class MailboxGui extends YeowoolGui {

    private static final int PAGE_SIZE = 45;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault());

    public MailboxGui(JavaPlugin plugin, MailboxService mailbox, MessageService messages, List<MailboxEntry> entries, int page) {
        this(plugin, mailbox, messages, entries, page, -8);
    }

    public MailboxGui(JavaPlugin plugin, MailboxService mailbox, MessageService messages, List<MailboxEntry> entries, int page, int backgroundOffsetPx) {
        super(54, MailboxBackgroundImages.title(backgroundOffsetPx, "mailbox_bg",
                Component.text("우편함 (페이지 " + (page + 1) + ")", NamedTextColor.DARK_AQUA)));

        int from = page * PAGE_SIZE;
        int to = Math.min(entries.size(), from + PAGE_SIZE);

        for (int i = from; i < to; i++) {
            MailboxEntry entry = entries.get(i);
            setButton(i - from, GuiButton.of(buildIcon(entry), event -> {
                Player player = (Player) event.getWhoClicked();
                if (event.getClick() == ClickType.RIGHT || event.getClick() == ClickType.SHIFT_RIGHT) {
                    new MailboxPreviewGui(entry).open(player);
                    return;
                }
                mailbox.claim(player.getUniqueId(), entry.id()).thenAccept(claimed -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (claimed.isEmpty()) {
                        messages.send(player, "mailbox.already-claimed");
                        return;
                    }
                    var leftover = player.getInventory().addItem(claimed.get());
                    leftover.values().forEach(extra -> player.getWorld().dropItemNaturally(player.getLocation(), extra));
                    messages.send(player, "mailbox.claim-success");
                    mailbox.loadPending(player.getUniqueId()).thenAccept(refreshed ->
                            Bukkit.getScheduler().runTask(plugin, () -> new MailboxGui(plugin, mailbox, messages, refreshed, 0, backgroundOffsetPx).open(player)));
                }));
            }));
        }

        if (page > 0) {
            setButton(45, GuiButton.of(navIcon("yeowool_mailbox:arrow_previous", Material.ARROW, "이전 페이지"), event ->
                    new MailboxGui(plugin, mailbox, messages, entries, page - 1, backgroundOffsetPx).open((Player) event.getWhoClicked())));
        }
        if (to < entries.size()) {
            setButton(53, GuiButton.of(navIcon("yeowool_mailbox:arrow_next", Material.ARROW, "다음 페이지"), event ->
                    new MailboxGui(plugin, mailbox, messages, entries, page + 1, backgroundOffsetPx).open((Player) event.getWhoClicked())));
        }
    }

    private ItemStack navIcon(String customIconId, Material fallback, String name) {
        ItemStack stack = MailboxBackgroundImages.icon(customIconId);
        if (stack == null) {
            stack = new ItemStack(fallback);
        }
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack buildIcon(MailboxEntry entry) {
        ItemStack stack = MailboxBackgroundImages.icon("yeowool_mailbox:unopened_box");
        if (stack == null) {
            stack = new ItemStack(Material.CHEST);
        }
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text("받지 않은 우편", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("출처: " + entry.sourcePlugin(), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        if (entry.note() != null && !entry.note().isBlank()) {
            lore.add(Component.text(entry.note(), NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.text("수신: " + TIME_FORMAT.format(Instant.ofEpochMilli(entry.createdAt())), NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("우클릭: 내용물 미리보기", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("좌클릭: 수령", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }
}
