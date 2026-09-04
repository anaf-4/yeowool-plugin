package com.yeowool.community.cosmetic;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * {@code /코스메틱} — browse and buy/equip cosmetics (plugin plan 10.2).
 * Clicking an unowned one buys it; clicking an owned one toggles
 * equip/unequip. Purely decorative — see {@link CosmeticManager}.
 */
public final class CosmeticShopGui extends YeowoolGui {

    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public CosmeticShopGui(YeowoolCoreAPI core, CosmeticManager manager) {
        super(Math.max(9, ((manager.all().size() / 9) + 1) * 9), Component.text("코스메틱 상점", NamedTextColor.LIGHT_PURPLE));

        int slot = 0;
        for (CosmeticDefinition cosmetic : manager.all()) {
            setButton(slot++, GuiButton.of(buildIcon(core, manager, cosmetic), event -> {
                Player player = (Player) event.getWhoClicked();
                var data = core.playerData().getOnline(player.getUniqueId());

                if (!manager.isUnlocked(data, cosmetic.id())) {
                    if (manager.purchase(player, cosmetic)) {
                        core.sounds().play(player, "success");
                        player.sendMessage(Component.text("구매했습니다: " + cosmetic.display(), NamedTextColor.GREEN));
                        new CosmeticShopGui(core, manager).open(player);
                    } else {
                        core.sounds().play(player, "error");
                        player.sendMessage(Component.text("온이 부족합니다.", NamedTextColor.RED));
                    }
                    return;
                }

                boolean currentlyEquipped = manager.equipped(data, cosmetic.type())
                        .map(equipped -> equipped.id().equals(cosmetic.id())).orElse(false);
                if (currentlyEquipped) {
                    manager.unequip(player, cosmetic.type());
                    player.sendMessage(Component.text("해제했습니다: " + cosmetic.display(), NamedTextColor.GRAY));
                } else {
                    manager.equip(player, cosmetic);
                    player.sendMessage(Component.text("장착했습니다: " + cosmetic.display(), NamedTextColor.GREEN));
                }
                new CosmeticShopGui(core, manager).open(player);
            }));
        }
    }

    private ItemStack buildIcon(YeowoolCoreAPI core, CosmeticManager manager, CosmeticDefinition cosmetic) {
        Material material = cosmetic.type() == CosmeticDefinition.Type.PARTICLE ? Material.BLAZE_POWDER : Material.NAME_TAG;
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(miniMessage.deserialize(cosmetic.display()));
        meta.lore(List.of(
                Component.text("가격: " + String.format("%,d", cosmetic.price()) + "온", NamedTextColor.GOLD),
                Component.text(cosmetic.type() == CosmeticDefinition.Type.PARTICLE ? "종류: 이동 파티클" : "종류: 채팅 이름 색상", NamedTextColor.GRAY)
        ));
        stack.setItemMeta(meta);
        return stack;
    }
}
