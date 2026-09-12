package com.yeowool.core.listener;

import com.yeowool.core.data.repository.PlayerInventoryRepository;
import com.yeowool.core.util.ItemStackSerializer;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps a player's main inventory, armor, off-hand item, ender chest, XP
 * (level + progress), health, and hunger (food level + saturation) in sync
 * across every backend server sharing this database (lobby/town/wild) —
 * each is otherwise a separate Paper server with its own local player-data
 * files, so without this a player's items would depend on which server they
 * last logged out on.
 *
 * <p>Loaded blocking at {@link AsyncPlayerPreLoginEvent} (already off the main
 * thread) into {@link #pending}, then applied at {@link PlayerJoinEvent} —
 * overwriting whatever this server's own local player-data file just gave the
 * player. A player with no saved snapshot yet (first join anywhere, or before
 * this feature existed) simply keeps whatever inventory this server loaded
 * for them locally; nothing is ever wiped to empty.
 *
 * <p>Saved back at {@link PlayerQuitEvent} — deliberately a <b>blocking</b> DB
 * write on the main thread here, unlike the rest of {@code PlayerData}'s
 * fire-and-forget async save ({@link PlayerConnectionListener}). Velocity can
 * connect a player to the next backend server almost immediately after they
 * leave this one, so an async save left a real window where the new server's
 * pre-login load would run before this one's write had actually landed,
 * handing the player a stale (pre-quit) inventory. Blocking here — a single
 * UPDATE against a local, pooled connection, plus cheap Base64 serialization —
 * closes that window at the cost of a few milliseconds of quit-time latency,
 * which is the right trade for something as visible and loss-sensitive as a
 * player's actual items.
 */
public final class PlayerInventorySyncListener implements Listener {

    private final JavaPlugin plugin;
    private final PlayerInventoryRepository repository;
    private final Map<UUID, PlayerInventoryRepository.Snapshot> pending = new ConcurrentHashMap<>();

    public PlayerInventorySyncListener(JavaPlugin plugin, PlayerInventoryRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        try {
            repository.load(event.getUniqueId()).ifPresent(snapshot -> pending.put(event.getUniqueId(), snapshot));
        } catch (SQLException e) {
            // 실패해도 접속은 막지 않음 — 이번 세션은 이 서버에 로컬로 저장된 인벤토리를 그대로 씀.
            plugin.getLogger().severe("인벤토리 동기화 로드 실패 (" + event.getName() + "): " + e.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        PlayerInventoryRepository.Snapshot snapshot = pending.remove(event.getPlayer().getUniqueId());
        if (snapshot == null) {
            return;
        }
        Player player = event.getPlayer();
        player.getInventory().setContents(ItemStackSerializer.deserializeArray(snapshot.mainInventory()));
        player.getInventory().setArmorContents(ItemStackSerializer.deserializeArray(snapshot.armor()));
        player.getInventory().setItemInOffHand(ItemStackSerializer.deserialize(snapshot.offHand()));
        player.getEnderChest().setContents(ItemStackSerializer.deserializeArray(snapshot.enderChest()));

        player.setLevel(snapshot.expLevel());
        player.setExp(Math.min(Math.max(snapshot.expProgress(), 0f), 1f));
        double maxHealth = player.getAttribute(Attribute.MAX_HEALTH).getValue();
        player.setHealth(Math.min(Math.max(snapshot.health(), 0), maxHealth));
        player.setFoodLevel(snapshot.foodLevel());
        player.setSaturation(snapshot.saturation());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        ItemStack[] mainInventory = player.getInventory().getContents().clone();
        ItemStack[] armor = player.getInventory().getArmorContents().clone();
        ItemStack offHand = player.getInventory().getItemInOffHand().clone();
        ItemStack[] enderChest = player.getEnderChest().getContents().clone();
        int expLevel = player.getLevel();
        float expProgress = player.getExp();
        double health = player.getHealth();
        int foodLevel = player.getFoodLevel();
        float saturation = player.getSaturation();

        try {
            repository.save(uuid, new PlayerInventoryRepository.Snapshot(
                    ItemStackSerializer.serializeArray(mainInventory),
                    ItemStackSerializer.serializeArray(armor),
                    ItemStackSerializer.serialize(offHand),
                    ItemStackSerializer.serializeArray(enderChest),
                    expLevel, expProgress, health, foodLevel, saturation));
        } catch (SQLException e) {
            plugin.getLogger().severe("인벤토리 동기화 저장 실패 (" + player.getName() + "): " + e.getMessage());
        }
    }
}
