package com.yeowool.life.scrapyard;

import com.yeowool.core.api.service.MessageService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Random;

/**
 * 폐기장의 진입점/출구/상자 블록 우클릭, 사망, 접속 종료를 처리한다. 몹
 * 스폰과 시간 초과/구역 이탈 감시는 {@link ScrapyardMobSpawnTask}/{@link
 * ScrapyardTickTask}가 각각 담당.
 */
public final class ScrapyardListener implements Listener {

    private final JavaPlugin plugin;
    private final ScrapyardSessionManager sessionManager;
    private final ScrapyardLocationStore locationStore;
    private final ScrapyardConfig config;
    private final MessageService messages;
    private final Random random = new Random();

    public ScrapyardListener(JavaPlugin plugin, ScrapyardSessionManager sessionManager, ScrapyardLocationStore locationStore,
                              ScrapyardConfig config, MessageService messages) {
        this.plugin = plugin;
        this.sessionManager = sessionManager;
        this.locationStore = locationStore;
        this.config = config;
        this.messages = messages;
    }

    /**
     * Deliberately {@code ignoreCancelled = false} (the default) — these are
     * admin-registered special points (portal/exit/chest), not general block
     * use, so they must still fire even if land protection or WorldGuard
     * already cancelled the vanilla interaction at that spot (e.g. a chest
     * block used as the portal trigger, sitting outside anyone's claimed
     * land). {@code HIGH} priority just means we run after most other
     * plugins have made their own cancellation decision, not that we respect it.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        Player player = event.getPlayer();
        Location clicked = event.getClickedBlock().getLocation();

        if (isAt(locationStore.point(ScrapyardLocationStore.ENTRY).orElse(null), clicked)) {
            handleEnter(player);
            return;
        }
        if (isAt(locationStore.point(ScrapyardLocationStore.EXIT).orElse(null), clicked)) {
            handleExit(player);
            return;
        }
        locationStore.chestAt(clicked).ifPresent(chest -> handleChest(player, chest));
    }

    /**
     * 넷헤르 포탈처럼 진입점 블록에 "닿기만" 해도 입장되도록 — 우클릭과 별개로
     * 지원. 매 틱 호출되는 이벤트라 블록이 실제로 바뀐 경우에만 검사해서
     * 불필요한 조회를 피함.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null || isSameBlock(event.getFrom(), event.getTo())) {
            return;
        }
        Location entryPoint = locationStore.point(ScrapyardLocationStore.ENTRY).orElse(null);
        if (entryPoint == null || !isAt(entryPoint, event.getTo())) {
            return;
        }
        handleEnter(event.getPlayer());
    }

    private boolean isSameBlock(Location a, Location b) {
        return a.getBlockX() == b.getBlockX() && a.getBlockY() == b.getBlockY() && a.getBlockZ() == b.getBlockZ()
                && a.getWorld().equals(b.getWorld());
    }

    private boolean isAt(Location point, Location clicked) {
        return point != null && point.getWorld().equals(clicked.getWorld())
                && point.getBlockX() == clicked.getBlockX() && point.getBlockY() == clicked.getBlockY() && point.getBlockZ() == clicked.getBlockZ();
    }

    private void handleEnter(Player player) {
        switch (sessionManager.enter(player)) {
            case OK -> messages.send(player, "scrapyard.enter-success",
                    Placeholder.unparsed("minutes", String.valueOf(config.sessionDurationSeconds() / 60)));
            case ALREADY_LOCKED_TODAY -> messages.send(player, "scrapyard.already-locked-today");
            case ALREADY_ACTIVE -> messages.send(player, "scrapyard.already-active");
            case NOT_CONFIGURED -> messages.send(player, "scrapyard.not-configured");
        }
    }

    private void handleExit(Player player) {
        if (!sessionManager.hasActiveSession(player.getUniqueId())) {
            messages.send(player, "scrapyard.need-active-session");
            return;
        }
        sessionManager.succeed(player);
        messages.send(player, "scrapyard.exit-success");
    }

    private void handleChest(Player player, ScrapyardRepository.Chest chest) {
        if (!sessionManager.hasActiveSession(player.getUniqueId())) {
            messages.send(player, "scrapyard.need-active-session");
            return;
        }
        String today = java.time.LocalDate.now().toString();
        if (today.equals(chest.lastOpenedDate())) {
            messages.send(player, "scrapyard.chest-already-opened");
            return;
        }
        locationStore.markChestOpened(chest, today);

        if (random.nextInt(100) < config.chestEmptyChancePercent() || config.scrapItems().isEmpty()) {
            messages.send(player, "scrapyard.chest-empty");
            return;
        }
        ScrapItem loot = rollLoot();
        ItemStack stack = config.build(loot, plugin);
        var leftover = player.getInventory().addItem(stack);
        leftover.values().forEach(extra -> player.getWorld().dropItemNaturally(player.getLocation(), extra));
        messages.send(player, "scrapyard.chest-loot", Placeholder.component("item", stack.getItemMeta().displayName()));
    }

    private ScrapItem rollLoot() {
        List<ScrapItem> items = List.copyOf(config.scrapItems().values());
        int totalWeight = items.stream().mapToInt(ScrapItem::lootWeight).sum();
        int roll = random.nextInt(Math.max(1, totalWeight));
        int cumulative = 0;
        for (ScrapItem item : items) {
            cumulative += item.lootWeight();
            if (roll < cumulative) {
                return item;
            }
        }
        return items.get(items.size() - 1);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!sessionManager.hasActiveSession(player.getUniqueId())) {
            return;
        }
        // 인벤토리에서 지우는 것만으로는 부족함 — 죽는 순간 바닥에 떨어질 드롭 목록에는
        // 이미 폐기물이 복사되어 있어서, 그 목록에서도 따로 빼줘야 땅에 안 떨어짐.
        var pdc = ScrapyardConfig.itemPdcKey(plugin);
        event.getDrops().removeIf(stack -> sessionManager.isScrapItem(stack, pdc));
        sessionManager.forfeit(player);
        messages.send(player, "scrapyard.died");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (sessionManager.hasActiveSession(player.getUniqueId())) {
            sessionManager.forfeit(player);
        }
    }

    /**
     * 접속 종료 시 {@link ScrapyardSessionManager#forfeit}이 귀환지점으로 순간이동을
     * 시도하지만, 연결이 끊기는 도중의 순간이동은 실제 저장 위치에 반영이 안 될 때가
     * 있음(마인크래프트 자체의 알려진 동작) — 그래서 재접속했는데도 여전히 폐기장
     * 구역 안(아레나 경계 안쪽)이면 한 번 더 귀환지점으로 보내서 확실히 처리한다.
     */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!locationStore.isInsideRegion(player.getLocation())) {
            return;
        }
        locationStore.point(ScrapyardLocationStore.RETURN).ifPresent(player::teleport);
    }
}
