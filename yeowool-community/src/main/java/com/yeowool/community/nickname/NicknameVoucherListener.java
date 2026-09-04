package com.yeowool.community.nickname;

import com.yeowool.community.display.PlayerIdentityService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.view.AnvilView;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Right-clicking a {@link NicknameVoucherItem} opens a virtual anvil (no
 * real anvil block needed, same trick as {@code /쿠폰}) to type a Korean
 * nickname. Every handler here first checks the anvil's slot-0 item is our
 * {@link NicknameAnvilPlaceholder} — unlike the coupon flow this also
 * guards against a coincidental match in a real, unrelated anvil use, since
 * a false-positive preview here would be a two-Korean-character item rename
 * rather than an unguessable coupon code. Preview/confirm is still split
 * the same way coupon redemption is: {@link PrepareAnvilEvent} only ever
 * previews (idempotent), and the one-shot consume-item + apply-nickname
 * side effect only runs from {@link InventoryClickEvent} on the result
 * slot (2), since PrepareAnvilEvent can re-fire for the same finished text.
 */
public final class NicknameVoucherListener implements Listener {

    private final JavaPlugin plugin;
    private final KoreanNicknameManager nicknameManager;
    private final PlayerIdentityService identityService;

    public NicknameVoucherListener(JavaPlugin plugin, KoreanNicknameManager nicknameManager, PlayerIdentityService identityService) {
        this.plugin = plugin;
        this.nicknameManager = nicknameManager;
        this.identityService = identityService;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (!NicknameVoucherItem.isVoucher(plugin, item)) {
            return;
        }
        event.setCancelled(true);

        Player player = event.getPlayer();
        var view = player.openAnvil(null, true);
        if (view == null || !(view.getTopInventory() instanceof AnvilInventory anvil)) {
            player.sendMessage(Component.text("지금은 모루를 열 수 없습니다.", NamedTextColor.RED));
            return;
        }
        anvil.setFirstItem(NicknameAnvilPlaceholder.create(plugin));
    }

    @EventHandler(ignoreCancelled = true)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (!isOurAnvil(event.getInventory())) {
            return;
        }
        String nickname = event.getView().getRenameText();
        if (nickname == null || nickname.isBlank()) {
            return;
        }
        event.setResult(previewItem(nickname));
    }

    private ItemStack previewItem(String nickname) {
        ItemStack preview = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = preview.getItemMeta();
        meta.displayName(Component.text(nickname, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add((nicknameManager.isValidFormat(nickname)
                ? Component.text("클릭하여 이 닉네임으로 설정", NamedTextColor.GREEN)
                : Component.text("한글 2~8자만 입력할 수 있습니다", NamedTextColor.RED)).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        preview.setItemMeta(meta);
        return preview;
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getClickedInventory() instanceof AnvilInventory anvil) || event.getSlot() != 2 || !isOurAnvil(anvil)) {
            return;
        }
        if (!(event.getView() instanceof AnvilView anvilView)) {
            return;
        }
        String nickname = anvilView.getRenameText();
        if (nickname == null || nickname.isBlank()) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!nicknameManager.isValidFormat(nickname)) {
            player.sendMessage(Component.text("한글 2~8자만 입력할 수 있습니다.", NamedTextColor.RED));
            return;
        }
        if (!consumeOneVoucher(player)) {
            player.sendMessage(Component.text("한글 닉네임 설정권이 없습니다.", NamedTextColor.RED));
            return;
        }

        nicknameManager.set(player, nickname);
        identityService.refresh(player);
        player.sendMessage(Component.text("한글 닉네임을 설정했습니다: " + nickname, NamedTextColor.GREEN));

        Bukkit.getScheduler().runTask(plugin, () -> player.closeInventory());
    }

    private boolean consumeOneVoucher(Player player) {
        var inventory = player.getInventory();
        for (ItemStack stack : inventory.getContents()) {
            if (NicknameVoucherItem.isVoucher(plugin, stack)) {
                stack.setAmount(stack.getAmount() - 1);
                return true;
            }
        }
        return false;
    }

    private boolean isOurAnvil(AnvilInventory anvil) {
        return NicknameAnvilPlaceholder.isPlaceholder(plugin, anvil.getItem(0));
    }

    /** Discards the "type here" placeholder on close instead of letting the anvil hand it back to the player. */
    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory() instanceof AnvilInventory anvil && isOurAnvil(anvil)) {
            anvil.setItem(0, null);
        }
    }
}
