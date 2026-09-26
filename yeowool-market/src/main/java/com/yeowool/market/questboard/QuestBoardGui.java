package com.yeowool.market.questboard;

import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
import com.yeowool.core.util.DurationFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** The board itself (opened from the quest board furniture): open requests newest first, click one to deliver what you hold. */
public final class QuestBoardGui extends YeowoolGui {

    private static final int SLOT_PREV = 45;
    private static final int SLOT_REGISTER = 49;
    private static final int SLOT_MINE = 50;
    private static final int SLOT_NEXT = 53;

    public QuestBoardGui(QuestBoardService service, List<QuestRequest> requests, int page, boolean hasNext) {
        super(54, Component.text("의뢰 게시판", NamedTextColor.DARK_GREEN));
        for (int i = 0; i < requests.size() && i < QuestBoardService.PAGE_SIZE; i++) {
            QuestRequest request = requests.get(i);
            setButton(i, GuiButton.of(icon(request, "클릭: 가진 만큼 납품"),
                    event -> service.deliver((Player) event.getWhoClicked(), request, page)));
        }
        if (page > 0) {
            setButton(SLOT_PREV, GuiButton.of(named(Material.ARROW, "이전 페이지", null),
                    event -> service.openBoard((Player) event.getWhoClicked(), page - 1)));
        }
        setButton(SLOT_REGISTER, GuiButton.of(named(Material.WRITABLE_BOOK, "의뢰 등록", "손에 든 아이템으로 의뢰를 올립니다"),
                event -> service.beginRegister((Player) event.getWhoClicked())));
        setButton(SLOT_MINE, GuiButton.of(named(Material.CHEST, "내 의뢰", "내가 올린 의뢰 확인·취소"),
                event -> service.openMine((Player) event.getWhoClicked())));
        if (hasNext) {
            setButton(SLOT_NEXT, GuiButton.of(named(Material.ARROW, "다음 페이지", null),
                    event -> service.openBoard((Player) event.getWhoClicked(), page + 1)));
        }
    }

    /** A copy of the requested item with the request's details appended to its lore; {@code action} may be null. */
    static ItemStack icon(QuestRequest request, String action) {
        ItemStack stack = request.sample().clone();
        ItemMeta meta = stack.getItemMeta();
        List<Component> lore = new ArrayList<>();
        if (meta.hasLore() && meta.lore() != null) {
            lore.addAll(meta.lore());
        }
        lore.add(line("의뢰 #" + request.id() + " · " + request.requesterName(), NamedTextColor.GRAY));
        lore.add(line("납품 " + String.format("%,d", request.delivered()) + " / " + String.format("%,d", request.quantity()), NamedTextColor.YELLOW));
        lore.add(line("개당 보상 " + String.format("%,d", request.rewardPerItem()) + "온", NamedTextColor.GOLD));
        if (request.isOpen()) {
            lore.add(line("남은 시간 " + DurationFormat.humanize(Math.max(0, request.expiresAt() - System.currentTimeMillis())), NamedTextColor.AQUA));
        } else {
            lore.add(line(statusLabel(request.status()), NamedTextColor.DARK_GRAY));
        }
        if (action != null) {
            lore.add(line(action, NamedTextColor.GREEN));
        }
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    static ItemStack named(Material material, String name, String description) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(line(name, NamedTextColor.WHITE));
        if (description != null) {
            meta.lore(List.of(line(description, NamedTextColor.GRAY)));
        }
        stack.setItemMeta(meta);
        return stack;
    }

    private static String statusLabel(String status) {
        return switch (status) {
            case "COMPLETED" -> "완료됨";
            case "CANCELLED" -> "취소됨";
            case "EXPIRED" -> "만료됨";
            default -> status;
        };
    }

    private static Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }
}
