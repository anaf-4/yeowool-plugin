# 탈것 이용권 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tradeable mount vouchers that permanently unlock an MCPets mount (via its LuckPerms permission), plus `/탈것` listing owned mounts and opening the MCPets menu.

**Architecture:** New package `com.yeowool.life.mount` in `yeowool-life`. `MountCatalog` (pure, tested) reads every `plugins/MCPets/Pets/*.yml` with `Mountable: true`. A voucher item carries the mount id in PDC; right-clicking it grants the permission with the console command `lp user <name> permission set <perm> true` (LuckPerms uses shared MySQL + messaging, so all three servers see it) and consumes one voucher.

**Tech Stack:** Paper 1.21.4, Java 21, LuckPerms (console command), MCPets (config files + `/mcpets` menu), JUnit 5.

**Spec:** `docs/superpowers/specs/2026-09-28-dex-competition-worldboss-mounts-design.md` (section 4)

## Global Constraints

- Build from `C:/Users/apple/OneDrive/Desktop/YEOWOOL_PLUGIN`: `./gradlew :yeowool-life:build`. OneDrive lock → `rm -rf yeowool-*/build/test-results/test/binary` and rerun.
- Bukkit API on the main thread. Player-visible text Korean via `MessageService` keys in `yeowool-life/src/main/resources/messages.yml` (MiniMessage, placeholders with `_`); user/config values via `Placeholder.unparsed`.
- Commits: stage only the task's files (never `git add -A`/`.`, never `homepage-plan.md`), never `--amend`, message ends with a blank line then exactly `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Do not dispatch subagents.

---

### Task 1: MountCatalog + tests

**Files:**
- Create: `yeowool-life/src/main/java/com/yeowool/life/mount/MountDefinition.java`
- Create: `yeowool-life/src/main/java/com/yeowool/life/mount/MountCatalog.java`
- Test: `yeowool-life/src/test/java/com/yeowool/life/mount/MountCatalogTest.java`

**Interfaces:**
- Produces: `record MountDefinition(String id, String permission, String displayName, Material icon, int customModelData)`; `MountCatalog.parse(YamlConfiguration) -> Optional<MountDefinition>`, `MountCatalog.load(File petsDir) -> Map<String, MountDefinition>` (insertion order by file name; missing dir → empty), `MountCatalog.stripColors(String) -> String`.

- [ ] **Step 1: Write the failing test**

```java
package com.yeowool.life.mount;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MountCatalogTest {

    private static YamlConfiguration yaml(String text) {
        return YamlConfiguration.loadConfiguration(new StringReader(text));
    }

    @Test
    void parsesMountablePet() {
        Optional<MountDefinition> mount = MountCatalog.parse(yaml("""
                Id: AmethystDragon
                Permission: mcpets.amethystdragonpet
                Mountable: true
                Icon:
                  Name: §5Amethyst Dragon
                  Material: AMETHYST_SHARD
                  CustomModelData: 2000006
                """));
        assertEquals(Optional.of(new MountDefinition("AmethystDragon", "mcpets.amethystdragonpet", "Amethyst Dragon",
                Material.AMETHYST_SHARD, 2000006)), mount);
    }

    @Test
    void skipsNonMountableOrIncompletePets() {
        assertTrue(MountCatalog.parse(yaml("Id: Cat\nPermission: mcpets.cat\nMountable: false\n")).isEmpty());
        assertTrue(MountCatalog.parse(yaml("Id: Cat\nPermission: mcpets.cat\n")).isEmpty());
        assertTrue(MountCatalog.parse(yaml("Permission: mcpets.x\nMountable: true\n")).isEmpty());
        assertTrue(MountCatalog.parse(yaml("Id: X\nMountable: true\n")).isEmpty());
    }

    @Test
    void fallsBackForMissingIconFields() {
        MountDefinition mount = MountCatalog.parse(yaml("Id: Bike\nPermission: mcpets.bike\nMountable: true\nIcon:\n  Material: NOT_A_MATERIAL\n")).orElseThrow();
        assertEquals("Bike", mount.displayName());
        assertEquals(Material.SADDLE, mount.icon());
        assertEquals(0, mount.customModelData());
    }

    @Test
    void stripsLegacyColorCodes() {
        assertEquals("Hover-Ride Angle Red", MountCatalog.stripColors("§6§nHover-Ride§r§b Angle Red"));
        assertEquals("Plain", MountCatalog.stripColors("&aPlain"));
    }
}
```

