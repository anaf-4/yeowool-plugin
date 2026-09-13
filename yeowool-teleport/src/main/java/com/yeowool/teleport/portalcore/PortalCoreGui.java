package com.yeowool.teleport.portalcore;

import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Portal Core 아이템 우클릭 메뉴. 원래 Nexo 리소스팩 + DeluxeMenus GUI로 판매되던
 * 에셋을 ItemsAdder 리소스팩(원본과 동일한 CustomModelData 100001~100007)과 이
 * 자체 GUI 프레임워크로 옮긴 것 — 2~4페이지는 원본 그대로 이전/다음 버튼만 있는
 * 빈 틀이라(구매자가 직접 채워 넣는 템플릿), 내용 없이 그대로 유지.
 */
public final class PortalCoreGui extends YeowoolGui {

    private static final int PAGE_COUNT = 5;
    private static final int PREV_SLOT = 0;
    private static final int NEXT_SLOT = 8;
    private static final int ARROW_LEFT_CMD = 100003;
    private static final int ARROW_RIGHT_CMD = 100002;

    public PortalCoreGui(int page) {
        super(9, Component.text("포탈 코어", NamedTextColor.LIGHT_PURPLE));

        if (page > 1) {
            setButton(PREV_SLOT, GuiButton.of(arrow(ARROW_LEFT_CMD, "이전 페이지"), event ->
                    new PortalCoreGui(page - 1).open((Player) event.getWhoClicked())));
        }
        if (page < PAGE_COUNT) {
            setButton(NEXT_SLOT, GuiButton.of(arrow(ARROW_RIGHT_CMD, "다음 페이지"), event ->
                    new PortalCoreGui(page + 1).open((Player) event.getWhoClicked())));
        }
    }

    private ItemStack arrow(int customModelData, String name) {
        ItemStack stack = new ItemStack(Material.COAL);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.AQUA));
        meta.setCustomModelData(customModelData);
        stack.setItemMeta(meta);
        return stack;
    }
}
