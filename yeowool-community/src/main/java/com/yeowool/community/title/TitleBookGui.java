package com.yeowool.community.title;

import com.yeowool.community.display.PlayerIdentityService;
import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
import com.yeowool.core.api.service.MessageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** {@code /칭호북 목록} — page 1 of the grant wizard: pick a title, then {@link TitleGrantTargetGui} picks who gets it. */
public final class TitleBookGui extends YeowoolGui {

    private static final int PAGE_SIZE = 45;

    public TitleBookGui(YeowoolCoreAPI core, TitleManager titleManager, PlayerIdentityService identityService, MessageService messages, int page) {
        super(54, Component.text("칭호북 (페이지 " + (page + 1) + ")", NamedTextColor.DARK_AQUA));

        List<TitleDefinition> all = titleManager.all();
        int from = page * PAGE_SIZE;
        int to = Math.min(all.size(), from + PAGE_SIZE);

        for (int i = from; i < to; i++) {
            TitleDefinition title = all.get(i);
            setButton(i - from, GuiButton.of(buildIcon(title), event ->
                    new TitleGrantTargetGui(core, titleManager, identityService, messages, title, page)
                            .open((Player) event.getWhoClicked())));
        }

        if (all.isEmpty()) {
            setButton(22, GuiButton.display(navItem(Material.BARRIER, "생성된 칭호가 없습니다")));
        }
        if (page > 0) {
            setButton(45, GuiButton.of(navItem(Material.ARROW, "이전 페이지"), event ->
                    new TitleBookGui(core, titleManager, identityService, messages, page - 1).open((Player) event.getWhoClicked())));
        }
        setButton(49, GuiButton.of(navItem(Material.BARRIER, "닫기"), event -> event.getWhoClicked().closeInventory()));
        if (to < all.size()) {
            setButton(53, GuiButton.of(navItem(Material.ARROW, "다음 페이지"), event ->
                    new TitleBookGui(core, titleManager, identityService, messages, page + 1).open((Player) event.getWhoClicked())));
        }
    }

    private ItemStack navItem(Material material, String name) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack buildIcon(TitleDefinition title) {
        ItemStack stack = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(MiniMessage.miniMessage().deserialize(title.display()).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("ID: " + title.id(), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(title.isAutoUnlock() ? "통계 자동 해금 조건 있음" : "관리자 전용 (자동 해금 없음)", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("클릭하여 지급 대상 선택", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }
}