- [ ] **Step 2: Run** `./gradlew :yeowool-life:test --tests "com.yeowool.life.mount.MountCatalogTest"` → FAIL (classes missing).

- [ ] **Step 3: Create `MountDefinition.java`**

```java
package com.yeowool.life.mount;

import org.bukkit.Material;

/** One MCPets pet with {@code Mountable: true}; {@code customModelData} 0 means none. */
public record MountDefinition(String id, String permission, String displayName, Material icon, int customModelData) {
}
```

- [ ] **Step 4: Create `MountCatalog.java`**

```java
package com.yeowool.life.mount;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Reads MCPets' own pet files so the mount list always matches what MCPets actually has installed. */
public final class MountCatalog {

    private MountCatalog() {
    }

    public static Map<String, MountDefinition> load(File petsDir) {
        Map<String, MountDefinition> mounts = new LinkedHashMap<>();
        File[] files = petsDir.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return mounts;
        }
        Arrays.sort(files, Comparator.comparing(File::getName));
        for (File file : files) {
            parse(YamlConfiguration.loadConfiguration(file)).ifPresent(mount -> mounts.put(mount.id(), mount));
        }
        return mounts;
    }

    /** Empty unless the pet is {@code Mountable: true} and has both {@code Id} and {@code Permission}. */
    public static Optional<MountDefinition> parse(YamlConfiguration yaml) {
        String id = yaml.getString("Id");
        String permission = yaml.getString("Permission");
        if (!yaml.getBoolean("Mountable", false) || id == null || id.isBlank() || permission == null || permission.isBlank()) {
            return Optional.empty();
        }
        String name = stripColors(yaml.getString("Icon.Name", id)).trim();
        Material icon = Material.matchMaterial(yaml.getString("Icon.Material", "SADDLE"));
        return Optional.of(new MountDefinition(id, permission, name.isEmpty() ? id : name,
                icon == null || !icon.isItem() ? Material.SADDLE : icon, yaml.getInt("Icon.CustomModelData", 0)));
    }

    public static String stripColors(String text) {
        return text.replaceAll("(?i)[§&][0-9A-FK-ORX]", "");
    }
}
```

- [ ] **Step 5: Run** the same test → PASS (4 tests). If `Material.matchMaterial`/`isItem` fail in the unit test environment (no server), replace `icon.isItem()` with a plain null check and note it as a deviation.

- [ ] **Step 6: Commit**

```bash
git add yeowool-life/src/main/java/com/yeowool/life/mount/MountDefinition.java yeowool-life/src/main/java/com/yeowool/life/mount/MountCatalog.java yeowool-life/src/test/java/com/yeowool/life/mount/MountCatalogTest.java
git commit -m "Add MCPets mount catalog

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Vouchers, `/탈것`, `/탈것이용권`, wiring

**Files:**
- Create: `yeowool-life/src/main/java/com/yeowool/life/mount/MountVoucherItem.java`
- Create: `yeowool-life/src/main/java/com/yeowool/life/mount/MountVoucherListener.java`
- Create: `yeowool-life/src/main/java/com/yeowool/life/mount/MountListGui.java`
- Create: `yeowool-life/src/main/java/com/yeowool/life/mount/MountCommand.java`
- Modify: `yeowool-life/src/main/java/com/yeowool/life/YeowoolLife.java`
- Modify: `yeowool-life/src/main/resources/messages.yml`, `plugin.yml`
- Modify: `yeowool-core/src/main/resources/help.yml`

**Interfaces:**
- Consumes: Task 1; `core.mailbox().deliverOrStore(UUID, ItemStack, String, String)` (main thread); `YeowoolGui(int, Component)`, `setButton`, `GuiButton.of`.
- Produces: `MountVoucherItem(JavaPlugin)`: `create(MountDefinition) -> ItemStack`, `read(ItemStack) -> Optional<String>`; `MountVoucherListener(JavaPlugin, MessageService, MountVoucherItem, Map<String, MountDefinition>)`; `MountCommand(YeowoolCoreAPI, MessageService, MountVoucherItem, Map<String, MountDefinition>)` handling both `/탈것` and `/탈것이용권` by command name.

- [ ] **Step 1: Create `MountVoucherItem.java`**

```java
package com.yeowool.life.mount;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Optional;

