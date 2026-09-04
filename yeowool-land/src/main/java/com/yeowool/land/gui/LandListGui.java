package com.yeowool.land.gui;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
import com.yeowool.land.LandManager;
import com.yeowool.land.model.Land;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /토지 관리 목록} ({@code yeowool.land.bypass} 전용) — 서버 전체 토지를
 * 청크 수 내림차순으로 페이지네이션해 보여준다 (이전엔 채팅 텍스트로만 10개씩
 * 출력했음). 클릭해도 아무것도 삭제/이전되지 않는다 — 해당 토지에 실행할 수
 * 있는 명령어를 채팅으로 안내만 한다. 삭제/소유권 이전은 되돌릴 수 없는
 * 작업이라, 목록 화면에서 한 클릭으로 바로 실행되게 두지 않기 위함이다.
 */
public final class LandListGui extends YeowoolGui {

    private static final int PAGE_SIZE = 45;

    public LandListGui(YeowoolCoreAPI core, LandManager landManager, int page) {
        super(54, Component.text("토지 관리 (페이지 " + (page + 1) + ")", NamedTextColor.DARK_AQUA));

        List<Land> all = landManager.all().stream()
                .sorted((a, b) -> Integer.compare(b.getChunkCount(), a.getChunkCount()))
                .toList();
        int from = page * PAGE_SIZE;
        int to = Math.min(all.size(), from + PAGE_SIZE);

        for (int i = from; i < to; i++) {
            Land land = all.get(i);
            String id = shortId(land);
            setButton(i - from, GuiButton.of(buildIcon(core, land), event -> {
                Player viewer = (Player) event.getWhoClicked();
                viewer.sendMessage(Component.text("삭제: ", NamedTextColor.GRAY)
                        .append(Component.text("/토지 관리 삭제 " + id, NamedTextColor.YELLOW)));
                viewer.sendMessage(Component.text("이전: ", NamedTextColor.GRAY)
                        .append(Component.text("/토지 관리 이전 " + id + " <닉네임>", NamedTextColor.YELLOW)));
            }));
        }

        if (page > 0) {
            setButton(45, GuiButton.of(navItem(Material.ARROW, "이전 페이지"), event ->
                    new LandListGui(core, landManager, page - 1).open((Player) event.getWhoClicked())));
        }
        setButton(49, GuiButton.of(navItem(Material.BARRIER, "닫기"), event -> event.getWhoClicked().closeInventory()));
        if (to < all.size()) {
            setButton(53, GuiButton.of(navItem(Material.ARROW, "다음 페이지"), event ->
                    new LandListGui(core, landManager, page + 1).open((Player) event.getWhoClicked())));
        }
    }

    private ItemStack navItem(Material material, String name) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack buildIcon(YeowoolCoreAPI core, Land land) {
        ItemStack stack = new ItemStack(Material.GRASS_BLOCK);
        ItemMeta meta = stack.getItemMeta();
        String ownerName = Bukkit.getOfflinePlayer(land.getOwner()).getName();
        meta.displayName(Component.text(land.getName() != null ? land.getName() : "이름없는 마을", NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("ID: " + shortId(land), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("소유자: " + (ownerName != null ? ownerName : "알 수 없음"), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("청크 수: " + land.getChunkCount(), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("토지 레벨: " + core.landStats().getLandLevel(land.getOwner()), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("멤버 수: " + land.getMembers().size(), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("클릭하여 관리 명령어 보기", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private String shortId(Land land) {
        return land.getId().toString().substring(0, 8);
    }
}
