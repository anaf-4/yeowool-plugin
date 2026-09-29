package com.yeowool.life.fishing.orders;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.model.PlayerData;
import com.yeowool.life.fishing.FishRarity;
import com.yeowool.life.fishing.FishSpecies;
import com.yeowool.life.fishing.customfishing.CustomFishingBridge;
import com.yeowool.life.fishing.customfishing.CustomFishingNativeFishExporter;
import com.yeowool.life.fishing.orders.FishingOrderRules.Fish;
import com.yeowool.life.fishing.orders.FishingOrderRules.Species;
import com.yeowool.life.job.JobManager;
import com.yeowool.life.orders.OrderCatalog;
import com.yeowool.life.orders.OrderGui;
import com.yeowool.life.orders.OrderRepository.Order;
import com.yeowool.life.orders.OrderRules;
import com.yeowool.life.orders.OrderRules.Candidate;
import com.yeowool.life.orders.OrderRules.Difficulty;
import com.yeowool.life.orders.OrderRules.Draw;
import com.yeowool.life.orders.OrderService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.momirealms.customfishing.api.BukkitCustomFishingPlugin;
import net.momirealms.customfishing.api.mechanic.item.ItemManager;
import net.momirealms.customfishing.api.mechanic.loot.Loot;
import net.momirealms.customfishing.api.mechanic.loot.LootType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 어부 주문's items: CustomFishing item loots (시판 물고기 + our exported {@code yw_} fish). Offered = species with a
 * 도감 record, difficulty = loot weight, VIP = 희귀·전설 as 대어 or 금별. Silver/golden variants count as their base
 * species; fish are used smallest first with star variants last. The species index is rebuilt on the main thread
 * by {@link #refresh()} (CustomFishing may finish loading its loots after us).
 */
public final class FishingOrderCatalog implements OrderCatalog {

    /** Spec §9 defaults (only used for keys missing from config.yml). */
    public static final OrderRules.Settings DEFAULTS = new OrderRules.Settings(3,
            Map.of(Difficulty.EASY, new OrderRules.Tier(4, 8, 800, 1, 1, 20),
                    Difficulty.NORMAL, new OrderRules.Tier(2, 5, 2500, 2, 2, 40),
                    Difficulty.HARD, new OrderRules.Tier(1, 3, 7000, 3, 3, 80)),
            List.of(1.0, 1.5, 3.0), 3000, 3, 3000, 20,
            new OrderRules.Vip(10, 1, 1, 5, 5, 5, 150),
            new OrderRules.Group(List.of(DayOfWeek.TUESDAY, DayOfWeek.FRIDAY), LocalTime.of(19, 0), 72, 400, 200, 0.5, 5, 5, List.of(15L, 10L, 5L)),
            List.of(new OrderRules.FameLevel("초보 낚시꾼", 0, 1.0), new OrderRules.FameLevel("낚시꾼", 30, 1.1),
                    new OrderRules.FameLevel("베테랑", 100, 1.2), new OrderRules.FameLevel("명인", 250, 1.35),
                    new OrderRules.FameLevel("여울 강태공", 500, 1.5)),
            Map.of(), Map.of());

    private static final String SILVER_SUFFIX = "_silver_star";
    private static final String GOLDEN_SUFFIX = "_golden_star";
    private static final int STORAGE_SLOTS = 36;
    /** {@code 'tuna:+15'} / {@code 'group_for_each:ocean&no_star:+15'} entries in loot-conditions.yml. */
    private static final Pattern WEIGHT_ENTRY = Pattern.compile("(group_for_each:)?([\\w&]+):\\+(\\d+(?:\\.\\d+)?)");

    /** {@code tagged} false = CustomFishing doesn't mark the item, so it can't be recognised in an inventory. */
    private record ItemConfig(Double weight, double minCm, double maxCm, String name, boolean tagged) {
    }

    /** A fish stack in the player's inventory. */
    private record Slot(int slot, Fish fish) {
    }

    private final FishingOrderRules rules;
    private final YeowoolCoreAPI core;
    private final JobManager jobManager;
    private final List<FishRarity> nativeRarities;
    private final Logger log;
    private volatile Map<String, Species> species = Map.of();
    private volatile Map<String, Component> names = Map.of();

    public FishingOrderCatalog(FishingOrderRules rules, YeowoolCoreAPI core, JobManager jobManager, List<FishRarity> nativeRarities, Logger log) {
        this.rules = rules;
        this.core = core;
        this.jobManager = jobManager;
        this.nativeRarities = nativeRarities;
        this.log = log;
    }

    /** Reads the fishing-only part of the {@code fishing-orders} block (spec §9). */
    public static FishingOrderRules.Settings settings(ConfigurationSection config) {
        return new FishingOrderRules.Settings(config.getDouble("rarity-weight.normal-min", 20), config.getDouble("rarity-weight.easy-min", 40),
                config.getDouble("rarity-weight.legendary-below", 5), config.getInt("size-condition.chance-percent", 30),
                config.getDouble("size-condition.money-multiplier", 1.3), config.getDouble("size-bonus-max", 0.5),
                config.getInt("vip.big-fish-top-percent", 25),
                Set.copyOf(config.isList("exclude") ? config.getStringList("exclude") : List.of("vanilla", "stick", "shoes", "seagrass")));
    }

    // ---- species index ----

    /** Main thread: rebuilds the species index from CustomFishing's registered loots and its item/loot-condition files. */
    public void refresh() {
        Plugin customFishing = Bukkit.getPluginManager().getPlugin("CustomFishing");
        if (customFishing == null) {
            return;
        }
        Map<String, ItemConfig> configs = itemConfigs(new File(customFishing.getDataFolder(), "contents/item"));
        Map<Set<String>, Double> conditionWeights = conditionWeights(new File(customFishing.getDataFolder(), "loot-conditions.yml"));
        Map<String, Loot> loots = new HashMap<>();
        for (Loot loot : BukkitCustomFishingPlugin.getInstance().getLootManager().getRegisteredLoots()) {
            if (loot.type() == LootType.ITEM && !loot.disableStats() && !rules.settings().exclude().contains(loot.id())) {
                loots.put(loot.id(), loot);
            }
        }
        Map<String, Species> index = new LinkedHashMap<>();
        Map<String, Component> display = new HashMap<>();
        for (Loot loot : loots.values()) {
            if (loot.id().endsWith(SILVER_SUFFIX) || loot.id().endsWith(GOLDEN_SUFFIX)) {
                continue;
            }
            String id = CustomFishingNativeFishExporter.nativeId(loot.id());
            if (index.containsKey(id) && !CustomFishingNativeFishExporter.isExportedId(loot.id())) {
                continue; // our own yw_ fish wins an id clash with a CustomFishing loot of the same name
            }
            ItemConfig config = configs.getOrDefault(loot.id(), new ItemConfig(null, 0, 0, null, true));
            if (!config.tagged()) {
                continue;
            }
            double weight = config.weight() != null ? config.weight() : conditionWeight(loot, conditionWeights);
            double minCm = config.minCm();
            double maxCm = config.maxCm();
            if (maxCm <= minCm) {
                Optional<FishSpecies> fallback = nativeRarities.stream().flatMap(rarity -> rarity.species().stream())
                        .filter(s -> s.id().equals(id)).findFirst();
                minCm = fallback.map(FishSpecies::minSizeCm).orElse(0.0);
                maxCm = fallback.map(FishSpecies::maxSizeCm).orElse(0.0);
            }
            index.put(id, new Species(id, loot.id(), weight, minCm, maxCm, loots.containsKey(loot.id() + GOLDEN_SUFFIX)));
            display.put(id, config.name() == null ? Component.text(id) : MiniMessage.miniMessage().deserialize(config.name()));
        }
        if (index.isEmpty() && !species.isEmpty()) {
            return; // CustomFishing mid-reload — keep the last good index
        }
        species = Map.copyOf(index);
        names = Map.copyOf(display);
    }

    private Map<String, ItemConfig> itemConfigs(File folder) {
        Map<String, ItemConfig> configs = new HashMap<>();
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        for (File file : files == null ? new File[0] : files) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            for (String key : yaml.getKeys(false)) {
                double[] size = parseRange(yaml.getString(key + ".size"));
                configs.put(key, new ItemConfig(yaml.isSet(key + ".weight") ? yaml.getDouble(key + ".weight") : null,
                        size[0], size[1], yaml.getString(key + ".display.name"), yaml.getBoolean(key + ".tag", true)));
            }
        }
        return configs;
    }

    /** "15~50" → {15, 50}; {0, 0} if absent or unreadable. */
    static double[] parseRange(String range) {
        if (range == null) {
            return new double[2];
        }
        String[] parts = range.split("~");
        try {
            return parts.length == 2 ? new double[]{Double.parseDouble(parts[0].trim()), Double.parseDouble(parts[1].trim())} : new double[2];
        } catch (NumberFormatException e) {
            return new double[2];
        }
    }

    /** Group set (a plain loot entry is a one-element set of its id) → the largest weight it adds anywhere in the file. */
    private Map<Set<String>, Double> conditionWeights(File file) {
        Map<Set<String>, Double> weights = new HashMap<>();
        try {
            Matcher matcher = WEIGHT_ENTRY.matcher(Files.readString(file.toPath()));
            while (matcher.find()) {
                Set<String> key = matcher.group(1) != null ? Set.of(matcher.group(2).split("&")) : Set.of("loot:" + matcher.group(2));
                weights.merge(key, Double.parseDouble(matcher.group(3)), Math::max);
            }
        } catch (IOException e) {
            log.warning("CustomFishing loot-conditions.yml을 읽지 못해 가중치가 없는 물고기는 전설로 취급합니다: " + e.getMessage());
        }
        return weights;
    }

    // ponytail: max weight over every biome/condition group — fine for the grade, not an exact catch chance.
    private static double conditionWeight(Loot loot, Map<Set<String>, Double> weights) {
        Set<String> groups = new HashSet<>(Arrays.asList(loot.lootGroup() == null ? new String[0] : loot.lootGroup()));
        double weight = weights.getOrDefault(Set.of("loot:" + loot.id()), 0.0);
        for (Map.Entry<Set<String>, Double> entry : weights.entrySet()) {
            if (!entry.getKey().iterator().next().startsWith("loot:") && groups.containsAll(entry.getKey())) {
                weight = Math.max(weight, entry.getValue());
            }
        }
        return weight;
    }

    // ---- OrderCatalog ----

    @Override
    public String id() {
        return "fishing";
    }

    @Override
    public String label() {
        return "어부 주문";
    }

    @Override
    public String itemTag() {
        return "fish";
    }

    @Override
    public Material vipBorder() {
        return Material.LIGHT_BLUE_STAINED_GLASS_PANE;
    }

    @Override
    public boolean exists(String id) {
        return species.containsKey(id);
    }

    @Override
    public String resolve(String input) {
        String id = CustomFishingNativeFishExporter.nativeId(input);
        return exists(id) ? id : null;
    }

    @Override
    public Component name(String id) {
        return names.getOrDefault(id, Component.text(id));
    }

    @Override
    public ItemStack icon(String id, Player viewer) {
        Species fish = species.get(id);
        return fish == null ? null : CustomFishingBridge.buildItem(viewer, fish.lootId());
    }

    @Override
    public List<Candidate> offered(Player player) {
        Optional<PlayerData> data = core.playerData().getIfLoaded(player.getUniqueId());
        if (data.isEmpty()) {
            return List.of();
        }
        return species.values().stream().filter(fish -> caught(data.get(), fish.id())).map(rules::candidate).toList();
    }

    /** 도감 record, counting the old {@code yw_} key and the 은별/금별 variants' own counters. */
    private static boolean caught(PlayerData data, String id) {
        String key = "life.fishing.catalog.";
        return data.getStatistic(key + id) + data.getStatistic(key + "yw_" + id)
                + data.getStatistic(key + id + SILVER_SUFFIX) + data.getStatistic(key + id + GOLDEN_SUFFIX) > 0;
    }

    @Override
    public List<Candidate> all() {
        return species.values().stream().map(rules::candidate).toList();
    }

    @Override
    public Draw decorate(Random random, Draw draw) {
        return rules.decorate(random, draw, species.get(draw.itemId()));
    }

    @Override
    public int count(Player player, Order order) {
        return fish(player.getInventory(), order).stream().mapToInt(slot -> player.getInventory().getItem(slot.slot()).getAmount()).sum();
    }

    @Override
    public List<Taken> take(Player player, Order order, boolean group, int amount) {
        PlayerInventory inventory = player.getInventory();
        List<Slot> slots = fish(inventory, order);
        if (slots.stream().mapToInt(slot -> inventory.getItem(slot.slot()).getAmount()).sum() < amount) {
            return null;
        }
        Species kind = species.get(order.itemId());
        List<Taken> taken = new ArrayList<>();
        int remaining = amount;
        for (Slot slot : slots) {
            if (remaining <= 0) {
                break;
            }
            ItemStack stack = inventory.getItem(slot.slot());
            int take = Math.min(remaining, stack.getAmount());
            remaining -= take;
            String detail = slot.fish().sizeMm() == null ? "" : String.format("%.1fcm ", slot.fish().sizeMm() / 10.0);
            for (int i = 0; i < take; i++) {
                taken.add(new Taken(rules.multiplier(order, slot.fish(), kind, group), detail));
            }
            stack.setAmount(stack.getAmount() - take);
            inventory.setItem(slot.slot(), stack.getAmount() > 0 ? stack : null);
        }
        return taken;
    }

    /** Stacks of the order's species that satisfy its conditions, in delivery order. */
    private List<Slot> fish(PlayerInventory inventory, Order order) {
        ItemManager items = BukkitCustomFishingPlugin.getInstance().getItemManager();
        List<Slot> slots = new ArrayList<>();
        for (int slot = 0; slot < STORAGE_SLOTS; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            String lootId = items.getCustomFishingItemID(stack);
            if (lootId == null) {
                continue;
            }
            int star = lootId.endsWith(GOLDEN_SUFFIX) ? OrderRules.GOLDEN : lootId.endsWith(SILVER_SUFFIX) ? OrderRules.SILVER : OrderRules.NORMAL;
            String base = star == OrderRules.NORMAL ? lootId
                    : lootId.substring(0, lootId.length() - (star == OrderRules.GOLDEN ? GOLDEN_SUFFIX : SILVER_SUFFIX).length());
            if (rules.settings().exclude().contains(base) || !CustomFishingNativeFishExporter.nativeId(base).equals(order.itemId())) {
                continue;
            }
            Float sizeCm = items.getFishSize(stack);
            Fish fish = new Fish(star, sizeCm == null ? null : Math.round(sizeCm * 10));
            if (FishingOrderRules.matches(order, fish)) {
                slots.add(new Slot(slot, fish));
            }
        }
        slots.sort((a, b) -> FishingOrderRules.DELIVERY_ORDER.compare(a.fish(), b.fish()));
        return slots;
    }

    @Override
    public void conditionLore(Order order, OrderGui gui, List<Component> lore) {
        if (order.vip()) {
            lore.add(FishingOrderRules.BIG.equals(order.vipKind())
                    ? gui.line("fishing-orders.gui.order-vip-big", Placeholder.unparsed("size", OrderService.cm(order.minSizeMm())))
                    : gui.line("fishing-orders.gui.order-vip-golden"));
            return;
        }
        if (order.minSizeMm() > 0) {
            lore.add(gui.line("fishing-orders.gui.order-size", Placeholder.unparsed("size", OrderService.cm(order.minSizeMm())),
                    Placeholder.unparsed("multiplier", OrderGui.multiplier(rules.settings().conditionMultiplier()))));
        }
        Species fish = species.get(order.itemId());
        if (fish != null && fish.hasSize()) {
            lore.add(gui.line("fishing-orders.gui.order-size-bonus",
                    Placeholder.unparsed("bonus", OrderGui.multiplier(1 + rules.settings().sizeBonusMax()))));
        }
    }

    @Override
    public void grantJobXp(Player player, long jobXp) {
        jobManager.grantXp(player, "fisherman", jobXp);
    }
}
