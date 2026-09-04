package com.yeowool.life.fishing;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
import dev.lone.itemsadder.api.CustomStack;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * Right-clicking a {@link FishBait} item "equips" it instead of requiring it
 * physically in the off-hand every cast: up to {@link #MAX_EQUIPPED} are
 * loaded from the held stack into the player's {@code PlayerData} (reusing
 * its generic {@code settings}/{@code statistics} maps — {@code
 * fishing.equipped-bait} for which one, {@code fishing.equipped-bait-count}
 * for how many — so it persists across sessions with no new schema), and
 * {@link FishingListener} draws from that count on every catch instead of
 * checking the off-hand. Switching to a *different* bait while one is still
 * loaded is refused until the current one runs out, so bait is never
 * silently discarded.
 */
public final class BaitEquipListener implements Listener {

    static final int MAX_EQUIPPED = 64;
    static final String SETTING_BAIT_ID = "fishing.equipped-bait";
    static final String STAT_BAIT_COUNT = "fishing.equipped-bait-count";

    private final YeowoolCoreAPI core;
    private final MessageService messages;
    private final Map<String, FishBait> baitsByItemId;

    public BaitEquipListener(YeowoolCoreAPI core, MessageService messages, Map<String, FishBait> baitsByItemId) {
        this.core = core;
        this.messages = messages;
        this.baitsByItemId = baitsByItemId;
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
        String itemId = customItemId(item);
        if (itemId == null) {
            return;
        }
        FishBait bait = baitsByItemId.get(itemId);
        if (bait == null) {
            return;
        }
        event.setCancelled(true);

        Player player = event.getPlayer();
        var loaded = core.playerData().getIfLoaded(player.getUniqueId());
        if (loaded.isEmpty()) {
            return;
        }
        var data = loaded.get();

        String equippedId = data.getSetting(SETTING_BAIT_ID, "");
        long currentCount = data.getStatistic(STAT_BAIT_COUNT);
        boolean sameBait = equippedId.equals(bait.id());

        if (!sameBait && currentCount > 0) {
            String equippedDisplay = baitsByItemId.values().stream()
                    .filter(b -> b.id().equals(equippedId))
                    .map(FishBait::displayName)
                    .findFirst()
                    .orElse(equippedId);
            messages.send(player, "fishing.bait-different-equipped", Placeholder.unparsed("bait", equippedDisplay));
            return;
        }
        if (sameBait && currentCount >= MAX_EQUIPPED) {
            messages.send(player, "fishing.bait-full", Placeholder.unparsed("max", String.valueOf(MAX_EQUIPPED)));
            return;
        }

        long room = MAX_EQUIPPED - (sameBait ? currentCount : 0);
        int consumeAmount = (int) Math.min(room, item.getAmount());
        item.setAmount(item.getAmount() - consumeAmount);

        data.setSetting(SETTING_BAIT_ID, bait.id());
        data.addStatistic(STAT_BAIT_COUNT, consumeAmount);

        messages.send(player, "fishing.bait-equipped",
                Placeholder.unparsed("bait", bait.displayName()),
                Placeholder.unparsed("count", String.valueOf(data.getStatistic(STAT_BAIT_COUNT))),
                Placeholder.unparsed("max", String.valueOf(MAX_EQUIPPED)));
    }

    private String customItemId(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) {
            return null;
        }
        CustomStack custom = CustomStack.byItemStack(stack);
        return custom == null ? null : custom.getNamespacedID();
    }
}
