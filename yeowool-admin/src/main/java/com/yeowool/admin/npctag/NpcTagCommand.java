package com.yeowool.admin.npctag;

import com.yeowool.core.api.service.MessageService;
import com.yeowool.core.util.TabCompletions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * {@code /npc태그 설정 <타입> <색상>} / {@code /npc태그 제거} — targets whatever
 * entity the sender is looking at and edits its vanilla custom name directly
 * (works on any plain entity). {@code /npc태그 모델생성}, separately, wraps
 * HQModeledNPC's own (undocumented — not in its plugin.yml, only found by
 * decompiling; base label {@code modelednpc}, {@code create} subcommand)
 * command instead, since ModelEngine-driven NPCs likely render their name
 * from their own stored metadata rather than the live entity's vanilla
 * custom name, and HQModeledNPC has no "rename an existing NPC" command at
 * all — so an existing tagless ModeledNPC can only get a tag by being
 * deleted and recreated with the tag baked into its name from the start.
 */
public final class NpcTagCommand implements CommandExecutor, TabCompleter {

    private static final double MAX_DISTANCE = 6.0;

    private final MessageService messages;
    private final NamespacedKey baseNameKey;
    private final NamespacedKey hasBaseNameKey;

    public NpcTagCommand(JavaPlugin plugin, MessageService messages) {
        this.messages = messages;
        this.baseNameKey = new NamespacedKey(plugin, "npctag_base_name");
        this.hasBaseNameKey = new NamespacedKey(plugin, "npctag_has_base_name");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return true;
        }
        if (args.length == 0) {
            messages.send(player, "npctag.usage");
            return true;
        }
        switch (args[0]) {
            case "설정" -> set(player, args);
            case "제거" -> remove(player);
            case "모델생성" -> createModeled(player, args);
            default -> messages.send(player, "npctag.usage");
        }
        return true;
    }

    /** Prefixes the tag glyph directly onto the name (no separating space — {@code modelednpc create} likely takes name as a single bare token, not a quoted string) and dispatches the real command. */
    private void createModeled(Player player, String[] args) {
        if (args.length < 5) {
            messages.send(player, "npctag.create-usage");
            return;
        }
        String type = args[1];
        String color = args[2];
        String name = args[3];
        String modelId = args[4];
        if (!NpcTagIcons.TYPES.contains(type) || !NpcTagIcons.COLORS.contains(color)) {
            messages.send(player, "npctag.invalid-tag");
            return;
        }
        String glyphChar = NpcTagIcons.glyphChar(type, color);
        if (glyphChar == null) {
            messages.send(player, "npctag.pack-unavailable");
            return;
        }
        StringBuilder dispatched = new StringBuilder("modelednpc create ")
                .append(glyphChar).append(name).append(' ').append(modelId);
        for (int i = 5; i < args.length; i++) {
            dispatched.append(' ').append(args[i]);
        }
        messages.send(player, "npctag.create-dispatched", Placeholder.unparsed("name", name));
        player.performCommand(dispatched.toString());
    }

    private void set(Player player, String[] args) {
        if (args.length != 3) {
            messages.send(player, "npctag.set-usage");
            return;
        }
        String type = args[1];
        String color = args[2];
        if (!NpcTagIcons.TYPES.contains(type) || !NpcTagIcons.COLORS.contains(color)) {
            messages.send(player, "npctag.invalid-tag");
            return;
        }
        Component glyph = NpcTagIcons.glyph(type, color);
        if (glyph == null) {
            messages.send(player, "npctag.pack-unavailable");
            return;
        }
        Entity target = player.getTargetEntity((int) MAX_DISTANCE, false);
        if (target == null) {
            messages.send(player, "npctag.no-target");
            return;
        }

        var pdc = target.getPersistentDataContainer();
        if (!pdc.has(hasBaseNameKey, PersistentDataType.BYTE)) {
            String existing = target.customName() == null ? "" : plainOf(target.customName());
            pdc.set(baseNameKey, PersistentDataType.STRING, existing);
            pdc.set(hasBaseNameKey, PersistentDataType.BYTE, (byte) 1);
        }
        String baseName = pdc.getOrDefault(baseNameKey, PersistentDataType.STRING, "");

        Component newName = baseName.isBlank() ? glyph : glyph.append(Component.text(" " + baseName));
        target.customName(newName);
        target.setCustomNameVisible(true);
        messages.send(player, "npctag.set-success", Placeholder.unparsed("type", type), Placeholder.unparsed("color", color));
    }

    private void remove(Player player) {
        Entity target = player.getTargetEntity((int) MAX_DISTANCE, false);
        if (target == null) {
            messages.send(player, "npctag.no-target");
            return;
        }
        var pdc = target.getPersistentDataContainer();
        if (!pdc.has(hasBaseNameKey, PersistentDataType.BYTE)) {
            messages.send(player, "npctag.not-tagged");
            return;
        }
        String baseName = pdc.getOrDefault(baseNameKey, PersistentDataType.STRING, "");
        if (baseName.isBlank()) {
            target.customName(null);
        } else {
            target.customName(Component.text(baseName));
        }
        pdc.remove(baseNameKey);
        pdc.remove(hasBaseNameKey);
        messages.send(player, "npctag.remove-success");
    }

    private String plainOf(Component component) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(component);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return TabCompletions.filter(List.of("설정", "제거", "모델생성"), args[0]);
        }
        if (args.length == 2 && (args[0].equals("설정") || args[0].equals("모델생성"))) {
            return TabCompletions.filter(NpcTagIcons.TYPES, args[1]);
        }
        if (args.length == 3 && (args[0].equals("설정") || args[0].equals("모델생성"))) {
            return TabCompletions.filter(NpcTagIcons.COLORS, args[2]);
        }
        return List.of();
    }
}
