package com.yeowool.admin.logviewer;

import com.yeowool.admin.logviewer.LogQueryService.LogRow;
import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code /여울관리 로그 <닉네임>} — browses one player's {@code yw_logs}
 * rows with a category filter and pagination, instead of dumping up to 100
 * chat lines at once. All rows for the target are fetched once up front
 * (see {@code AdminCommand}) and filtering/paging happens entirely
 * in-memory here, so cycling the category filter or flipping pages never
 * needs another database round trip.
 */
public final class LogViewerGui extends YeowoolGui {

    private static final int PAGE_SIZE = 45;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final String ALL_CATEGORIES = "전체";

    public LogViewerGui(String targetName, List<LogRow> allRows, List<String> categories, String activeCategory, int page) {
        super(54, Component.text(targetName + "님의 로그 (" + activeCategory + ", 페이지 " + (page + 1) + ")", NamedTextColor.DARK_AQUA));

        List<LogRow> filtered = ALL_CATEGORIES.equals(activeCategory)
                ? allRows
                : allRows.stream().filter(row -> activeCategory.equals(row.category())).toList();

        int from = page * PAGE_SIZE;
        int to = Math.min(filtered.size(), from + PAGE_SIZE);
        for (int i = from; i < to; i++) {
            setButton(i - from, GuiButton.display(buildIcon(filtered.get(i))));
        }
        if (filtered.isEmpty()) {
            setButton(22, GuiButton.display(navItem(Material.BARRIER, "기록이 없습니다", NamedTextColor.GRAY)));
        }

        if (page > 0) {
            setButton(45, GuiButton.of(navItem(Material.ARROW, "이전 페이지", NamedTextColor.GRAY), event ->
                    new LogViewerGui(targetName, allRows, categories, activeCategory, page - 1).open((Player) event.getWhoClicked())));
        }
        setButton(47, GuiButton.of(buildFilterIcon(activeCategory), event ->
                new LogViewerGui(targetName, allRows, categories, nextCategory(categories, activeCategory), 0).open((Player) event.getWhoClicked())));
        setButton(51, GuiButton.display(buildInfoIcon(targetName, filtered.size(), allRows.size())));
        setButton(49, GuiButton.of(navItem(Material.BARRIER, "닫기", NamedTextColor.RED), event -> event.getWhoClicked().closeInventory()));
        if (to < filtered.size()) {
            setButton(53, GuiButton.of(navItem(Material.ARROW, "다음 페이지", NamedTextColor.GRAY), event ->
                    new LogViewerGui(targetName, allRows, categories, activeCategory, page + 1).open((Player) event.getWhoClicked())));
        }
    }

    /** {@code categories} is every distinct category seen in the fetched rows, in insertion order; cycles 전체 → each category → back to 전체. */
    private String nextCategory(List<String> categories, String current) {
        if (ALL_CATEGORIES.equals(current)) {
            return categories.isEmpty() ? ALL_CATEGORIES : categories.get(0);
        }
        int index = categories.indexOf(current);
        if (index < 0 || index == categories.size() - 1) {
            return ALL_CATEGORIES;
        }
        return categories.get(index + 1);
    }

    private ItemStack buildFilterIcon(String activeCategory) {
        ItemStack stack = new ItemStack(Material.COMPASS);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text("필터: " + activeCategory, NamedTextColor.YELLOW));
        meta.lore(List.of(Component.text("클릭하여 다음 카테고리로 전환", NamedTextColor.GRAY)));
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack buildInfoIcon(String targetName, int filteredCount, int totalCount) {
        ItemStack stack = new ItemStack(Material.BOOK);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(targetName, NamedTextColor.GOLD));
        meta.lore(List.of(
                Component.text("표시: " + filteredCount + "건", NamedTextColor.GRAY),
                Component.text("전체 불러온 기록: " + totalCount + "건", NamedTextColor.DARK_GRAY)));
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack navItem(Material material, String name, NamedTextColor color) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name, color));
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack buildIcon(LogRow row) {
        ItemStack stack = new ItemStack(Material.PAPER);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(row.category(), NamedTextColor.AQUA));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(TIME_FORMAT.format(Instant.ofEpochMilli(row.createdAt())), NamedTextColor.DARK_GRAY));
        lore.add(Component.text(row.pluginName(), NamedTextColor.GRAY));
        lore.add(Component.text(row.message(), NamedTextColor.WHITE));
        if (row.data() != null && !row.data().isBlank()) {
            lore.add(Component.text(row.data(), NamedTextColor.DARK_GRAY));
        }
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }
}
