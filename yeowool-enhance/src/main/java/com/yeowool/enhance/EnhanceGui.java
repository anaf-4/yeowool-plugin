package com.yeowool.enhance;

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

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /강화} — operates on whatever is currently in the viewer's main hand
 * at click time (re-read live each attempt, not a snapshot taken on open), so
 * a swapped-out item is simply handled fresh rather than desyncing the GUI.
 * Slot 13 previews the item, slot 22 is the action button, slot 31 closes —
 * plain +N강 system, unrelated to the separate {@code /인챈트강화} system
 * (see {@code com.yeowool.enchant}). Background {@code yeowool_enhance:enhance_bg}
 * (converted from AdvancedEnchantments UI's enchanter.png), 36 slots to match it.
 */
public final class EnhanceGui extends YeowoolGui {

    private static final int SLOT_PREVIEW = 13;
    private static final int SLOT_ACTION = 22;
    private static final int SLOT_CLOSE = 31;

    private final EnhanceService service;
    private final MessageService messages;

    public EnhanceGui(EnhanceService service, MessageService messages, int backgroundOffsetPx) {
        super(36, EnhanceBackgroundImages.title(backgroundOffsetPx, "enhance_bg",
                Component.text("⚒ 강화", NamedTextColor.GOLD)));
        this.service = service;
        this.messages = messages;

        setButton(SLOT_ACTION, GuiButton.of(actionIcon(null), event -> attempt((Player) event.getWhoClicked())));
        setButton(SLOT_CLOSE, GuiButton.of(EnhanceIcons.closeIcon(), event -> event.getWhoClicked().closeInventory()));
    }

    @Override
    public void open(Player player) {
        refresh(player);
        super.open(player);
    }

    private void attempt(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        EnhanceService.Result result = service.attempt(player, hand);
        int level = service.itemData().level(hand);
        EnhanceTier tier = service.config().tierFor(level);

        switch (result) {
            case NOT_ENHANCEABLE -> messages.send(player, "enhance.not-enhanceable");
            case MAX_LEVEL -> messages.send(player, "enhance.max-level", Placeholder.unparsed("max", String.valueOf(service.config().maxLevel())));
            case INSUFFICIENT_FUNDS -> messages.send(player, "enhance.insufficient-funds",
                    Placeholder.unparsed("cost", String.format("%,d", service.costs().costFor(level).currency())));
            case INSUFFICIENT_MATERIAL -> {
                var cost = service.costs().costFor(level);
                messages.send(player, "enhance.insufficient-material",
                        Placeholder.unparsed("material", EnhanceMaterialResolver.displayName(cost.materialId())),
                        Placeholder.unparsed("amount", String.valueOf(cost.materialAmount())));
            }
            case SUCCESS -> {
                playSound(player,Sound.ENTITY_PLAYER_LEVELUP);
                messages.send(player, "enhance.success", Placeholder.unparsed("level", String.valueOf(level + 1)), Placeholder.unparsed("tier", tier.name()));
            }
            case SUCCESS_TIER_UP -> {
                playSound(player,Sound.UI_TOAST_CHALLENGE_COMPLETE);
                messages.send(player, "enhance.success-tier-up", Placeholder.unparsed("level", String.valueOf(level)), Placeholder.unparsed("tier", tier.name()));
            }
            case FAIL_SAFE -> {
                playSound(player,Sound.ENTITY_VILLAGER_NO);
                messages.send(player, "enhance.fail-safe");
            }
            case FAIL_PROTECTED -> {
                playSound(player,Sound.ENTITY_VILLAGER_NO);
                messages.send(player, "enhance.fail-protected",
                        Placeholder.unparsed("protection", EnhanceMaterialResolver.displayName(service.config().protectionItemId())));
            }
            case FAIL_DOWNGRADE -> {
                playSound(player,Sound.ENTITY_ITEM_BREAK);
                messages.send(player, "enhance.fail-downgrade", Placeholder.unparsed("level", String.valueOf(level - 1)));
            }
            case FAIL_DESTROYED -> {
                playSound(player,Sound.ENTITY_GENERIC_EXPLODE);
                messages.send(player, "enhance.fail-destroyed");
            }
        }
        refresh(player);
    }

    private void playSound(Player player, Sound sound) {
        player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
    }

    private void refresh(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        boolean enhanceable = service.itemData().isEnhanceable(hand);
        setButton(SLOT_PREVIEW, GuiButton.of(previewIcon(enhanceable ? hand : null), event -> attempt((Player) event.getWhoClicked())));
        setButton(SLOT_ACTION, GuiButton.of(actionIcon(enhanceable ? hand : null), event -> attempt((Player) event.getWhoClicked())));
        player.updateInventory();
    }

    private ItemStack previewIcon(ItemStack hand) {
        if (hand == null) {
            ItemStack stack = new ItemStack(Material.BARRIER);
            ItemMeta meta = stack.getItemMeta();
            meta.displayName(Component.text("강화할 무기/방어구를 손에 드세요", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
            stack.setItemMeta(meta);
            return stack;
        }
        return hand.clone();
    }

    private ItemStack actionIcon(ItemStack hand) {
        boolean enhanceable = hand != null;
        ItemStack stack = EnhanceIcons.resolveCustom(enhanceable ? "yeowool_enhance:enhance_confirm" : "yeowool_enhance:enhance_unconfirm");
        if (stack == null) {
            stack = new ItemStack(Material.ANVIL);
        }
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text("강화하기", NamedTextColor.GOLD, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        if (hand == null) {
            lore.add(Component.text("손에 무기나 방어구를 들어야 합니다.", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        } else {
            int level = service.itemData().level(hand);
            EnhanceConfig config = service.config();
            if (level >= config.maxLevel()) {
                lore.add(Component.text("이미 최대 강화 수치입니다. (+" + config.maxLevel() + ")", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            } else {
                EnhanceTier currentTier = config.tierFor(level);
                var cost = service.costs().costFor(level);
                lore.add(Component.text("현재: +" + level + "강 (", NamedTextColor.GRAY)
                        .append(Component.text(currentTier.name(), currentTier.color()))
                        .append(Component.text(")", NamedTextColor.GRAY)).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("성공 확률: " + config.successRate(level) + "%", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("필요 온: " + String.format("%,d", cost.currency()), NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("필요 " + EnhanceMaterialResolver.displayName(cost.materialId()) + ": " + cost.materialAmount() + "개", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
                if (config.isFailRisky(level)) {
                    lore.add(Component.text("⚠ 실패 시 하락/파괴 위험이 있습니다!", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
                    String protection = config.protectionItemId();
                    if (protection != null && !protection.isBlank()) {
                        lore.add(Component.text(EnhanceMaterialResolver.displayName(protection) + " 소지 시 자동으로 보호됩니다.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
                    }
                }
                if (config.crossesTierAt(level)) {
                    EnhanceTier nextTier = config.tierFor(level + 1);
                    lore.add(Component.text("성공 시 " + nextTier.name() + " 등급으로 승급!", nextTier.color()).decoration(TextDecoration.ITALIC, false));
                }
            }
        }
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

}
