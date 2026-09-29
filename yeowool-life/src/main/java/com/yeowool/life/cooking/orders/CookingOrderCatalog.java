package com.yeowool.life.cooking.orders;

import com.yeowool.life.cooking.addcook.AddCookRecipeIndex;
import com.yeowool.life.cooking.addcook.AddCookRecipeIndex.RecipeEntry;
import com.yeowool.life.orders.OrderCatalog;
import com.yeowool.life.orders.OrderGui;
import com.yeowool.life.orders.OrderRepository.Order;
import com.yeowool.life.orders.OrderRules;
import com.yeowool.life.orders.OrderRules.Candidate;
import com.yeowool.life.orders.OrderRules.Difficulty;
import dev.lone.itemsadder.api.CustomStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * 요리 주문's items: AddCook recipes. Offered = learned recipes (their permission), difficulty = ingredient
 * stage count, VIP = recipes with a 금 tier and takes 금 only; dishes are used 금 → 은 → 일반 and pay by quality.
 */
public final class CookingOrderCatalog implements OrderCatalog {

    /** Fallbacks for keys missing from config.yml (same as before the shared engine). */
    public static final OrderRules.Settings DEFAULTS = new OrderRules.Settings(3,
            Map.of(Difficulty.EASY, new OrderRules.Tier(3, 6, 1500, 1, 1, 0),
                    Difficulty.NORMAL, new OrderRules.Tier(2, 4, 4000, 2, 2, 0),
                    Difficulty.HARD, new OrderRules.Tier(1, 3, 10000, 3, 3, 0)),
            List.of(1.0, 1.5, 3.0), 5000, 3, 5000, 20,
            new OrderRules.Vip(10, 1, 2, 5, 5, 5, 0),
            new OrderRules.Group(List.<DayOfWeek>of(), LocalTime.of(19, 0), 72, 300, 150, 0.5, 5, 5, List.of(15L, 10L, 5L)),
            List.of(new OrderRules.FameLevel("견습", 0, 1.0)), Map.of(), Map.of());

    /** One accepted dish item (ItemsAdder namespaced id, or a vanilla material name) and its quality index. */
    private record Dish(String key, int quality) {
    }

    private static final int STORAGE_SLOTS = 36;

    private final OrderRules rules;
    private final Map<String, RecipeEntry> recipes = new LinkedHashMap<>();

    public CookingOrderCatalog(OrderRules rules, List<RecipeEntry> recipes) {
        this.rules = rules;
        recipes.forEach(recipe -> this.recipes.put(recipe.id(), recipe));
    }

    public static Difficulty byStages(int stages) {
        return stages <= 1 ? Difficulty.EASY : stages == 2 ? Difficulty.NORMAL : Difficulty.HARD;
    }

    /** What the rules need about a recipe: stage count and number of result tiers (3 = has 금). */
    static Candidate candidate(OrderRules rules, String id, int stages, int qualities) {
        return new Candidate(id, rules.difficulty(id, byStages(stages)), qualities > OrderRules.GOLDEN, false);
    }

    private Candidate candidate(RecipeEntry entry) {
        return candidate(rules, entry.id(), entry.stages().size(), Math.min(3, entry.results().size()));
    }

    @Override
    public String id() {
        return "cooking";
    }

    @Override
    public String label() {
        return "요리 주문";
    }

    @Override
    public String itemTag() {
        return "dish";
    }

    @Override
    public Material vipBorder() {
        return Material.YELLOW_STAINED_GLASS_PANE;
    }

    @Override
    public boolean exists(String id) {
        return recipes.containsKey(id);
    }

    @Override
    public Component name(String id) {
        RecipeEntry recipe = recipes.get(id);
        return recipe == null ? Component.text(id) : MiniMessage.miniMessage().deserialize(recipe.displayName());
    }

    @Override
    public ItemStack icon(String id, Player viewer) {
        RecipeEntry recipe = recipes.get(id);
        return recipe == null ? null : AddCookRecipeIndex.resolveIcon(recipe.iconId()).clone();
    }

    @Override
    public List<Candidate> offered(Player player) {
        return recipes.values().stream().filter(recipe -> player.hasPermission(recipe.permission())).map(this::candidate).toList();
    }

    @Override
    public List<Candidate> all() {
        return recipes.values().stream().map(this::candidate).toList();
    }

    @Override
    public int count(Player player, Order order) {
        RecipeEntry recipe = recipes.get(order.itemId());
        return recipe == null ? 0 : count(player.getInventory(), dishes(recipe, order.vip()));
    }

    @Override
    public List<Taken> take(Player player, Order order, boolean group, int amount) {
        RecipeEntry recipe = recipes.get(order.itemId());
        if (recipe == null) {
            return null;
        }
        int[] taken = remove(player.getInventory(), dishes(recipe, order.vip()), amount);
        if (taken == null) {
            return null;
        }
        List<Taken> items = new ArrayList<>();
        for (int quality = 0; quality < taken.length; quality++) {
            for (int i = 0; i < taken[quality]; i++) {
                items.add(new Taken(rules.settings().itemMultipliers().get(quality), ""));
            }
        }
        return items;
    }

    @Override
    public void conditionLore(Order order, OrderGui gui, List<Component> lore) {
        if (order.vip()) {
            lore.add(gui.line("cooking-orders.gui.order-vip-only"));
        }
    }

    // ---- inventory ----

    /** Accepted dish items, 금 → 은 → 일반 (the order they're used in); VIP takes 금 only. */
    private static List<Dish> dishes(RecipeEntry recipe, boolean goldenOnly) {
        List<Dish> dishes = new ArrayList<>();
        for (int quality = Math.min(3, recipe.results().size()) - 1; quality >= 0; quality--) {
            if (goldenOnly && quality != OrderRules.GOLDEN) {
                continue;
            }
            String id = recipe.results().get(quality).itemId();
            dishes.add(new Dish(id.startsWith("ia:") ? id.substring("ia:".length()) : id, quality));
        }
        return dishes;
    }

    private static String itemKey(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return null;
        }
        CustomStack custom = CustomStack.byItemStack(stack);
        return custom != null ? custom.getNamespacedID() : stack.getType().name();
    }

    private static int count(PlayerInventory inventory, List<Dish> dishes) {
        int have = 0;
        for (int slot = 0; slot < STORAGE_SLOTS; slot++) {
            ItemStack stack = inventory.getItem(slot);
            String key = itemKey(stack);
            if (key != null && dishes.stream().anyMatch(dish -> dish.key().equals(key))) {
                have += stack.getAmount();
            }
        }
        return have;
    }

    /** Takes exactly {@code amount} dishes (금 first); per-quality counts taken, or null (nothing taken) if too few. */
    private static int[] remove(PlayerInventory inventory, List<Dish> dishes, int amount) {
        if (count(inventory, dishes) < amount) {
            return null;
        }
        int[] taken = new int[3];
        int remaining = amount;
        for (Dish dish : dishes) {
            for (int slot = 0; slot < STORAGE_SLOTS && remaining > 0; slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (dish.key().equals(itemKey(stack))) {
                    int take = Math.min(remaining, stack.getAmount());
                    remaining -= take;
                    taken[dish.quality()] += take;
                    stack.setAmount(stack.getAmount() - take);
                    inventory.setItem(slot, stack.getAmount() > 0 ? stack : null);
                }
            }
        }
        return taken;
    }
}
