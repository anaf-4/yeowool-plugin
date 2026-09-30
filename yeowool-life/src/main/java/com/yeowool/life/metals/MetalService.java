package com.yeowool.life.metals;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
import com.yeowool.core.util.ConfigMerger;
import com.yeowool.life.job.JobManager;
import com.yeowool.life.metals.MetalConfig.Conversion;
import com.yeowool.life.metals.MetalConfig.Metal;
import com.yeowool.life.metals.MetalConfig.Recipe;
import com.yeowool.life.metals.MetalConfig.Tier;
import com.yeowool.life.mining.MiningListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.logging.Level;

/**
 * 판타지 금속 (spec 2026-09-30-fantasy-metals): 원석 drops while mining in {@code metals.yml worlds}, and the 대장간
 * actions (제련·변환·제작). Every inventory/balance step runs on the main thread and re-counts the inventory right before
 * taking; JDBC (legendary-find announcements) only on the worker.
 */
public final class MetalService {

    private static final String SOURCE = "YeowoolLife";
    private static final String MINER_JOB = "miner";

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private final MessageService messages;
    private final JobManager jobManager;
    private final MetalAnnouncementRepository repository;
    private final Executor executor;
    private final Random random = new Random();
    private final File npcFile;
    private final Set<Integer> npcIds = new LinkedHashSet<>();
    private final AtomicBoolean polling = new AtomicBoolean();
    private volatile MetalConfig config;
    private volatile long announcedUntil;
    private boolean warnedMissingPack;

    MetalService(JavaPlugin plugin, YeowoolCoreAPI core, MessageService messages, JobManager jobManager,
                 MetalAnnouncementRepository repository, Executor executor, long announcedUntil) {
        this.plugin = plugin;
        this.core = core;
        this.messages = messages;
        this.jobManager = jobManager;
        this.repository = repository;
        this.executor = executor;
        this.announcedUntil = announcedUntil;
        this.npcFile = new File(plugin.getDataFolder(), "smithy-npcs.yml");
        npcIds.addAll(YamlConfiguration.loadConfiguration(npcFile).getIntegerList("npc-ids"));
        reload();
    }

    /** Wires the 대장간: table, service, command, NPC listener, announcement poll. Null (and logged) when the DB failed. */
    public static MetalService start(JavaPlugin plugin, YeowoolCoreAPI core, MessageService messages, JobManager jobManager, Executor executor) {
        MetalAnnouncementRepository repository = new MetalAnnouncementRepository(core.dataSource());
        long announcedUntil;
        try {
            repository.createTable();
            announcedUntil = repository.maxId();
        } catch (SQLException e) {
            plugin.getLogger().severe("대장간 데이터베이스 초기화 실패 — 판타지 금속을 끕니다: " + e.getMessage());
            return null;
        }
        MetalService service = new MetalService(plugin, core, messages, jobManager, repository, executor, announcedUntil);
        var command = plugin.getCommand("대장간관리");
        if (command != null) {
            var executorCmd = new SmithyCommand(service, messages);
            command.setExecutor(executorCmd);
            command.setTabCompleter(executorCmd);
        }
        if (Bukkit.getPluginManager().isPluginEnabled("Citizens")) {
            Bukkit.getPluginManager().registerEvents(new com.yeowool.life.orders.OrderNpcListener(service::isNpc, service::open), plugin);
        } else {
            plugin.getLogger().warning("Citizens가 없어 이 서버에서는 대장간 NPC를 쓸 수 없습니다.");
        }
        Bukkit.getScheduler().runTaskTimer(plugin, () -> executor.execute(service::pollAnnouncements), 20L * 20, 20L * 20);
        return service;
    }

