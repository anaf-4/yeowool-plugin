package com.yeowool.federation;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

/** {@code /연합 폐쇄}'s one-more-step confirmation — same accept/deny pattern as yeowool-market's ConfirmPurchaseGui. */
public final class FederationDisbandConfirmGui extends YeowoolGui {

    private static final int SLOT_ACCEPT = 11;
    private static final int SLOT_DENY = 15;

    public FederationDisbandConfirmGui(JavaPlugin plugin, YeowoolCoreAPI core, FederationManager manager,
                                        ExecutorService executor, UUID actingLandId, String federationName) {
        super(27, Component.text("연합 폐쇄 확인", NamedTextColor.RED));

        setButton(SLOT_ACCEPT, GuiButton.of(acceptIcon(federationName), event -> {
            Player player = (Player) event.getWhoClicked();
            player.closeInventory();
            executor.execute(() -> {
                try {
                    var result = manager.disband(actingLandId);
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        switch (result) {
                            case SUCCESS -> core.messages().send(player, "federation.disband-success",
                                    Placeholder.unparsed("name", federationName));
                            case NOT_LEADER -> core.messages().send(player, "federation.leader-only");
                        }
                    });
                } catch (SQLException e) {
                    plugin.getLogger().severe("연합 폐쇄 실패: " + e.getMessage());
                }
            });
        }));

        setButton(SLOT_DENY, GuiButton.of(denyIcon(), event -> ((Player) event.getWhoClicked()).closeInventory()));
    }

    private ItemStack acceptIcon(String federationName) {
        ItemStack stack = new ItemStack(Material.LIME_DYE);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text("폐쇄 확정", NamedTextColor.GREEN, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("'" + federationName + "' 연합을 폐쇄합니다. 되돌릴 수 없습니다.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack denyIcon() {
        ItemStack stack = new ItemStack(Material.RED_DYE);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text("취소", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        stack.setItemMeta(meta);
        return stack;
    }
}
