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
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.ClickType;
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
    /** 보조 재료 칸 (강화 촉진제 / 파괴 방지 부적) — a real editable slot; its item goes back to the player on close. */
    private static final int SLOT_AID = 15;

    private final EnhanceService service;
    private final MessageService messages;
    private long lastActionAt;
    private Player viewer;

    public EnhanceGui(EnhanceService service, MessageService messages, int backgroundOffsetPx) {
        super(36, EnhanceBackgroundImages.title(backgroundOffsetPx, "enhance_bg",
                Component.text("⚒ 강화", NamedTextColor.GOLD)));
        this.service = service;
        this.messages = messages;

        setButton(SLOT_ACTION, GuiButton.of(actionIcon(null), this::handleAction));
        setButton(SLOT_CLOSE, GuiButton.of(EnhanceIcons.closeIcon(), event -> event.getWhoClicked().closeInventory()));
        setEditableSlot(SLOT_AID);
    }

    @Override
    public void open(Player player) {
        this.viewer = player;
        service.itemData().repair(player.getInventory().getItemInMainHand());
        refresh(player);
        super.open(player);
    }

    @Override
    public void onEditableSlotChanged() {
        if (viewer != null && viewer.isOnline()) {
            refresh(viewer);
        }
    }

    /** Hands the 보조 재료 칸 item back (dropping what doesn't fit) — also runs on quit and on plugin disable. */
    @Override
    public void onClose(Player player) {
        ItemStack aid = getInventory().getItem(SLOT_AID);
        if (aid == null || aid.getType().isAir()) {
            return;
        }
        getInventory().setItem(SLOT_AID, null);
        player.getInventory().addItem(aid).values()
                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
    }

    private EnhanceAid aid() {
        return EnhanceAid.of(getInventory().getItem(SLOT_AID));
    }

    /** Uses up one 보조 재료 after an attempt that actually happened. */
    private void consumeAid() {
        ItemStack stack = getInventory().getItem(SLOT_AID);
        if (stack != null && !stack.getType().isAir()) {
            stack.setAmount(stack.getAmount() - 1);
            getInventory().setItem(SLOT_AID, stack.getAmount() > 0 ? stack : null);
        }
    }

    /** Ignores double-click echoes and clicks within 400ms of the last one, so one intent = one paid attempt. */
    private void handleAction(InventoryClickEvent event) {
        if (event.getClick() == ClickType.DOUBLE_CLICK) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastActionAt < 400) {
            return;
        }
        lastActionAt = now;
        onAction((Player) event.getWhoClicked());
    }

    private void onAction(Player player) {
        if (service.isAtGate(player.getInventory().getItemInMainHand())) {
            transcend(player);
        } else {
            attempt(player);
        }
    }

    private void transcend(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        int nextStage = service.itemData().stage(hand) + 1;
        var target = service.config().transcendStage(nextStage);
        EnhanceAid aid = aid();
        EnhanceService.Result result = service.transcend(player, aid);
        if (result.attempted() && aid.isBooster() && service.boosterOnTranscend()) {
            consumeAid();
            messages.send(player, "enhance.booster-used", Placeholder.unparsed("bonus", String.valueOf(aid.boosterPercent())),
                    Placeholder.unparsed("rate", rate(EnhanceAid.boostedRate(target.map(TranscendStage::successRate).orElse(0.0), aid.boosterPercent()))));
        }
        switch (result) {
            case TRANSCEND_SUCCESS, TRANSCEND_SUCCESS_NEW_MATERIAL -> {
                playSound(player, Sound.UI_TOAST_CHALLENGE_COMPLETE);
                String name = target.map(TranscendStage::name).orElse(nextStage + "차 초월");
                String key = result == EnhanceService.Result.TRANSCEND_SUCCESS_NEW_MATERIAL ? "enhance.transcend-success-netherite"
                        : nextStage == 1 ? "enhance.transcend-success-restart" : "enhance.transcend-success";
                messages.send(player, key, Placeholder.unparsed("stage", name));
                messages.broadcast("enhance.transcend-broadcast",
                        Placeholder.unparsed("player", player.getName()), Placeholder.unparsed("stage", name));
            }
            case TRANSCEND_FAIL -> {
                playSound(player, Sound.ENTITY_VILLAGER_NO);
                messages.send(player, "enhance.transcend-fail");
            }
            case INSUFFICIENT_FUNDS -> messages.send(player, "enhance.insufficient-funds",
                    Placeholder.unparsed("cost", String.format("%,d", target.map(TranscendStage::currency).orElse(0L))));
            case INSUFFICIENT_STONE -> messages.send(player, "enhance.insufficient-stone",
                    Placeholder.unparsed("stone", target.map(t -> EnhanceMaterialResolver.displayName(t.stoneItemId())).orElse("초월석")),
                    Placeholder.unparsed("amount", String.valueOf(target.map(TranscendStage::stoneAmount).orElse(1))));
            case NOT_ENHANCEABLE -> messages.send(player, "enhance.not-enhanceable");
            default -> messages.send(player, "enhance.not-at-gate");
        }
        refresh(player);
    }

    private String gradeName(ItemStack item) {
        int stage = service.itemData().stage(item);
        if (stage > 0) {
            return service.config().transcendStage(stage).map(TranscendStage::name).orElse(stage + "차 초월");
        }
        return service.config().tierFor(service.itemData().level(item)).name();
    }

    private void attempt(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        int level = service.itemData().level(hand); // before the attempt changes it
        EnhanceAid aid = aid();
        EnhanceService.Result result = service.attempt(player, hand, aid);
        EnhanceTier tier = service.config().tierFor(level + 1);
        if (result.attempted() && aid.isBooster()) {
            consumeAid();
            messages.send(player, "enhance.booster-used", Placeholder.unparsed("bonus", String.valueOf(aid.boosterPercent())),
                    Placeholder.unparsed("rate", rate(EnhanceAid.boostedRate(service.config().successRate(level), aid.boosterPercent()))));
        } else if (result.attempted() && aid.charm() && service.config().isFailRisky(level)) {
            consumeAid();
            if (result != EnhanceService.Result.FAIL_CHARMED) {
                messages.send(player, "enhance.charm-used");
            }
        }

        switch (result) {
            case NOT_ENHANCEABLE -> messages.send(player, "enhance.not-enhanceable");
            case MAX_LEVEL -> messages.send(player, "enhance.max-level",
                    Placeholder.unparsed("max", String.valueOf(TranscendRules.levelCap(service.itemData().stage(hand), service.config().maxLevel()))));
            case NEEDS_TRANSCEND -> messages.send(player, "enhance.needs-transcend");
            case INSUFFICIENT_FUNDS -> messages.send(player, "enhance.insufficient-funds",
                    Placeholder.unparsed("cost", String.format("%,d", service.enhanceCurrency(hand))));
            case INSUFFICIENT_MATERIAL -> {
                var cost = service.costs().costFor(level);
                messages.send(player, "enhance.insufficient-material",
                        Placeholder.unparsed("material", EnhanceMaterialResolver.displayName(cost.materialId())),
                        Placeholder.unparsed("amount", String.valueOf(cost.materialAmount())));
            }
            case SUCCESS -> {
                playSound(player,Sound.ENTITY_PLAYER_LEVELUP);
                messages.send(player, "enhance.success", Placeholder.unparsed("level", String.valueOf(level + 1)), Placeholder.unparsed("tier", gradeName(hand)));
            }
            case SUCCESS_TIER_UP -> {
                playSound(player,Sound.UI_TOAST_CHALLENGE_COMPLETE);
                messages.send(player, "enhance.success-tier-up", Placeholder.unparsed("level", String.valueOf(level + 1)), Placeholder.unparsed("tier", tier.name()));
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
            case FAIL_CHARMED -> {
                playSound(player, Sound.ITEM_TOTEM_USE);
                messages.send(player, "enhance.fail-charmed");
            }
            default -> {
            }
        }
        refresh(player);
    }

    /** "70" / "72.5" */
    private static String rate(double value) {
        return value == Math.floor(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    private void playSound(Player player, Sound sound) {
        player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
    }

    private void refresh(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        boolean enhanceable = service.itemData().isEnhanceable(hand);
        setButton(SLOT_PREVIEW, GuiButton.of(previewIcon(enhanceable ? hand : null), this::handleAction));
        setButton(SLOT_ACTION, GuiButton.of(actionIcon(enhanceable ? hand : null), this::handleAction));
        player.updateInventory();
    }

    private ItemStack previewIcon(ItemStack hand) {
        if (hand == null) {
            ItemStack stack = new ItemStack(Material.BARRIER);
            ItemMeta meta = stack.getItemMeta();
            meta.displayName(Component.text("강화할 무기/방어구/도구를 손에 드세요", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
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
            lore.add(Component.text("손에 무기, 방어구, 도구를 들어야 합니다.", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        } else {
            if (service.isAtGate(hand)) {
                return transcendIcon(hand);
            }
            int level = service.itemData().level(hand);
            EnhanceConfig config = service.config();
            int stage = service.itemData().stage(hand);
            if (level >= TranscendRules.levelCap(stage, config.maxLevel())) {
                lore.add(Component.text("이미 최대 강화 수치입니다. (+" + TranscendRules.levelCap(stage, config.maxLevel()) + ")", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            } else {
                EnhanceTier currentTier = config.tierFor(level);
                var cost = service.costs().costFor(level);
                lore.add(Component.text("현재: +" + level + "강 (", NamedTextColor.GRAY)
                        .append(Component.text(gradeName(hand), stage > 0
                                ? config.transcendStage(stage).map(TranscendStage::color).orElse(NamedTextColor.DARK_PURPLE)
                                : currentTier.color()))
                        .append(Component.text(")", NamedTextColor.GRAY)).decoration(TextDecoration.ITALIC, false));
                EnhanceAid aid = aid();
                lore.add(Component.text("성공 확률: " + rate(EnhanceAid.boostedRate(config.successRate(level), aid.boosterPercent())) + "%"
                        + (aid.isBooster() ? " (촉진제 +" + aid.boosterPercent() + "%)" : ""), NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("필요 온: " + String.format("%,d", service.enhanceCurrency(hand)), NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("필요 " + EnhanceMaterialResolver.displayName(cost.materialId()) + ": " + cost.materialAmount() + "개", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
                if (config.isFailRisky(level)) {
                    boolean noDestroy = stage > 0 && config.transcendProtectFromDestroy();
                    lore.add(Component.text(noDestroy ? "⚠ 실패 시 하락 위험이 있습니다! (초월 장비는 파괴되지 않음)" : "⚠ 실패 시 하락/파괴 위험이 있습니다!",
                            NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
                    String protection = config.protectionItemId();
                    if (aid.charm()) {
                        lore.add(Component.text("✦ 파괴 방지 부적 적용 — 이번 시도는 하락·파괴되지 않습니다.", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
                    } else if (protection != null && !protection.isBlank()) {
                        lore.add(Component.text(EnhanceMaterialResolver.displayName(protection) + " 소지 시 자동으로 보호됩니다.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
                    }
                }
                if (stage == 0 && config.crossesTierAt(level)) {
                    EnhanceTier nextTier = config.tierFor(level + 1);
                    lore.add(Component.text("성공 시 " + nextTier.name() + " 등급으로 승급!", nextTier.color()).decoration(TextDecoration.ITALIC, false));
                }
            }
        }
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack transcendIcon(ItemStack hand) {
        ItemStack stack = EnhanceIcons.resolveCustom("yeowool_enhance:enhance_confirm");
        if (stack == null) {
            stack = new ItemStack(Material.NETHER_STAR);
        }
        int nextStage = service.itemData().stage(hand) + 1;
        TranscendStage target = service.config().transcendStage(nextStage).orElseThrow();
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text("✦ 초월하기 — " + target.name(), target.color(), TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        if (nextStage == 1) {
            boolean becomesNetherite = !TranscendRules.netheriteVariant(hand.getType().name()).equals(hand.getType().name());
            lore.add(Component.text(becomesNetherite ? "성공 시 네더라이트 장비로 바뀌고 0강부터 다시 강화합니다."
                    : "성공 시 0강부터 다시 강화합니다. (이 장비는 모양이 그대로 유지됩니다)", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text("성공 시 강화 수치를 유지한 채 " + target.name() + "로 올라갑니다.", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
        }
        int bonus = service.boosterOnTranscend() ? aid().boosterPercent() : 0;
        lore.add(Component.text("성공 확률: " + rate(EnhanceAid.boostedRate(target.successRate(), bonus)) + "%"
                + (bonus > 0 ? " (촉진제 +" + bonus + "%)" : ""), NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        if (aid().charm()) {
            lore.add(Component.text("파괴 방지 부적은 초월에 쓰이지 않습니다. (초월 실패는 원래 파괴가 없음)", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.text("필요 온: " + String.format("%,d", target.currency()), NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("필요 " + EnhanceMaterialResolver.displayName(target.stoneItemId()) + ": " + target.stoneAmount() + "개", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("실패해도 장비는 그대로입니다. (초월석·온만 소모)", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }
}