/** A tradeable voucher; the mount id lives in PDC so renaming the item can't change what it unlocks. */
public final class MountVoucherItem {

    private final NamespacedKey key;

    public MountVoucherItem(JavaPlugin plugin) {
        this.key = new NamespacedKey(plugin, "mount_voucher");
    }

    public ItemStack create(MountDefinition mount) {
        ItemStack stack = new ItemStack(mount.icon());
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(mount.displayName() + " 탈것 이용권", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("우클릭하면 이 탈것을 영구히 해금합니다", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("해금 후 /탈것 으로 소환", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)));
        if (mount.customModelData() != 0) {
            meta.setCustomModelData(mount.customModelData());
        }
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, mount.id());
        stack.setItemMeta(meta);
        return stack;
    }

    public Optional<String> read(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return Optional.empty();
        }
        return Optional.ofNullable(stack.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING));
    }
}
```

- [ ] **Step 2: Create `MountVoucherListener.java`**

```java
package com.yeowool.life.mount;

import com.yeowool.core.api.service.MessageService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Right-clicking a voucher grants the mount's MCPets permission permanently through LuckPerms
 * and consumes one voucher. LuckPerms applies the change a moment later, so a short in-memory
 * guard stops a fast double-click from spending a second voucher.
 */
public final class MountVoucherListener implements Listener {

    private final JavaPlugin plugin;
    private final MessageService messages;
    private final MountVoucherItem voucherItem;
    private final Map<String, MountDefinition> mounts;
    private final Set<String> granting = ConcurrentHashMap.newKeySet();

    public MountVoucherListener(JavaPlugin plugin, MessageService messages, MountVoucherItem voucherItem, Map<String, MountDefinition> mounts) {
        this.plugin = plugin;
        this.messages = messages;
        this.voucherItem = voucherItem;
        this.mounts = mounts;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        var mountId = voucherItem.read(event.getItem());
        if (mountId.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        MountDefinition mount = mounts.get(mountId.get());
        if (mount == null) {
            messages.send(player, "mount.unknown", Placeholder.unparsed("id", mountId.get()));
            return;
        }
        String guardKey = player.getUniqueId() + ":" + mount.id();
        if (player.hasPermission(mount.permission()) || granting.contains(guardKey)) {
            messages.send(player, "mount.already-owned", Placeholder.unparsed("mount", mount.displayName()));
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        player.getInventory().setItemInMainHand(hand.getAmount() > 1 ? hand.asQuantity(hand.getAmount() - 1) : null);
        granting.add(guardKey);
        Bukkit.getScheduler().runTaskLater(plugin, () -> granting.remove(guardKey), 20L * 10);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                "lp user " + player.getName() + " permission set " + mount.permission() + " true");
        plugin.getLogger().info("탈것 이용권 사용: " + player.getName() + " → " + mount.id() + " (" + mount.permission() + ")");
        messages.send(player, "mount.unlocked", Placeholder.unparsed("mount", mount.displayName()));
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
    }
}
```

- [ ] **Step 3: Create `MountListGui.java`**

```java
package com.yeowool.life.mount;

import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/** {@code /탈것}: the viewer's unlocked mounts; clicking one opens MCPets' own menu to summon and ride. */
public final class MountListGui extends YeowoolGui {

    public static final int SIZE = 27;

