package com.yeowool.life.orders;

import com.yeowool.life.orders.OrderRepository.Order;
import com.yeowool.life.orders.OrderRules.Candidate;
import com.yeowool.life.orders.OrderRules.Draw;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Random;

/**
 * What one order system (요리 / 어부) orders: the items a player may be offered, how inventory items
 * match an order and what each is worth, and the system's names. The {@link OrderService} does the rest.
 * Methods taking a {@link Player} run on the main thread; {@link #all()} and {@link #decorate} may run on the worker.
 */
public interface OrderCatalog {

    /** One item taken from an inventory: its reward multiplier and a short text for announcements ("42.3cm ", or ""). */
    record Taken(double multiplier, String detail) {
    }

    /** {@code cooking} / {@code fishing}: config block and message prefix {@code <id>-orders}, stardust cap key, {@code <id>-npcs.yml}. */
    String id();

    /** "요리 주문" — for logs and ledger reasons. */
    String label();

    /** The placeholder an item's name goes into in messages ({@code dish} / {@code fish}). */
    String itemTag();

    Material vipBorder();

    /** Whether {@code id} can still be ordered (a recipe/species that vanished from the config shows "준비 중단"). */
    boolean exists(String id);

    /** An admin-typed id as this catalog's id, or null if unknown. */
    default String resolve(String input) {
        return exists(input) ? input : null;
    }

    Component name(String id);

    /** A fresh icon for {@code id}, or null if it no longer exists. */
    ItemStack icon(String id, Player viewer);

    /** Items this player may be offered today. */
    List<Candidate> offered(Player player);

    /** Every orderable item (group orders). */
    List<Candidate> all();

    /** Adds catalog conditions (size, VIP kind) to a freshly drawn order. */
    default Draw decorate(Random random, Draw draw) {
        return draw;
    }

    /** How many items in the player's inventory satisfy {@code order} (group: {@code order} carries no conditions). */
    int count(Player player, Order order);

    /** Takes exactly {@code amount} matching items in delivery order; null (nothing taken) if too few. */
    List<Taken> take(Player player, Order order, boolean group, int amount);

    /** Condition lines for an order's icon (e.g. "금 요리만", "35cm 이상"). */
    void conditionLore(Order order, OrderGui gui, List<Component> lore);

    /** Main thread, player online: an order was completed and the tier grants {@code jobXp}. */
    default void grantJobXp(Player player, long jobXp) {
    }
}
