package com.yeowool.market.exchange;

import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
import com.yeowool.market.exchange.ExchangeRepository.Entry;
import com.yeowool.market.exchange.ExchangeRepository.Limit;
import com.yeowool.market.util.ItemResolver;
import dev.lone.itemsadder.api.CustomStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Player view: 45 entries per page, 별조각 balance in the bottom row. Clicks go to {@link ExchangeService#exchange}. */
final class ExchangeGui extends YeowoolGui {

    private static final int PER_PAGE = 45;
    private static final String STARDUST_ITEM = "yeowool_market:stardust";

    ExchangeGui(ExchangeService service, List<Entry> entries, Map<String, Integer> usage, long stardust, LocalDate today, int page) {
        super(54, Component.text("교환소", NamedTextColor.DARK_PURPLE));
        int pages = Math.max(1, (entries.size() + PER_PAGE - 1) / PER_PAGE);
        int current = Math.min(Math.max(page, 0), pages - 1);
        for (int i = 0; i < PER_PAGE && current * PER_PAGE + i < entries.size(); i++) {
            Entry entry = entries.get(current * PER_PAGE + i);
            String period = ExchangeRepository.periodKey(entry.limit(), today);
            int used = period == null ? 0 : usage.getOrDefault(entry.id() + "|" + period, 0);
            setButton(i, GuiButton.of(icon(entry, used), event ->
                    service.exchange((Player) event.getWhoClicked(), entry, current)));
        }
        if (current > 0) {
            setButton(45, GuiButton.of(named(new ItemStack(Material.ARROW), "이전 페이지", NamedTextColor.YELLOW),
                    event -> service.open((Player) event.getWhoClicked(), current - 1)));
        }
        if (current < pages - 1) {
            setButton(53, GuiButton.of(named(new ItemStack(Material.ARROW), "다음 페이지", NamedTextColor.YELLOW),
                    event -> service.open((Player) event.getWhoClicked(), current + 1)));
        }
        ItemStack balance = named(stardustIcon(), "보유 별조각: " + String.format("%,d", stardust) + "개", NamedTextColor.LIGHT_PURPLE);
        lore(balance, List.of(line("월드보스·생활 대회·일일/주간 퀘스트·보물지도·의뢰 납품으로 모읍니다.", NamedTextColor.GRAY)));
        setButton(49, GuiButton.display(balance));
    }

    private static ItemStack icon(Entry entry, int used) {
        ItemStack icon = entry.reward().clone();
        ItemMeta meta = icon.getItemMeta();
        List<Component> lore = new ArrayList<>();
        if (meta != null && meta.lore() != null) {
            lore.addAll(meta.lore());
        }
        lore.add(Component.empty());
        lore.add(line("필요한 것", NamedTextColor.GRAY));
        for (ExchangeService.Cost cost : ExchangeService.mergedCosts(entry)) {
            lore.add(Component.text("· ", NamedTextColor.WHITE).append(cost.sample().effectiveName())
                    .append(Component.text(" ×" + cost.amount())).decoration(TextDecoration.ITALIC, false));
        }
        if (entry.costMoney() > 0) {
            lore.add(line("· " + String.format("%,d", entry.costMoney()) + "온", NamedTextColor.GOLD));
        }
        if (entry.costStardust() > 0) {
            lore.add(line("· ✦ 별조각 " + String.format("%,d", entry.costStardust()) + "개", NamedTextColor.LIGHT_PURPLE));
        }
        boolean full = false;
        if (entry.limit() != Limit.NONE) {
            full = used >= entry.limitCount();
            lore.add(line(entry.limit().label() + " 교환 " + used + "/" + entry.limitCount() + "회", full ? NamedTextColor.RED : NamedTextColor.AQUA));
        }
        lore.add(Component.empty());
        if (ExchangeService.freeAndUnlimited(entry)) {
            lore.add(line("교환할 수 없는 항목입니다 (관리진 설정 필요)", NamedTextColor.RED));
        } else {
            lore.add(full ? line("교환 한도에 도달했습니다", NamedTextColor.RED) : line("클릭하여 교환", NamedTextColor.GREEN));
        }
        lore(icon, lore);
        return icon;
    }

    private static ItemStack stardustIcon() {
        if (ItemResolver.isItemsAdderAvailable()) {
            CustomStack custom = CustomStack.getInstance(STARDUST_ITEM);
            if (custom != null) {
                return custom.getItemStack();
            }
        }
        return new ItemStack(Material.NETHER_STAR);
    }

    private static ItemStack named(ItemStack item, String name, NamedTextColor color) {
        ItemMeta meta = item.getItemMeta();
        meta.displayName(line(name, color));
        item.setItemMeta(meta);
        return item;
    }

    private static void lore(ItemStack item, List<Component> lore) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.lore(lore);
            item.setItemMeta(meta);
        }
    }

    private static Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }
}
