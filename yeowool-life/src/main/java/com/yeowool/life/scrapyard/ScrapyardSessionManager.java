package com.yeowool.life.scrapyard;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.model.PlayerData;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * "여울 폐기장" 하루 1회 입장 제한 + 진행 중 세션 상태. 입장 가능 여부(오늘 이미
 * 도전했는지)는 공유 {@code PlayerData} 설정값이라 분할서버 어디서 입장해도
 * 정확하지만, 진행 중 세션 자체(시작 시각)는 이 서버 프로세스의 메모리에만
 * 있음 — 폐기장은 물리적으로 한 서버에만 존재하는 하나의 장소라 세션 중
 * 다른 서버로 이동한다는 개념이 없고, 만약 그런 일이 생기면(플레이어가
 * 연결을 끊는 등) 그냥 실패 처리한다({@link ScrapyardTickTask}가 감지).
 */
public final class ScrapyardSessionManager {

    private static final String LAST_ENTER_DATE_SETTING = "scrapyard.last-enter-date";

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private final ScrapyardLocationStore locationStore;
    private final ScrapyardConfig config;
    private final Map<UUID, Long> sessionStartedAt = new ConcurrentHashMap<>();
    private final Set<UUID> bossSpawnedFor = ConcurrentHashMap.newKeySet();

    public ScrapyardSessionManager(JavaPlugin plugin, YeowoolCoreAPI core, ScrapyardLocationStore locationStore, ScrapyardConfig config) {
        this.plugin = plugin;
        this.core = core;
        this.locationStore = locationStore;
        this.config = config;
    }

    private String today() {
        return LocalDate.now(ZoneId.systemDefault()).toString();
    }

    public boolean isLockedToday(UUID uuid) {
        var data = core.playerData().getIfLoaded(uuid);
        return data.isPresent() && today().equals(data.get().getSetting(LAST_ENTER_DATE_SETTING, ""));
    }

    /** {@code /폐기장설정 초기화} — clears today's entry lock so the player can go in again right away. */
    public void resetLockToday(PlayerData data) {
        data.setSetting(LAST_ENTER_DATE_SETTING, "");
    }

    public boolean hasActiveSession(UUID uuid) {
        return sessionStartedAt.containsKey(uuid);
    }

    public enum EnterResult { OK, ALREADY_LOCKED_TODAY, ALREADY_ACTIVE, NOT_CONFIGURED }

    public EnterResult enter(Player player) {
        Location destination = locationStore.point(ScrapyardLocationStore.ENTRY_DESTINATION).orElse(null);
        if (destination == null || locationStore.point(ScrapyardLocationStore.RETURN).isEmpty() || !locationStore.hasRegion()) {
            return EnterResult.NOT_CONFIGURED;
        }
        if (hasActiveSession(player.getUniqueId())) {
            return EnterResult.ALREADY_ACTIVE;
        }
        if (isLockedToday(player.getUniqueId())) {
            return EnterResult.ALREADY_LOCKED_TODAY;
        }
        core.playerData().getIfLoaded(player.getUniqueId())
                .ifPresent(data -> data.setSetting(LAST_ENTER_DATE_SETTING, today()));
        sessionStartedAt.put(player.getUniqueId(), System.currentTimeMillis());
        bossSpawnedFor.remove(player.getUniqueId());
        player.teleport(destination);
        return EnterResult.OK;
    }

    /** Empty if there's no active session for this player. */
    public Optional<Long> remainingMillis(UUID uuid) {
        Long startedAt = sessionStartedAt.get(uuid);
        if (startedAt == null) {
            return Optional.empty();
        }
        long elapsed = System.currentTimeMillis() - startedAt;
        long total = config.sessionDurationSeconds() * 1000L;
        return Optional.of(Math.max(0, total - elapsed));
    }

    /** Empty if there's no active session for this player. */
    public Optional<Long> elapsedMillis(UUID uuid) {
        Long startedAt = sessionStartedAt.get(uuid);
        return startedAt == null ? Optional.empty() : Optional.of(System.currentTimeMillis() - startedAt);
    }

    /** {@link ScrapyardConfig#hasBoss()}가 true일 때만 의미 있음 — 이번 세션에서 보스를 아직 안 불렀으면 true. */
    public boolean shouldSpawnBoss(UUID uuid) {
        return hasActiveSession(uuid) && !bossSpawnedFor.contains(uuid);
    }

    public void markBossSpawned(UUID uuid) {
        bossSpawnedFor.add(uuid);
    }

    /** 실패(사망/시간초과/구역 이탈/접속종료) — 소지 중인 폐기물을 전부 비우고 세션을 끝낸다. */
    public void forfeit(Player player) {
        if (sessionStartedAt.remove(player.getUniqueId()) == null) {
            return;
        }
        bossSpawnedFor.remove(player.getUniqueId());
        clearScrapItems(player);
        teleportToReturn(player);
    }

    /** 탈출 성공 — 소지품은 그대로 두고 세션을 끝낸다. */
    public void succeed(Player player) {
        if (sessionStartedAt.remove(player.getUniqueId()) == null) {
            return;
        }
        bossSpawnedFor.remove(player.getUniqueId());
        teleportToReturn(player);
    }

    private void teleportToReturn(Player player) {
        Location returnPoint = locationStore.point(ScrapyardLocationStore.RETURN).orElse(null);
        if (returnPoint != null) {
            player.teleport(returnPoint);
        }
    }

    private void clearScrapItems(Player player) {
        var pdc = ScrapyardConfig.itemPdcKey(plugin);
        var contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (isScrapItem(stack, pdc)) {
                player.getInventory().setItem(i, null);
            }
        }
    }

    public boolean isScrapItem(ItemStack stack, org.bukkit.NamespacedKey pdcKey) {
        return stack != null && !stack.getType().isAir() && stack.hasItemMeta()
                && stack.getItemMeta().getPersistentDataContainer().has(pdcKey, PersistentDataType.STRING);
    }

    /** Total configured weight of every scrap item currently in {@code player}'s inventory. */
    public int carriedWeight(Player player) {
        var pdc = ScrapyardConfig.itemPdcKey(plugin);
        int total = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (!isScrapItem(stack, pdc)) {
                continue;
            }
            String id = stack.getItemMeta().getPersistentDataContainer().get(pdc, PersistentDataType.STRING);
            ScrapItem item = config.scrapItems().get(id);
            if (item != null) {
                total += item.weight() * stack.getAmount();
            }
        }
        return total;
    }
}