    public void reload() {
        ConfigMerger.mergeDefaults(plugin, "metals.yml");
        config = MetalConfig.load(YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "metals.yml")), plugin.getLogger());
        warnedMissingPack = false;
    }

    MetalConfig config() {
        return config;
    }

    public int metalCount() {
        return config.metals().size();
    }

    MessageService messages() {
        return messages;
    }

    // ---- 원석 drops (called from MiningListener, which already skips player-placed blocks) ----

    /** Blocks MiningListener must remember when a player places them, beyond ores: the stone-type hosts in metal worlds. */
    public boolean tracksPlaced(Block block) {
        MetalConfig current = config;
        return current.enabled() && current.stoneBlocks().contains(block.getType()) && current.worlds().contains(block.getWorld().getName());
    }

    /** A naturally generated block was broken (not player-placed). */
    public void onNaturalBreak(BlockBreakEvent event) {
        MetalConfig current = config;
        Block block = event.getBlock();
        if (!current.enabled() || !current.worlds().contains(block.getWorld().getName())) {
            return;
        }
        Material type = block.getType();
        double chance = MiningListener.isOre(type) ? current.oreChance() : current.stoneBlocks().contains(type) ? current.stoneChance() : 0;
        Player player = event.getPlayer();
        if (chance <= 0 || player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR
                || block.getDrops(player.getInventory().getItemInMainHand(), player).isEmpty() // wrong tool — vanilla drops nothing either
                || !MetalConfig.rollDrop(random, chance)) {
            return;
        }
        String dimension = block.getWorld().getEnvironment() == World.Environment.NETHER ? MetalConfig.NETHER : MetalConfig.OVERWORLD;
        Metal metal = current.pick(random, dimension, block.getY()).orElse(null);
        if (metal == null) {
            return;
        }
        ItemStack raw = MetalItems.create(metal.id(), MetalConfig.RAW, 1);
        if (raw == null) {
            if (!warnedMissingPack) {
                warnedMissingPack = true;
                plugin.getLogger().warning("ItemsAdder에서 " + MetalItems.NAMESPACE + ":" + metal.id() + MetalConfig.RAW
                        + "을(를) 찾지 못해 원석 드롭을 건너뜁니다 (bundle_metals 팩 확인).");
            }
            return;
        }
        block.getWorld().dropItemNaturally(block.getLocation(), raw);
        MetalConfig.TierInfo tier = current.tier(metal.tier());
        player.sendActionBar(messages.resolveRaw("metals.found",
                Placeholder.component("item", name(metal, "metals.form.raw")), tierTag(metal.tier())));
        player.playSound(player.getLocation(), metal.tier() == Tier.LEGENDARY ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.2f);
        core.playerData().getIfLoaded(player.getUniqueId()).ifPresent(data -> data.addStatistic("life.metals.found." + metal.id(), 1));
        jobManager.grantXp(player, MINER_JOB, tier.xp());
        if (metal.tier() == Tier.LEGENDARY && current.announceLegendary()) {
            String playerName = player.getName();
            executor.execute(() -> {
                try {
                    repository.insert(playerName, metal.id());
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.WARNING, "전설 원석 공지 저장 실패", e);
                }
            });
        }
    }

    /** Worker, every 20 s on every server: broadcasts legendary finds from any server. */
    void pollAnnouncements() {
        if (!polling.compareAndSet(false, true)) {
            return;
        }
        try {
            List<MetalAnnouncementRepository.Find> finds = repository.after(announcedUntil);
            if (finds.isEmpty()) {
                return;
            }
            announcedUntil = finds.get(finds.size() - 1).id();
            if (plugin.isEnabled()) {
                Bukkit.getScheduler().runTask(plugin, () -> finds.forEach(find -> {
                    Metal metal = config.metals().get(find.metalId());
                    Component metalName = metal == null ? Component.text(find.metalId()) : name(metal, "metals.form.raw");
                    messages.broadcast("metals.legendary-broadcast", Placeholder.unparsed("player", find.player()),
                            Placeholder.component("metal", metalName));
                }));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "전설 원석 공지 조회 실패", e);
        } finally {
            polling.set(false);
        }
    }

    // ---- display helpers ----

    static NamedTextColor color(MetalConfig config, Tier tier) {
        NamedTextColor color = NamedTextColor.NAMES.value(config.tier(tier).color());
        return color == null ? NamedTextColor.WHITE : color;
    }

    /** "푸른 강철 원석" in the tier color; {@code formKey} = metals.form.raw / metals.form.ingot. */
    Component name(Metal metal, String formKey) {
        return Component.text(metal.name() + " ").append(messages.resolveRaw(formKey)).color(color(config, metal.tier()));
    }

    TagResolver tierTag(Tier tier) {
        return Placeholder.component("tier", Component.text(config.tier(tier).name(), color(config, tier)));
    }

    static Predicate<String> item(Metal metal, int suffix) {
        String id = metal.id() + suffix;
        return id::equals;
    }

    /** Ingots (suffix 4) of any metal of {@code tier}. */
    Predicate<String> ingotsOf(Tier tier) {
        Map<String, Metal> metals = config.metals();
        return id -> {
            if (!id.endsWith(String.valueOf(MetalConfig.INGOT))) {
                return false;
            }
            Metal metal = metals.get(id.substring(0, id.length() - 1));
            return metal != null && metal.tier() == tier;
        };
    }

    // ---- NPC binding (per server) ----

    public boolean isNpc(int npcId) {
        return npcIds.contains(npcId);
    }

    boolean toggleNpc(int npcId) {
        boolean bound = npcIds.add(npcId) || !npcIds.remove(npcId);
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("npc-ids", new ArrayList<>(npcIds));
        try {
            yaml.save(npcFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, npcFile.getName() + " 저장 실패", e);
        }
        return bound;
    }

    public void open(Player player) {
        if (!config.enabled()) {
            messages.send(player, "metals.disabled");
            return;
        }
        new SmithyGui(this, player).open(player);
    }

    // ---- 대장간 actions (main thread; each re-checks the inventory right before taking) ----

    /** 제련: raw ×{@code rawPerIngot} + 온 → 1 ingot, {@code wanted} times at most. */
    void smelt(Player player, Metal metal, int wanted) {
        MetalConfig current = config;
        UUID uuid = player.getUniqueId();
        int raw = MetalItems.count(player, item(metal, MetalConfig.RAW));
        int times = current.smeltTimes(raw, core.economyData().getBalance(uuid), metal.tier(), wanted);
        if (times <= 0) {
            boolean noRaw = raw < current.rawPerIngot();
            messages.send(player, noRaw ? "metals.smelt-no-raw" : "metals.no-money",
                    Placeholder.component("item", name(metal, "metals.form.raw")),
                    Placeholder.unparsed("amount", String.valueOf(current.rawPerIngot())),
                    Placeholder.unparsed("cost", money(current.smeltCost(metal.tier(), 1))));
            return;
        }
        ItemStack ingots = MetalItems.create(metal.id(), MetalConfig.INGOT, times);
        if (ingots == null) {
            messages.send(player, "metals.pack-missing");
            return;
        }
        long cost = current.smeltCost(metal.tier(), times);
        if (cost > 0 && !core.economyData().modifyBalance(uuid, -cost, SOURCE, "대장간 제련 (" + metal.id() + " ×" + times + ")")) {
            messages.send(player, "metals.no-money", Placeholder.unparsed("cost", money(cost)));
            return;
        }
        MetalItems.take(player, item(metal, MetalConfig.RAW), times * current.rawPerIngot());
        MetalItems.give(player, ingots);
        player.playSound(player.getLocation(), Sound.BLOCK_BLASTFURNACE_FIRE_CRACKLE, 1f, 1f);
        messages.send(player, "metals.smelted", Placeholder.component("item", name(metal, "metals.form.ingot")),
                Placeholder.unparsed("amount", String.valueOf(times)), Placeholder.unparsed("cost", money(cost)));
    }

    /** 변환: {@code ingots} ingots ↔ 1 decorative item, {@code times} times (fewer if the player has less). */
    void convert(Player player, Metal metal, Conversion conversion, boolean toIngots, int times) {
        Predicate<String> from = toIngots ? item(metal, conversion.suffix()) : item(metal, MetalConfig.INGOT);
        int per = toIngots ? 1 : conversion.ingots();
        int possible = Math.min(times, MetalItems.count(player, from) / per);
        if (possible <= 0) {
            messages.send(player, "metals.convert-missing", Placeholder.unparsed("amount", String.valueOf(per)));
            return;
        }
        int outSuffix = toIngots ? MetalConfig.INGOT : conversion.suffix();
        int outAmount = toIngots ? possible * conversion.ingots() : possible;
        ItemStack out = MetalItems.create(metal.id(), outSuffix, outAmount);
        if (out == null) {
            messages.send(player, "metals.pack-missing");
            return;
        }
        MetalItems.take(player, from, possible * per);
        MetalItems.give(player, out);
        player.playSound(player.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 1f, 1f);
        messages.send(player, "metals.converted", Placeholder.unparsed("amount", String.valueOf(outAmount)));
    }

    /** 제작: 등급별 주괴 (any metal of that tier) + 온 → 강화 촉진제 / 파괴 방지 부적. */
    void craft(Player player, Recipe recipe) {
        for (Map.Entry<Tier, Integer> need : recipe.ingots().entrySet()) {
            if (MetalItems.count(player, ingotsOf(need.getKey())) < need.getValue()) {
                messages.send(player, "metals.craft-missing", tierTag(need.getKey()),
                        Placeholder.unparsed("amount", String.valueOf(need.getValue())));
                return;
            }
        }
        UUID uuid = player.getUniqueId();
        if (recipe.money() > 0 && !core.economyData().modifyBalance(uuid, -recipe.money(), SOURCE, "대장간 제작 (" + recipe.key() + ")")) {
            messages.send(player, "metals.no-money", Placeholder.unparsed("cost", money(recipe.money())));
            return;
        }
        recipe.ingots().forEach((tier, amount) -> MetalItems.take(player, ingotsOf(tier), amount));
        MetalItems.give(player, MetalItems.aid(recipe, messages, 1));
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.2f);
        messages.send(player, "metals.crafted", Placeholder.unparsed("name", recipe.name()));
    }

    static String money(long amount) {
        return String.format("%,d", amount);
    }
}
