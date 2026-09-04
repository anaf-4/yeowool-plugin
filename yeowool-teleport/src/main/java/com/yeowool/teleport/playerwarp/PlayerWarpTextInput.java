package com.yeowool.teleport.playerwarp;

import com.yeowool.teleport.model.PlayerWarp;
import com.yeowool.teleport.util.PlayerWarpCurrency;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.view.AnvilView;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One shared virtual-anvil text-capture flow for every free-text playerwarp
 * action (rename / display name / description / search / transfer target),
 * mirroring the exact two-step preview-then-confirm-on-click pattern
 * {@code CouponRedeemListener} already established (a single
 * {@code PrepareAnvilEvent} can re-fire for the same finished text, so only
 * the result-slot click actually commits anything).
 */
public final class PlayerWarpTextInput implements Listener {

    private enum Mode { RENAME, DISPLAY_NAME, DESCRIPTION, SEARCH, TRANSFER_TARGET, PRICE }

    private record Pending(Mode mode, UUID warpOwner, String warpName) {
    }

    private final JavaPlugin plugin;
    private final PlayerWarpContext ctx;
    private final NamespacedKey placeholderKey;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public PlayerWarpTextInput(JavaPlugin plugin, PlayerWarpContext ctx) {
        this.plugin = plugin;
        this.ctx = ctx;
        this.placeholderKey = new NamespacedKey(plugin, "playerwarp_text_input");
    }

    public void openRename(Player player, PlayerWarp warp) {
        open(player, Mode.RENAME, warp.owner(), warp.name(), "새 이름을 입력하세요");
    }

    public void openDisplayName(Player player, PlayerWarp warp) {
        open(player, Mode.DISPLAY_NAME, warp.owner(), warp.name(), "표시 이름을 입력하세요");
    }

    public void openDescription(Player player, PlayerWarp warp) {
        open(player, Mode.DESCRIPTION, warp.owner(), warp.name(), "설명을 입력하세요");
    }

    public void openTransferTarget(Player player, PlayerWarp warp) {
        open(player, Mode.TRANSFER_TARGET, warp.owner(), warp.name(), "새 주인의 닉네임을 입력하세요");
    }

    public void openSearch(Player player) {
        open(player, Mode.SEARCH, null, null, "검색어를 입력하세요");
    }

    public void openPrice(Player player, PlayerWarp warp) {
        open(player, Mode.PRICE, warp.owner(), warp.name(), "입장료(온)를 숫자로 입력하세요");
    }

    private void open(Player player, Mode mode, UUID warpOwner, String warpName, String hint) {
        pending.put(player.getUniqueId(), new Pending(mode, warpOwner, warpName));
        var view = player.openAnvil(null, true);
        if (view == null || !(view.getTopInventory() instanceof AnvilInventory anvil)) {
            ctx.messages().send(player, "playerwarp.anvil-unavailable");
            pending.remove(player.getUniqueId());
            return;
        }
        anvil.setFirstItem(placeholder(hint));
    }

    private ItemStack placeholder(String hint) {
        ItemStack stack = new ItemStack(Material.PAPER);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(hint, NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        meta.getPersistentDataContainer().set(placeholderKey, PersistentDataType.BYTE, (byte) 1);
        stack.setItemMeta(meta);
        return stack;
    }

    private boolean isPlaceholder(ItemStack stack) {
        return stack != null && stack.hasItemMeta() && stack.getItemMeta().getPersistentDataContainer().has(placeholderKey, PersistentDataType.BYTE);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (!isPlaceholder(event.getInventory().getItem(0))) {
            return;
        }
        String text = event.getView().getRenameText();
        if (text == null || text.isBlank()) {
            return;
        }
        ItemStack preview = new ItemStack(Material.LIME_DYE);
        ItemMeta meta = preview.getItemMeta();
        meta.displayName(Component.text("확인: " + text, NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        preview.setItemMeta(meta);
        event.setResult(preview);
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getClickedInventory() instanceof AnvilInventory anvil) || event.getSlot() != 2) {
            return;
        }
        if (!isPlaceholder(anvil.getItem(0)) || !(event.getView() instanceof AnvilView anvilView)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        String text = anvilView.getRenameText();
        if (text == null || text.isBlank()) {
            return;
        }
        event.setCancelled(true);
        Pending request = pending.remove(player.getUniqueId());
        if (request == null) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            player.closeInventory();
            apply(player, request, text.trim());
        });
    }

