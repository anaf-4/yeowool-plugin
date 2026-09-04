package com.yeowool.core.help;

import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /길라잡이 목록} — admin view of every numbered onboarding mission.
 * Clicking one doesn't delete/edit it directly (a mission's text is easy to
 * get wrong from a bare click with no further input) — it just prints the
 * exact {@code /길라잡이 수정}/{@code 제거} commands to run, same as {@code
 * LandListGui} does for irreversible land actions.
 */
public final class GuideMissionListGui extends YeowoolGui {

    private static final int PAGE_SIZE = 45;

    public GuideMissionListGui(GuideMissionManager missionManager) {
        this(missionManager, 0);
    }

    public GuideMissionListGui(GuideMissionManager missionManager, int page) {
        super(54, Component.text("길라잡이 미션 관리 (페이지 " + (page + 1) + ")", NamedTextColor.DARK_AQUA));

        List<GuideMission> all = missionManager.all();
        int from = page * PAGE_SIZE;
        int to = Math.min(all.size(), from + PAGE_SIZE);

        for (int i = from; i < to; i++) {
            GuideMission mission = all.get(i);
            setButton(i - from, GuiButton.of(buildIcon(mission), event -> {
                Player viewer = (Player) event.getWhoClicked();
                viewer.sendMessage(Component.text("수정: ", NamedTextColor.GRAY)
                        .append(Component.text("/길라잡이 수정 " + mission.number() + " " + mission.title() + " <새 행동>", NamedTextColor.YELLOW)));
                viewer.sendMessage(Component.text("제거: ", NamedTextColor.GRAY)
                        .append(Component.text("/길라잡이 제거 " + mission.number(), NamedTextColor.YELLOW)));
            }));
        }

        if (all.isEmpty()) {
            setButton(22, GuiButton.display(navItem(Material.BARRIER, "등록된 미션이 없습니다")));
        }
        if (page > 0) {
            setButton(45, GuiButton.of(navItem(Material.ARROW, "이전 페이지"), event ->
                    new GuideMissionListGui(missionManager, page - 1).open((Player) event.getWhoClicked())));
        }
        setButton(49, GuiButton.of(navItem(Material.BARRIER, "닫기"), event -> event.getWhoClicked().closeInventory()));
        if (to < all.size()) {
            setButton(53, GuiButton.of(navItem(Material.ARROW, "다음 페이지"), event ->
                    new GuideMissionListGui(missionManager, page + 1).open((Player) event.getWhoClicked())));
        }
    }

    private ItemStack navItem(Material material, String name) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack buildIcon(GuideMission mission) {
        ItemStack stack = new ItemStack(Material.PAPER);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(mission.number() + ". " + mission.title(), NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(mission.action(), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("클릭하면 수정/제거 명령어를 안내합니다.", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }
}
