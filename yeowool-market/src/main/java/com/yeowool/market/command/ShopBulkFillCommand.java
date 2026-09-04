package com.yeowool.market.command;

import com.yeowool.core.api.model.CurrencyType;
import com.yeowool.market.adminshop.AdminShopStore;
import com.yeowool.market.npcshop.ShopLayout;
import com.yeowool.market.npcshop.ShopPricedItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code /상점자동채우기 [초기화]} — one-shot admin utility that stocks the
 * still-empty 건축(building)/목축(livestock)/강화재료(enhance)/잡화(misc) admin
 * shops with a curated vanilla item set and default prices, mirroring what an
 * operator would otherwise place by hand through {@code /상점아이템설정} +
 * {@code /상점아이템가격}. Skips any page that already has items, so re-running
 * it is harmless. {@code 초기화} reverses it — wipes every page this command
 * ever filled back down to one empty page each, exactly as {@code /상점생성}
 * originally left them.
 */
public final class ShopBulkFillCommand implements CommandExecutor {

    private record Entry(Material material, long buy, long sell) {
    }

    private static final List<String> SHOP_IDS = List.of("building", "livestock", "enhance", "misc");

    private final JavaPlugin plugin;
    private final AdminShopStore store;

    public ShopBulkFillCommand(JavaPlugin plugin, AdminShopStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equals("초기화")) {
            for (String id : SHOP_IDS) {
                clear(id, sender);
            }
            sender.sendMessage(Component.text("자동 채우기로 넣은 아이템을 모두 비웠습니다.", NamedTextColor.GREEN));
            return true;
        }
        fill("building", BUILDING_PAGES, sender);
        fill("livestock", LIVESTOCK_PAGES, sender);
        fill("enhance", ENHANCE_PAGES, sender);
        fill("misc", MISC_PAGES, sender);
        sender.sendMessage(Component.text("상점 일괄 채우기를 완료했습니다.", NamedTextColor.GREEN));
        return true;
    }

    /** Removes every page above the first, then empties page 0 — back to exactly what {@code /상점생성} leaves. */
    private void clear(String shopId, CommandSender sender) {
        if (!store.isAdminShop(shopId)) {
            sender.sendMessage(Component.text("[" + shopId + "] 존재하지 않는 상점이라 건너뜁니다.", NamedTextColor.RED));
            return;
        }
        while (store.pageCount(shopId) > 1) {
            store.removePage(shopId);
        }
        store.savePage(shopId, 0, Map.of());
        sender.sendMessage(Component.text("[" + shopId + "] 비웠습니다.", NamedTextColor.GREEN));
    }

    /** Skips any page that already has items, so a retry after a transient DB error only redoes what's missing. */
    private void fill(String shopId, List<List<Entry>> pages, CommandSender sender) {
        if (!store.isAdminShop(shopId)) {
            sender.sendMessage(Component.text("[" + shopId + "] 존재하지 않는 상점이라 건너뜁니다.", NamedTextColor.RED));
            return;
        }
        List<Integer> slots = ShopLayout.USABLE_SLOTS;
        int filled = 0;
        for (int page = 0; page < pages.size(); page++) {
            if (!store.rawItemsOf(shopId, page).isEmpty()) {
                continue;
            }
            while (store.pageCount(shopId) <= page) {
                store.addPage(shopId);
            }
            Map<Integer, ItemStack> items = new LinkedHashMap<>();
            List<Entry> entries = pages.get(page);
            for (int i = 0; i < entries.size() && i < slots.size(); i++) {
                Entry entry = entries.get(i);
                ItemStack stack = new ItemStack(entry.material());
                ShopPricedItem.stamp(plugin, stack, entry.buy(), entry.sell(), CurrencyType.ON, false);
                items.put(slots.get(i), stack);
            }
            store.savePage(shopId, page, items);
            filled++;
        }
        if (filled == 0) {
            sender.sendMessage(Component.text("[" + shopId + "] 이미 모든 페이지가 채워져 있습니다.", NamedTextColor.YELLOW));
        } else {
            sender.sendMessage(Component.text("[" + shopId + "] " + filled + "페이지를 채웠습니다.", NamedTextColor.GREEN));
        }
    }

    // 가격 등급: 흔한 재료 -> 희귀/최상급 순.
    private static Entry common(Material m) {
        return new Entry(m, 8, 3);
    }

    private static Entry uncommon(Material m) {
        return new Entry(m, 25, 10);
    }

    private static Entry rare(Material m) {
        return new Entry(m, 120, 50);
    }

    private static Entry epic(Material m) {
        return new Entry(m, 600, 250);
    }

    private static Entry legendary(Material m) {
        return new Entry(m, 3000, 1200);
    }

    private static Entry mythic(Material m) {
        return new Entry(m, 12000, 5000);
    }

    private static final List<List<Entry>> BUILDING_PAGES = List.of(
            List.of(
                    common(Material.TORCH), uncommon(Material.SOUL_TORCH), uncommon(Material.LANTERN),
                    uncommon(Material.SOUL_LANTERN), rare(Material.SEA_LANTERN), uncommon(Material.GLOWSTONE),
                    rare(Material.SHROOMLIGHT), rare(Material.END_ROD), uncommon(Material.REDSTONE_LAMP),
                    common(Material.CANDLE), common(Material.OAK_DOOR), common(Material.SPRUCE_DOOR),
                    uncommon(Material.IRON_DOOR), common(Material.OAK_TRAPDOOR), uncommon(Material.IRON_TRAPDOOR),
                    common(Material.OAK_FENCE_GATE), common(Material.IRON_BARS), common(Material.GLASS_PANE),
                    common(Material.LADDER), common(Material.SCAFFOLDING), uncommon(Material.CHAIN),
                    common(Material.FLOWER_POT), uncommon(Material.ITEM_FRAME), rare(Material.GLOW_ITEM_FRAME),
                    uncommon(Material.PAINTING), uncommon(Material.WHITE_BANNER), uncommon(Material.WHITE_BED),
                    uncommon(Material.RED_BED)
            ),
            List.of(
                    common(Material.WHITE_CARPET), common(Material.ORANGE_CARPET), common(Material.MAGENTA_CARPET),
                    common(Material.LIGHT_BLUE_CARPET), common(Material.YELLOW_CARPET), common(Material.LIME_CARPET),
                    common(Material.PINK_CARPET), common(Material.GRAY_CARPET), common(Material.LIGHT_GRAY_CARPET),
                    common(Material.CYAN_CARPET), common(Material.PURPLE_CARPET), common(Material.BLUE_CARPET),
                    common(Material.BROWN_CARPET), common(Material.GREEN_CARPET), common(Material.RED_CARPET),
                    common(Material.BLACK_CARPET), common(Material.VINE), common(Material.MOSS_CARPET),
                    common(Material.MOSS_BLOCK), common(Material.FERN), uncommon(Material.SUNFLOWER),
                    uncommon(Material.LILAC), uncommon(Material.PEONY), uncommon(Material.ROSE_BUSH),
                    common(Material.LARGE_FERN), common(Material.DEAD_BUSH), common(Material.BAMBOO),
                    uncommon(Material.BOOKSHELF)
            )
    );

    private static final List<List<Entry>> LIVESTOCK_PAGES = List.of(
            List.of(
                    common(Material.WHEAT), common(Material.WHEAT_SEEDS), common(Material.CARROT),
                    common(Material.POTATO), common(Material.BEETROOT), common(Material.BEETROOT_SEEDS),
                    common(Material.MELON_SEEDS), common(Material.PUMPKIN_SEEDS), common(Material.SWEET_BERRIES),
                    uncommon(Material.HAY_BLOCK), rare(Material.SADDLE), uncommon(Material.LEAD),
                    rare(Material.NAME_TAG), uncommon(Material.LEATHER_HORSE_ARMOR), rare(Material.IRON_HORSE_ARMOR),
                    epic(Material.GOLDEN_HORSE_ARMOR), legendary(Material.DIAMOND_HORSE_ARMOR), uncommon(Material.LEATHER),
                    common(Material.FEATHER), common(Material.EGG), uncommon(Material.MILK_BUCKET),
                    common(Material.RABBIT_HIDE), uncommon(Material.RABBIT_FOOT), uncommon(Material.HONEYCOMB),
                    uncommon(Material.HONEY_BOTTLE), uncommon(Material.HONEYCOMB_BLOCK), common(Material.WHITE_WOOL),
                    common(Material.BLACK_WOOL)
            ),
            List.of(
                    common(Material.BEEF), common(Material.PORKCHOP), common(Material.MUTTON),
                    common(Material.CHICKEN), common(Material.RABBIT), common(Material.COD),
                    common(Material.SALMON), uncommon(Material.TROPICAL_FISH), uncommon(Material.PUFFERFISH),
                    common(Material.ORANGE_WOOL), common(Material.MAGENTA_WOOL), common(Material.LIGHT_BLUE_WOOL),
                    common(Material.YELLOW_WOOL), common(Material.LIME_WOOL), common(Material.PINK_WOOL),
                    common(Material.GRAY_WOOL), common(Material.LIGHT_GRAY_WOOL), common(Material.CYAN_WOOL),
                    common(Material.PURPLE_WOOL), common(Material.BLUE_WOOL), common(Material.BROWN_WOOL),
                    common(Material.GREEN_WOOL), common(Material.RED_WOOL), uncommon(Material.GOLDEN_CARROT),
                    rare(Material.GOLDEN_APPLE), common(Material.KELP), common(Material.CACTUS),
                    common(Material.BONE_MEAL)
            )
    );

    private static final List<List<Entry>> ENHANCE_PAGES = List.of(
            List.of(
                    new Entry(Material.DIAMOND, 300, 120), legendary(Material.NETHERITE_SCRAP), mythic(Material.NETHERITE_INGOT),
                    rare(Material.EMERALD), legendary(Material.ANCIENT_DEBRIS), uncommon(Material.OBSIDIAN),
                    rare(Material.CRYING_OBSIDIAN), mythic(Material.NETHER_STAR), epic(Material.ECHO_SHARD),
                    uncommon(Material.AMETHYST_SHARD), uncommon(Material.PRISMARINE_CRYSTALS), common(Material.PRISMARINE_SHARD),
                    uncommon(Material.EXPERIENCE_BOTTLE), mythic(Material.TOTEM_OF_UNDYING), uncommon(Material.PHANTOM_MEMBRANE),
                    rare(Material.GHAST_TEAR), epic(Material.SHULKER_SHELL), rare(Material.TURTLE_SCUTE),
                    rare(Material.NAUTILUS_SHELL), uncommon(Material.BLAZE_ROD), common(Material.BLAZE_POWDER),
                    uncommon(Material.MAGMA_CREAM), common(Material.GLOWSTONE_DUST), common(Material.REDSTONE),
                    common(Material.LAPIS_LAZULI), common(Material.QUARTZ), common(Material.IRON_INGOT),
                    uncommon(Material.GOLD_INGOT)
            ),
            List.of(
                    legendary(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE), rare(Material.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE),
                    rare(Material.VEX_ARMOR_TRIM_SMITHING_TEMPLATE), rare(Material.WILD_ARMOR_TRIM_SMITHING_TEMPLATE),
                    rare(Material.COAST_ARMOR_TRIM_SMITHING_TEMPLATE), rare(Material.DUNE_ARMOR_TRIM_SMITHING_TEMPLATE),
                    rare(Material.WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE), rare(Material.RAISER_ARMOR_TRIM_SMITHING_TEMPLATE),
                    rare(Material.SHAPER_ARMOR_TRIM_SMITHING_TEMPLATE), rare(Material.HOST_ARMOR_TRIM_SMITHING_TEMPLATE),
                    rare(Material.WARD_ARMOR_TRIM_SMITHING_TEMPLATE), rare(Material.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE),
                    rare(Material.TIDE_ARMOR_TRIM_SMITHING_TEMPLATE), rare(Material.SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE),
                    rare(Material.RIB_ARMOR_TRIM_SMITHING_TEMPLATE), rare(Material.EYE_ARMOR_TRIM_SMITHING_TEMPLATE),
                    rare(Material.SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE), rare(Material.FLOW_ARMOR_TRIM_SMITHING_TEMPLATE),
                    rare(Material.BOLT_ARMOR_TRIM_SMITHING_TEMPLATE), uncommon(Material.DRAGON_BREATH),
                    epic(Material.HEART_OF_THE_SEA), legendary(Material.WITHER_SKELETON_SKULL)
            )
    );

    private static final List<List<Entry>> MISC_PAGES = List.of(
            List.of(
                    common(Material.BUCKET), common(Material.WATER_BUCKET), uncommon(Material.LAVA_BUCKET),
                    common(Material.FLINT_AND_STEEL), uncommon(Material.COMPASS), uncommon(Material.CLOCK),
                    common(Material.MAP), rare(Material.SPYGLASS), common(Material.BOOK),
                    common(Material.WRITABLE_BOOK), common(Material.PAPER), common(Material.INK_SAC),
                    uncommon(Material.GLOW_INK_SAC), common(Material.SLIME_BALL), common(Material.BONE),
                    common(Material.GUNPOWDER), uncommon(Material.TNT), common(Material.FIREWORK_ROCKET),
                    common(Material.ARROW), uncommon(Material.SPECTRAL_ARROW), common(Material.CHEST),
                    common(Material.BARREL), epic(Material.SHULKER_BOX), rare(Material.ENDER_CHEST),
                    common(Material.SNOWBALL), common(Material.FIRE_CHARGE), uncommon(Material.BUNDLE),
                    uncommon(Material.BRUSH)
            )
    );
}
