package com.yeowool.life.metals;

import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
import com.yeowool.core.api.service.MessageService;
import com.yeowool.life.metals.MetalConfig.Conversion;
import com.yeowool.life.metals.MetalConfig.Metal;
import com.yeowool.life.metals.MetalConfig.Recipe;
import com.yeowool.life.metals.MetalConfig.Tier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/** 대장간 window: 제련 / 변환 (metal list → that metal's exchanges) / 제작 tabs. Left-click = once, shift-click = up to 64. */
final class SmithyGui extends YeowoolGui {

    private enum Tab { SMELT, CONVERT, CRAFT }

    private static final int SMELT_TAB = 2;
    private static final int CONVERT_TAB = 4;
    private static final int CRAFT_TAB = 6;
    private static final int BACK_SLOT = 45;
    private static final int CLOSE_SLOT = 49;
    private static final int FIRST_CONTENT = 9;
    private static final int LAST_CONTENT = 44;
    private static final int BULK = 64;

    private final MetalService service;
    private final MessageService messages;
    private final Player viewer;
    private Tab tab = Tab.SMELT;
    private Metal selected;
    private long lastClickAt;

    SmithyGui(MetalService service, Player viewer) {
        super(54, service.messages().resolveRaw("metals.gui.title"));
        this.service = service;
        this.messages = service.messages();
        this.viewer = viewer;
        render();
    }

    private void render() {
        for (int slot = 0; slot < 54; slot++) {
            clearButton(slot);
        }
        MetalConfig config = service.config();
        tabButton(SMELT_TAB, Tab.SMELT, Material.BLAST_FURNACE, "metals.gui.tab-smelt");
        tabButton(CONVERT_TAB, Tab.CONVERT, Material.STONECUTTER, "metals.gui.tab-convert");
        tabButton(CRAFT_TAB, Tab.CRAFT, Material.ANVIL, "metals.gui.tab-craft");
        switch (tab) {
            case SMELT -> renderMetals(config, this::smeltIcon, (metal, event) -> service.smelt(viewer, metal, event.isShiftClick() ? BULK : 1));
            case CONVERT -> {
                if (selected == null) {
                    renderMetals(config, this::convertListIcon, (metal, event) -> {
                        selected = metal;
                        render();
                    });
                } else {
                    renderConversions(config);
                }
            }
            case CRAFT -> renderRecipes(config);
        }
        setButton(CLOSE_SLOT, GuiButton.of(icon(new ItemStack(Material.BARRIER), line("metals.gui.close"), List.of()),
                event -> event.getWhoClicked().closeInventory()));
    }

    private void tabButton(int slot, Tab target, Material material, String key) {
        ItemStack icon = icon(new ItemStack(material), line(key), List.of());
        if (tab == target) {
            glint(icon);
        }
        setButton(slot, GuiButton.of(icon, event -> {
            tab = target;
            selected = null;
            render();
        }));
    }

    private void renderMetals(MetalConfig config, Function<Metal, ItemStack> iconFor, BiConsumer<Metal, InventoryClickEvent> action) {
        int slot = FIRST_CONTENT;
        for (Metal metal : config.metals().values()) {
            if (slot > LAST_CONTENT) {
                break; // ponytail: one page (36) — the pack has 28 metals; paginate if metals.yml ever grows past it
            }
            setButton(slot++, GuiButton.of(iconFor.apply(metal), guarded(event -> action.accept(metal, event))));
        }
    }

    private ItemStack smeltIcon(Metal metal) {
        MetalConfig config = service.config();
        List<Component> lore = new ArrayList<>();
        lore.add(line("metals.gui.tier", service.tierTag(metal.tier())));
        lore.add(line("metals.gui.smelt-cost", Placeholder.unparsed("amount", String.valueOf(config.rawPerIngot())),
                Placeholder.unparsed("cost", MetalService.money(config.tier(metal.tier()).smeltCost()))));
        lore.add(line("metals.gui.owned-raw", Placeholder.unparsed("amount",
                String.valueOf(MetalItems.count(viewer, MetalService.item(metal, MetalConfig.RAW))))));
        lore.add(Component.empty());
        lore.add(line("metals.gui.click-bulk"));
        return packIcon(metal, MetalConfig.INGOT, service.name(metal, "metals.form.ingot"), lore);
    }

    private ItemStack convertListIcon(Metal metal) {
        List<Component> lore = new ArrayList<>();
        lore.add(line("metals.gui.tier", service.tierTag(metal.tier())));
        lore.add(line("metals.gui.owned-ingot", Placeholder.unparsed("amount",
                String.valueOf(MetalItems.count(viewer, MetalService.item(metal, MetalConfig.INGOT))))));
        lore.add(Component.empty());
        lore.add(line("metals.gui.click-convert"));
        return packIcon(metal, MetalConfig.INGOT, service.name(metal, "metals.form.ingot"), lore);
    }

