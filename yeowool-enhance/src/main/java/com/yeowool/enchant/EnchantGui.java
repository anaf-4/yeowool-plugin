package com.yeowool.enchant;

import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
import com.yeowool.core.api.service.MessageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * {@code /인챈트강화}'s Enchanter/hub screen — the exact 36-slot layout of
 * AdvancedEnchantments' own {@code menus/enchanter.yml}: 6 tier-buy buttons
 * at slots 2/3/4/5/6/13, a repeated "open Tinkerer" icon filling the 2x4
 * block at 18/19/20/21/27/28/29/30, and a repeated "open Alchemist" icon
 * filling the 2x4 block at 23/24/25/26/32/33/34/35 (column 22/31 is a
 * deliberate gap in the original, same as here). No close button — the real
 * enchanter.yml doesn't define one either. Background
 * {@code yeowool_enhance:enhance_bg} at offset -32, matching AE's own
 * {@code name: '&f:offset_-32::ae_enchanter:'}.
 */
public final class EnchantGui extends YeowoolGui {

    private static final int[] TIER_SLOTS = {2, 3, 4, 5, 6, 13};
    private static final int[] TINKERER_SLOTS = {18, 19, 20, 21, 27, 28, 29, 30};
    private static final int[] ALCHEMIST_SLOTS = {23, 24, 25, 26, 32, 33, 34, 35};

    private final EnchantService service;
    private final MessageService messages;

    public EnchantGui(EnchantService service, MessageService messages) {
        super(36, EnchantBackgroundImages.title(service.config().backgroundOffsetPx(), "enhance_bg",
                Component.text("서버 엔챈터", NamedTextColor.LIGHT_PURPLE)));
        this.service = service;
        this.messages = messages;

        EnchantTier[] tiers = EnchantTier.values();
        for (int i = 0; i < TIER_SLOTS.length && i < tiers.length; i++) {
            EnchantTier tier = tiers[i];
            setButton(TIER_SLOTS[i], GuiButton.of(buyIcon(tier), event -> buy((Player) event.getWhoClicked(), tier)));
        }

        ItemStack tinkererIcon = navIcon("틴커러", "인챈트북을 분해해서", "마법 가루와 온을 돌려받습니다.");
        for (int slot : TINKERER_SLOTS) {
            setButton(slot, GuiButton.of(tinkererIcon, event -> {
                Player clicker = (Player) event.getWhoClicked();
                new EnchantTinkererGui(service, messages, clicker).open(clicker);
            }));
        }
        ItemStack alchemistIcon = navIcon("알케미스트", "같은 등급 인챈트북 여러 개와", "마법 가루를 모아 상위 등급으로 조합합니다.");
        for (int slot : ALCHEMIST_SLOTS) {
            setButton(slot, GuiButton.of(alchemistIcon, event -> {
                Player clicker = (Player) event.getWhoClicked();
                new EnchantAlchemistGui(service, messages, clicker).open(clicker);
            }));
        }
    }

    private void buy(Player player, EnchantTier tier) {
        var result = service.buy(player, tier);
        switch (result) {
            case SUCCESS -> {
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                messages.send(player, "inchant.buy-success", Placeholder.unparsed("tier", tier.displayName()));
            }
            case INSUFFICIENT_FUNDS -> messages.send(player, "inchant.insufficient-funds",
                    Placeholder.unparsed("cost", String.format("%,d", service.config().settingFor(tier).cost())));
        }
    }

    private ItemStack buyIcon(EnchantTier tier) {
        ItemStack stack = service.items().previewIcon(tier);
        ItemMeta meta = stack.getItemMeta();
        var setting = service.config().settingFor(tier);
        meta.displayName(Component.text(tier.displayName() + " 인챈트북 (우클릭)", tier.color(), TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("무작위 " + tier.displayName() + " 등급 인챈트북을 받습니다.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text("가격: " + String.format("%,d", setting.cost()) + "온", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false),
                Component.text("클릭하여 구매", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false)));
        stack.setItemMeta(meta);
        return stack;
    }

    /** Invisible ({@code yeowool_enhance:enhance_invisible}, AE's own air.png) — the TINKERER/ALCHEMIST labels are already painted into the background image, so a visible icon on top would just clutter it. */
    private ItemStack navIcon(String name, String... loreLines) {
        ItemStack stack = EnchantIcons.resolveCustom("yeowool_enhance:enhance_invisible");
        if (stack == null) {
            stack = new ItemStack(Material.GLASS_PANE);
        }
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.AQUA, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(loreLines).stream()
                .map(line -> (Component) Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)).toList());
        stack.setItemMeta(meta);
        return stack;
    }
}
