package com.yeowool.market.merchant;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
import com.yeowool.market.npcshop.NPCShopGui;
import com.yeowool.market.npcshop.ShopDefinition;
import com.yeowool.market.npcshop.ShopOpenGate;
import com.yeowool.market.npcshop.ShopRotationManager;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.npc.MemoryNPCDataStore;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import com.yeowool.core.api.event.ShopOpenEvent;

import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Drives the one wandering merchant shared by all servers. Every server runs
 * {@link #tick()} each minute: it advances the shared state when a spawn or
 * despawn is due (only one server's conditional UPDATE wins), then on the
 * main thread keeps a Citizens NPC standing here only while the merchant's
 * spot is on this server, and announces every state change once. NPCs live
 * in an anonymous in-memory registry, so nothing is saved to Citizens'
 * saves.yml and a restart simply respawns it on the next tick.
 */
public final class MerchantService implements Listener {

    public record Settings(String thisServerId, String shopId, String npcName, EntityType entityType, int stayMinutes,
                           int intervalMinMinutes, int intervalMaxMinutes, Map<String, String> serverNames) {

        public String serverName(String serverId) {
            return serverNames.getOrDefault(serverId, serverId);
        }
    }

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private final MessageService messages;
    private final MerchantRepository repository;
    private final Settings settings;
    private final Map<String, ShopDefinition> shops;
    private final ShopRotationManager rotationManager;
    private final Random random = new Random();
    private volatile boolean warnedNoSpots;

    private static final int MERCHANT_NPC_ID = Integer.MAX_VALUE - 1;

    // main thread only
    private MerchantRepository.State current;
    private long announcedSeq = -1;
    private NPCRegistry registry;
    private NPC npc;
    private long npcSeq = -1;
    private long warnedWorldSeq = -1;
    private boolean openingFromNpc;

    public MerchantService(JavaPlugin plugin, YeowoolCoreAPI core, MessageService messages, MerchantRepository repository,
                           Settings settings, Map<String, ShopDefinition> shops, ShopRotationManager rotationManager) {
        this.plugin = plugin;
        this.core = core;
        this.messages = messages;
        this.repository = repository;
        this.settings = settings;
        this.shops = shops;
        this.rotationManager = rotationManager;
    }

    public Settings settings() {
        return settings;
    }

    public MerchantRepository repository() {
        return repository;
    }

    /** Main thread: the last state this server saw, null until the first tick finishes. */
    public MerchantRepository.State current() {
        return current;
    }

    /** Executor thread: advance the shared state if a transition is due, then apply the latest state on the main thread. */
    public void tick() {
        try {
            long now = System.currentTimeMillis();
            MerchantRepository.State state = repository.state();
            if (!state.active() && now >= state.nextSpawnAt()) {
                Optional<MerchantRepository.Spot> spot = MerchantRules.pick(repository.spots(), random);
                if (spot.isEmpty()) {
                    if (!warnedNoSpots) {
                        warnedNoSpots = true;
                        plugin.getLogger().warning("떠돌이 상인 후보 지점이 없습니다 — /떠돌이상인 위치추가 <이름>으로 등록하세요.");
                    }
                } else {
                    warnedNoSpots = false;
                    repository.spawn(state.seq(), spot.get(), now + settings.stayMinutes() * 60_000L);
                    state = repository.state();
                }
            } else if (state.active() && now >= state.despawnAt()) {
                long next = now + MerchantRules.nextDelayMillis(random, settings.intervalMinMinutes(), settings.intervalMaxMinutes());
                repository.despawn(state.seq(), next);
                state = repository.state();
            }
            MerchantRepository.State latest = state;
            if (plugin.isEnabled()) {
                Bukkit.getScheduler().runTask(plugin, () -> apply(latest));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "떠돌이 상인 상태 처리 실패", e);
        }
    }

    private void apply(MerchantRepository.State state) {
        if (current != null && state.seq() < current.seq()) {
            return; // an older read finished after a newer one
        }
        current = state;
        boolean here = state.active() && settings.thisServerId().equals(state.spot().serverId());
        if (!here || npcSeq != state.seq()) {
            removeNpc();
        }
        if (here && npc == null) {
            spawnNpc(state);
        }
        if (announcedSeq < 0) {
            announcedSeq = state.seq(); // first read after startup: whatever happened before isn't news
        } else if (state.seq() != announcedSeq) {
            announcedSeq = state.seq();
            announce(state);
        }
    }

    private void spawnNpc(MerchantRepository.State state) {
        MerchantRepository.Spot spot = state.spot();
        World world = Bukkit.getWorld(spot.world());
        if (world == null) {
            if (warnedWorldSeq != state.seq()) {
                warnedWorldSeq = state.seq();
                plugin.getLogger().warning("떠돌이 상인 지점 '" + spot.name() + "'의 월드 '" + spot.world() + "'를 찾을 수 없어 소환하지 못했습니다.");
            }
            return;
        }
        if (registry == null) {
            registry = CitizensAPI.createAnonymousNPCRegistry(new MemoryNPCDataStore());
        }
        npc = registry.createNPC(settings.entityType(), UUID.randomUUID(), MERCHANT_NPC_ID, settings.npcName());
        npc.spawn(new Location(world, spot.x(), spot.y(), spot.z(), spot.yaw(), spot.pitch()));
        npcSeq = state.seq();
    }

    /** Main thread. Also called from onDisable. */
    public void removeNpc() {
        if (npc != null) {
            npc.destroy();
            npc = null;
        }
        npcSeq = -1;
    }

    private void announce(MerchantRepository.State state) {
        if (!state.active()) {
            messages.broadcast("merchant.left");
            return;
        }
        long minutes = Math.max(1, (state.despawnAt() - System.currentTimeMillis() + 59_999) / 60_000);
        messages.broadcast("merchant.appeared",
                Placeholder.unparsed("server", settings.serverName(state.spot().serverId())),
                Placeholder.unparsed("spot", state.spot().name()),
                Placeholder.unparsed("minutes", String.valueOf(minutes)));
    }

    @EventHandler
    public void onRightClick(NPCRightClickEvent event) {
        if (npc == null || event.getNPC() != npc) {
            return;
        }
        Player player = event.getClicker();
        ShopDefinition shop = shops.get(settings.shopId());
        if (shop == null) {
            messages.send(player, "merchant.shop-missing");
            return;
        }
        openingFromNpc = true;
        try {
            if (ShopOpenGate.allows(player, shop.id())) {
                new NPCShopGui(plugin, core, messages, shops, shop, rotationManager, 0).open(player);
            }
        } finally {
            openingFromNpc = false;
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onShopOpen(ShopOpenEvent event) {
        if (!openingFromNpc && settings.shopId().equals(event.getShopId())) {
            event.setCancelled(true);
            messages.send(event.getPlayer(), "merchant.shop-npc-only");
        }
    }
}