    private void renderConversions(MetalConfig config) {
        List<Conversion> conversions = config.conversions();
        int shown = Math.min(conversions.size(), 5);
        int start = 4 - (shown - 1);
        Component ingotName = service.name(selected, "metals.form.ingot");
        int ingotsOwned = MetalItems.count(viewer, MetalService.item(selected, MetalConfig.INGOT));
        for (int i = 0; i < shown; i++) {
            Conversion conversion = conversions.get(i);
            Component targetName = Component.text(selected.name() + " " + conversion.name()).color(MetalService.color(config, selected.tier()));
            int targetOwned = MetalItems.count(viewer, MetalService.item(selected, conversion.suffix()));
            TagResolver[] tags = {Placeholder.component("ingot", ingotName), Placeholder.component("target", targetName),
                    Placeholder.unparsed("ingots", String.valueOf(conversion.ingots()))};
            setButton(18 + start + i * 2, GuiButton.of(packIcon(selected, conversion.suffix(), line("metals.gui.to-target", tags),
                    List.of(line("metals.gui.owned-ingot", Placeholder.unparsed("amount", String.valueOf(ingotsOwned))), line("metals.gui.click-bulk"))),
                    guarded(event -> service.convert(viewer, selected, conversion, false, event.isShiftClick() ? BULK : 1))));
            setButton(27 + start + i * 2, GuiButton.of(packIcon(selected, MetalConfig.INGOT, line("metals.gui.to-ingot", tags),
                    List.of(line("metals.gui.owned-target", Placeholder.unparsed("amount", String.valueOf(targetOwned))), line("metals.gui.click-bulk"))),
                    guarded(event -> service.convert(viewer, selected, conversion, true, event.isShiftClick() ? BULK : 1))));
        }
        setButton(BACK_SLOT, GuiButton.of(icon(new ItemStack(Material.ARROW), line("metals.gui.back"), List.of()), event -> {
            selected = null;
            render();
        }));
    }

    private void renderRecipes(MetalConfig config) {
        List<Recipe> recipes = config.recipes();
        int shown = Math.min(recipes.size(), 5);
        int start = 4 - (shown - 1);
        for (int i = 0; i < shown; i++) {
            Recipe recipe = recipes.get(i);
            ItemStack stack = MetalItems.aid(recipe, messages, 1);
            ItemMeta meta = stack.getItemMeta();
            List<Component> lore = new ArrayList<>(meta.lore() == null ? List.of() : meta.lore());
            lore.add(Component.empty());
            lore.add(line("metals.gui.recipe-header"));
            for (Map.Entry<Tier, Integer> need : recipe.ingots().entrySet()) {
                lore.add(line("metals.gui.recipe-ingots", service.tierTag(need.getKey()),
                        Placeholder.unparsed("amount", String.valueOf(need.getValue())),
                        Placeholder.unparsed("owned", String.valueOf(MetalItems.count(viewer, service.ingotsOf(need.getKey()))))));
            }
            if (recipe.money() > 0) {
                lore.add(line("metals.gui.recipe-money", Placeholder.unparsed("cost", MetalService.money(recipe.money()))));
            }
            lore.add(Component.empty());
            lore.add(line("metals.gui.click-craft"));
            meta.lore(lore);
            stack.setItemMeta(meta);
            setButton(18 + start + i * 2, GuiButton.of(stack, guarded(event -> service.craft(viewer, recipe))));
        }
    }

    /** Drops double-click echoes and clicks within 250 ms of the last one (one intent = one paid action), then re-renders. */
    private Consumer<InventoryClickEvent> guarded(Consumer<InventoryClickEvent> action) {
        return event -> {
            long now = System.currentTimeMillis();
            if (event.getClick() == ClickType.DOUBLE_CLICK || now - lastClickAt < 250) {
                return;
            }
            lastClickAt = now;
            action.accept(event);
            if (viewer.getOpenInventory().getTopInventory().getHolder() == this) {
                render();
            }
        };
    }

    /** The pack item as the icon (barrier if ItemsAdder doesn't know it), renamed with our own name/lore. */
    private static ItemStack packIcon(Metal metal, int suffix, Component name, List<Component> lore) {
        ItemStack stack = MetalItems.create(metal.id(), suffix, 1);
        return icon(stack == null ? new ItemStack(Material.BARRIER) : stack,
                name.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE), lore);
    }

    private Component line(String key, TagResolver... placeholders) {
        return messages.resolveRaw(key, placeholders).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    private static ItemStack icon(ItemStack stack, Component name, List<Component> lore) {
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(name);
            meta.lore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static void glint(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setEnchantmentGlintOverride(true);
            stack.setItemMeta(meta);
        }
    }
}
