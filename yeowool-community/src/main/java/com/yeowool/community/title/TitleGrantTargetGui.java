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
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Page 2 of the {@code /칭호북 목록} grant wizard — pick an online player to
 * grant {@code title} to. Only online targets: see {@link TitleBookCommand}
 * for why offline granting stays on the {@code /칭호 지급} chat command.
 */
public final class TitleGrantTargetGui extends YeowoolGui {

    private static final int PAGE_SIZE = 36;
    private static final int SLOT_TITLE_INFO = 4;

    public TitleGrantTargetGui(YeowoolCoreAPI core, TitleManager titleManager, PlayerIdentityService identityService,
                                MessageService messages, TitleDefinition title, int backPage) {
        super(54, Component.text("지급 대상 선택", NamedTextColor.DARK_AQUA));

        setButton(SLOT_TITLE_INFO, GuiButton.display(buildTitleInfoIcon(title)));

        List<Player> online = List.copyOf(Bukkit.getOnlinePlayers());
        int from = 0;
        int to = Math.min(online.size(), PAGE_SIZE);
        for (int i = from; i < to; i++) {
            Player target = online.get(i);
            setButton(9 + i, GuiButton.of(buildPlayerIcon(target), event -> {
                Player admin = (Player) event.getWhoClicked();
                var data = core.playerData().getOnline(target.getUniqueId());
                if (titleManager.grant(data, title.id())) {
                    messages.send(admin, "title.grant-success",
                            Placeholder.unparsed("player", target.getName()),
                            Placeholder.parsed("title", title.display()));
                } else {
                    messages.send(admin, "title.unknown-title");
                }
                admin.closeInventory();
            }));
        }
        if (online.isEmpty()) {
            setButton(22, GuiButton.display(navItem(Material.BARRIER, "온라인 플레이어가 없습니다")));
        }

        setButton(49, GuiButton.of(navItem(Material.ARROW, "뒤로가기"), event ->
                new TitleBookGui(core, titleManager, identityService, messages, backPage).open((Player) event.getWhoClicked())));
    }

    private ItemStack navItem(Material material, String name) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack buildTitleInfoIcon(TitleDefinition title) {
        ItemStack stack = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(MiniMessage.miniMessage().deserialize(title.display()).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("이 칭호를 아래에서 선택한 플레이어에게 지급합니다.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack buildPlayerIcon(Player target) {
        ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) stack.getItemMeta();
        meta.setOwningPlayer(target);
        meta.displayName(Component.text(target.getName(), NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("클릭하여 지급", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }
}