    public MountListGui(List<MountDefinition> owned) {
        super(SIZE, Component.text("내 탈것", NamedTextColor.DARK_GREEN));
        for (int i = 0; i < owned.size() && i < SIZE; i++) {
            setButton(i, GuiButton.of(icon(owned.get(i)), event -> {
                Player player = (Player) event.getWhoClicked();
                player.closeInventory();
                player.performCommand("mcpets");
            }));
        }
    }

    private static ItemStack icon(MountDefinition mount) {
        ItemStack stack = new ItemStack(mount.icon());
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(mount.displayName(), NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("클릭: 펫 메뉴에서 소환·탑승", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        if (mount.customModelData() != 0) {
            meta.setCustomModelData(mount.customModelData());
        }
        stack.setItemMeta(meta);
        return stack;
    }
}
```

- [ ] **Step 4: Create `MountCommand.java`**

```java
package com.yeowool.life.mount;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

/** {@code /탈것} (owned mounts GUI) and {@code /탈것이용권 <펫ID> [수량] [닉네임]} (staff: issue vouchers). */
public final class MountCommand implements CommandExecutor, TabCompleter {

    private static final String ADMIN = "yeowool.life.mount.manage";
    private static final int MAX_AMOUNT = 64;

    private final YeowoolCoreAPI core;
    private final MessageService messages;
    private final MountVoucherItem voucherItem;
    private final Map<String, MountDefinition> mounts;

    public MountCommand(YeowoolCoreAPI core, MessageService messages, MountVoucherItem voucherItem, Map<String, MountDefinition> mounts) {
        this.core = core;
        this.messages = messages;
        this.voucherItem = voucherItem;
        this.mounts = mounts;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equals("탈것이용권")) {
            issue(sender, args);
        } else {
            list(sender);
        }
        return true;
    }

    private void list(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return;
        }
        List<MountDefinition> owned = mounts.values().stream().filter(m -> player.hasPermission(m.permission())).toList();
        if (owned.isEmpty()) {
            messages.send(player, "mount.none-owned");
            return;
        }
        new MountListGui(owned).open(player);
    }

    private void issue(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ADMIN)) {
            messages.send(sender, "general.no-permission");
            return;
        }
        if (args.length < 1) {
            messages.send(sender, "mount.issue-usage");
            return;
        }
        MountDefinition mount = mounts.get(args[0]);
        if (mount == null) {
            messages.send(sender, "mount.unknown", Placeholder.unparsed("id", args[0]));
            return;
        }
        int amount = 1;
        if (args.length >= 2) {
            try {
                amount = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                amount = -1;
            }
            if (amount < 1 || amount > MAX_AMOUNT) {
                messages.send(sender, "mount.invalid-amount", Placeholder.unparsed("max", String.valueOf(MAX_AMOUNT)));
                return;
            }
        }
        Player target;
        if (args.length >= 3) {
            target = Bukkit.getPlayerExact(args[2]);
        } else {
            target = sender instanceof Player self ? self : null;
        }
        if (target == null) {
            messages.send(sender, "mount.target-offline");
            return;
        }
        for (int i = 0; i < amount; i++) {
            core.mailbox().deliverOrStore(target.getUniqueId(), voucherItem.create(mount), "YeowoolLife", "탈것 이용권");
        }
        messages.send(sender, "mount.issued",
                Placeholder.unparsed("player", target.getName()),
                Placeholder.unparsed("mount", mount.displayName()),
                Placeholder.unparsed("amount", String.valueOf(amount)));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equals("탈것이용권") || !sender.hasPermission(ADMIN)) {
            return List.of();
        }
        if (args.length == 1) {
            return mounts.keySet().stream().filter(id -> id.toLowerCase().startsWith(args[0].toLowerCase())).toList();
        }
        if (args.length == 3) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(n -> n.startsWith(args[2])).toList();
        }
        return List.of();
    }
}
```

(Vouchers are delivered one per stack because each is an individual item; `deliverOrStore` handles full inventories via the mailbox.)

- [ ] **Step 5: Wire into `YeowoolLife.java`**

Add imports `com.yeowool.life.mount.MountCatalog`, `MountCommand`, `MountDefinition`, `MountVoucherItem`, `MountVoucherListener` (and `java.io.File` if missing). Call `enableMounts(core, messages);` right after `enableLifeCompetition(core, messages);` and add:

```java
    /** 탈것 이용권 — MCPets의 탈것(Mountable: true) 권한을 이용권 아이템으로 영구 해금, /탈것 으로 보유 목록. */
    private void enableMounts(YeowoolCoreAPI core, MessageManager messages) {
        var mcpets = getServer().getPluginManager().getPlugin("MCPets");
        if (mcpets == null || !mcpets.isEnabled()) {
            getLogger().warning("MCPets가 없어 탈것 이용권을 끕니다.");
            return;
        }
        Map<String, MountDefinition> mounts = MountCatalog.load(new File(mcpets.getDataFolder(), "Pets"));
        getLogger().info("탈것 " + mounts.size() + "종을 불러왔습니다: " + mounts.keySet());
        MountVoucherItem voucherItem = new MountVoucherItem(this);
        getServer().getPluginManager().registerEvents(new MountVoucherListener(this, messages, voucherItem, mounts), this);
        MountCommand mountCommand = new MountCommand(core, messages, voucherItem, mounts);
        for (String name : List.of("탈것", "탈것이용권")) {
            var command = getCommand(name);
            if (command != null) {
                command.setExecutor(mountCommand);
                command.setTabCompleter(mountCommand);
            }
        }
    }