    private void apply(Player player, Pending request, String text) {
        if (request.mode() == Mode.SEARCH) {
            new PlayerWarpBrowseGui(ctx, this, 0, null, text, PlayerWarpBrowseGui.SortMode.ALPHABETICAL).open(player);
            return;
        }

        var warpOpt = ctx.warps().get(request.warpOwner(), request.warpName());
        if (warpOpt.isEmpty()) {
            ctx.messages().send(player, "playerwarp.delete-not-found", Placeholder.unparsed("name", request.warpName()));
            return;
        }
        PlayerWarp warp = warpOpt.get();

        switch (request.mode()) {
            case RENAME -> {
                if (text.length() > ctx.config().nameMaxLength()) {
                    ctx.messages().send(player, "playerwarp.name-too-long", Placeholder.unparsed("max", String.valueOf(ctx.config().nameMaxLength())));
                    return;
                }
                if (ctx.warps().nameTaken(warp.owner(), text)) {
                    ctx.messages().send(player, "playerwarp.create-name-taken", Placeholder.unparsed("name", text));
                    return;
                }
                if (!chargeFee(player, ctx.config().renameFee())) {
                    return;
                }
                PlayerWarp renamed = warp.withName(text);
                ctx.warps().rekey(warp.owner(), warp.name(), renamed);
                ctx.messages().send(player, "playerwarp.rename-success", Placeholder.unparsed("old", warp.name()), Placeholder.unparsed("name", text));
                new PlayerWarpEditGui(ctx, renamed, this).open(player);
            }
            case DISPLAY_NAME -> {
                if (text.length() > ctx.config().displayNameMaxLength()) {
                    ctx.messages().send(player, "playerwarp.name-too-long", Placeholder.unparsed("max", String.valueOf(ctx.config().displayNameMaxLength())));
                    return;
                }
                if (!chargeFee(player, ctx.config().setDisplayNameFee())) {
                    return;
                }
                PlayerWarp updated = warp.withDisplayName(text);
                ctx.warps().put(updated);
                ctx.messages().send(player, "playerwarp.edit-success");
                new PlayerWarpEditGui(ctx, updated, this).open(player);
            }
            case DESCRIPTION -> {
                String desc = text.length() > ctx.config().descriptionMaxLength() ? text.substring(0, ctx.config().descriptionMaxLength()) : text;
                if (!chargeFee(player, ctx.config().setDescriptionFee())) {
                    return;
                }
                PlayerWarp updated = warp.withDescription(desc);
                ctx.warps().put(updated);
                ctx.messages().send(player, "playerwarp.edit-success");
                new PlayerWarpEditGui(ctx, updated, this).open(player);
            }
            case PRICE -> {
                long price;
                try {
                    price = Long.parseLong(text.replace(",", ""));
                } catch (NumberFormatException e) {
                    ctx.messages().send(player, "playerwarp.invalid-number");
                    return;
                }
                if (price < 0 || price > ctx.config().maxAdmission()) {
                    ctx.messages().send(player, "playerwarp.price-out-of-range", Placeholder.unparsed("max", String.format("%,d", ctx.config().maxAdmission())));
                    return;
                }
                if (!chargeFee(player, ctx.config().setPriceFee())) {
                    return;
                }
                PlayerWarp updated = warp.withPrice(price);
                ctx.warps().put(updated);
                ctx.messages().send(player, "playerwarp.edit-success");
                new PlayerWarpEditGui(ctx, updated, this).open(player);
            }
            case TRANSFER_TARGET -> {
                var target = Bukkit.getOfflinePlayer(text);
                if (target.getUniqueId() == null || (target.getName() == null && !target.hasPlayedBefore())) {
                    ctx.messages().send(player, "playerwarp.player-not-found", Placeholder.unparsed("name", text));
                    return;
                }
                if (ctx.warps().nameTaken(target.getUniqueId(), warp.name())) {
                    ctx.messages().send(player, "playerwarp.create-name-taken", Placeholder.unparsed("name", warp.name()));
                    return;
                }
                if (!chargeFee(player, ctx.config().transferOwnershipFee())) {
                    return;
                }
                PlayerWarp transferred = warp.withOwner(target.getUniqueId());
                ctx.warps().rekey(warp.owner(), warp.name(), transferred);
                ctx.messages().send(player, "playerwarp.transfer-success", Placeholder.unparsed("name", warp.name()), Placeholder.unparsed("target", String.valueOf(target.getName())));
            }
            default -> {
            }
        }
    }

    private boolean chargeFee(Player player, long fee) {
        if (fee <= 0) {
            return true;
        }
        if (!PlayerWarpCurrency.has(ctx, player.getUniqueId(), fee)) {
            ctx.messages().send(player, "playerwarp.insufficient-funds", Placeholder.unparsed("cost", String.format("%,d", fee)));
            return false;
        }
        PlayerWarpCurrency.charge(ctx, player.getUniqueId(), fee, "플레이어 워프 편집 수수료");
        return true;
    }

    /** Discards the "type here" placeholder on close instead of letting the anvil hand it back to the player. */
    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory() instanceof AnvilInventory anvil && isPlaceholder(anvil.getItem(0))) {
            anvil.setItem(0, null);
        }
    }
}