```

- [ ] **Step 6: `messages.yml`** — append:

```yaml

mount:
  unlocked: "<gold>[탈것]</gold> <yellow><mount>을(를) 해금했습니다! /탈것 으로 소환해 보세요.</yellow>"
  already-owned: "<red>이미 보유한 탈것입니다: <mount></red>"
  unknown: "<red>알 수 없는 탈것입니다: <id></red>"
  none-owned: "<gray>보유한 탈것이 없습니다. 탈것 이용권을 우클릭하면 해금됩니다.</gray>"
  issue-usage: "<gray>/탈것이용권 [펫ID] [수량] [닉네임]</gray>"
  invalid-amount: "<red>수량은 1~<max> 사이로 입력하세요.</red>"
  target-offline: "<red>이 서버에 접속 중인 플레이어가 아닙니다.</red>"
  issued: "<green><player>님에게 <mount> 탈것 이용권 <amount>장을 지급했습니다.</green>"
```

- [ ] **Step 7: `plugin.yml`** — add under `commands:` (after the last command):

```yaml
  탈것:
    description: 보유한 탈것 목록을 열고 소환 메뉴로 이동합니다
  탈것이용권:
    description: 관리진 전용 - 탈것 이용권 아이템을 지급합니다
```

and under the existing `permissions:` section add:

```yaml
  yeowool.life.mount.manage:
    description: 탈것 이용권 지급
    default: op
```

- [ ] **Step 8: Help** — in `yeowool-core/src/main/resources/help.yml`, add after the `/보물지도 - ...` player line:

```yaml
      - "/탈것 - 보유한 탈것 목록 (탈것 이용권을 우클릭하면 영구 해금)"
```

and after the admin line starting with `      - "/보물지도 보상설정`:

```yaml
      - "/탈것이용권 <펫ID> [수량] [닉네임] (yeowool.life.mount.manage)"
```

- [ ] **Step 9: Build** — `./gradlew :yeowool-life:build :yeowool-core:build` → BUILD SUCCESSFUL.

- [ ] **Step 10: Commit**

```bash
git add yeowool-life/src/main/java/com/yeowool/life/mount/MountVoucherItem.java yeowool-life/src/main/java/com/yeowool/life/mount/MountVoucherListener.java yeowool-life/src/main/java/com/yeowool/life/mount/MountListGui.java yeowool-life/src/main/java/com/yeowool/life/mount/MountCommand.java yeowool-life/src/main/java/com/yeowool/life/YeowoolLife.java yeowool-life/src/main/resources/messages.yml yeowool-life/src/main/resources/plugin.yml yeowool-core/src/main/resources/help.yml
git commit -m "Add mount vouchers and /탈것

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
