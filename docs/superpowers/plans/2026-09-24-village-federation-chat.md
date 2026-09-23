# 마을 연합 2단계 — 연합 채팅 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Federation members (land owners + residents of every land in the federation) get a private cross-server chat via `/연합채팅 <메시지>` (one-shot) and `/연합채팅` (toggle mode).

**Architecture:** The sending server resolves the sender's federation and the recipient UUID set with raw JDBC, delivers to recipients online on its own server, and sends the remaining UUIDs plus the rendered message to the Velocity proxy over a new generic `yeowool:targeted` plugin-messaging channel; the proxy just delivers to whichever of those UUIDs are online. Toggle mode is a `LOW`-priority `AsyncChatEvent` listener in yeowool-federation that cancels the event, so yeowool-community's `NORMAL`/`ignoreCancelled` chat listener skips it with no change to community.

**Tech Stack:** Paper 1.21.4 API, Velocity API (proxy), Adventure (`GsonComponentSerializer`), raw JDBC via `YeowoolCoreAPI.dataSource()`, JUnit 5.

**Spec:** `docs/superpowers/specs/2026-09-24-village-federation-chat-design.md`

## Global Constraints

- Do not modify yeowool-community (no FEDERATION entry in `/채널`).
- yeowool-federation keeps NO compile dependency on yeowool-land / yeowool-community — land tables are read with raw JDBC.
- Recipients = `yw_lands.owner_uuid` ∪ `yw_land_members.member_uuid` over every land in the federation.
- Sender's federation: the federation of a land the player OWNS; otherwise the first (by `yw_federations.name`) federation among lands the player is a RESIDENT of.
- Output format: `[연합] <player.playerListName()>: <메시지>`, `[연합]` tag in `NamedTextColor.DARK_GREEN`.
- Toggle state key: player setting `federation.chat-mode` (value `"on"` = on, blank = off).
- Chat listener priority: `EventPriority.LOW`.
- Cooldown config key: `chat.cooldown-ms` in yeowool-federation `config.yml`, default `1500`.
- Mute check: `core.punishments().activeMute(uuid)`.
- Proxy payload (`yeowool:targeted`): `int count`, then `count` × `UTF uuid-string`, then `UTF component-json` (Java `DataOutputStream` format). Proxy needs no server name.
- Only zero-Bukkit/zero-JDBC classes get JUnit tests; everything else is verified by build + live deploy.
- Deploy: yeowool-federation + yeowool-core jars → `C:/YEOWOOL/{lobby,town,wild}/plugins/`; yeowool-proxy jar → `C:/YEOWOOL/proxy/plugins/`. Never send RCON `stop` — the user restarts servers manually.
- Git: stage only named files (never `git add -A` / `git add .`), never amend. Log user-facing changes to `update.md` (top, under today's `## 2026-09-24` heading).
- If `./gradlew` fails with `Unable to delete directory '...\build\test-results\test\binary'`, that is a OneDrive file lock: `rm -rf` that folder and re-run.

---

### Task 1: Proxy `yeowool:targeted` channel

**Files:**
- Modify: `yeowool-proxy/src/main/java/com/yeowool/proxy/YeowoolProxy.java`

**Interfaces:**
- Produces: proxy accepts `yeowool:targeted` payloads in the format from Global Constraints and delivers the component to each listed UUID that is online on the proxy.

- [ ] **Step 1: Add the channel constant** next to the existing `CHAT_CHANNEL` constant (around line 50):

```java
    private static final MinecraftChannelIdentifier TARGETED_CHANNEL = MinecraftChannelIdentifier.create("yeowool", "targeted");
```

- [ ] **Step 2: Register it** in `onProxyInitialize`, right after `server.getChannelRegistrar().register(CHAT_CHANNEL);`:

```java
        server.getChannelRegistrar().register(TARGETED_CHANNEL);
```

- [ ] **Step 3: Handle it.** Replace the start of `onPluginMessage` — currently:

```java
    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getIdentifier().equals(CHAT_CHANNEL)) {
            return;
        }
```

with:

```java
    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (event.getIdentifier().equals(TARGETED_CHANNEL)) {
            event.setResult(PluginMessageEvent.ForwardResult.handled());
            deliverTargeted(event.getData());
            return;
        }
        if (!event.getIdentifier().equals(CHAT_CHANNEL)) {
            return;
        }
```

and add this method below `onPluginMessage`:

```java
    /**
     * {@code yeowool:targeted}: a backend server sends a recipient UUID list plus a
     * rendered message; deliver it to whichever of those players are online. The
     * sender already delivered to recipients on its own server and left them out
     * of the list, so no source-server filtering is needed here.
     */
    private void deliverTargeted(byte[] data) {
        ByteArrayDataInput in = ByteStreams.newDataInput(data);
        List<java.util.UUID> recipients = new java.util.ArrayList<>();
        Component message;
        try {
            int count = in.readInt();
            for (int i = 0; i < count; i++) {
                recipients.add(java.util.UUID.fromString(in.readUTF()));
            }
            message = GsonComponentSerializer.gson().deserialize(in.readUTF());
        } catch (Exception e) {
            logger.warn("잘못된 지정 수신자 메시지 페이로드를 받았습니다: {}", e.getMessage());
            return;
        }
        for (java.util.UUID recipient : recipients) {
            server.getPlayer(recipient).ifPresent(player -> player.sendMessage(message));
        }
    }
```

- [ ] **Step 4: Build**

Run: `./gradlew :yeowool-proxy:build`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Deploy** — copy `yeowool-proxy/build/libs/yeowool-proxy-1.0.0-SNAPSHOT.jar` to `C:/YEOWOOL/proxy/plugins/` (overwrite). Do not start/stop the proxy.

- [ ] **Step 6: Commit**

```bash
git add yeowool-proxy/src/main/java/com/yeowool/proxy/YeowoolProxy.java
git commit -m "Add generic yeowool:targeted proxy channel for recipient-list messages"
```

---

### Task 2: Targeted payload encoder (contract with the proxy)

**Files:**
- Create: `yeowool-federation/src/main/java/com/yeowool/federation/chat/TargetedPayload.java`
- Test: `yeowool-federation/src/test/java/com/yeowool/federation/chat/TargetedPayloadTest.java`

**Interfaces:**
- Produces: `public static byte[] TargetedPayload.encode(Collection<UUID> recipients, String componentJson)` — byte layout exactly as Global Constraints (the proxy from Task 1 decodes it).

- [ ] **Step 1: Write the failing test**

```java
package com.yeowool.federation.chat;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TargetedPayloadTest {

    @Test
    void encodesCountThenUuidsThenMessageInProxyReadOrder() throws IOException {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        byte[] payload = TargetedPayload.encode(List.of(first, second), "{\"text\":\"안녕\"}");

        DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload));
        assertEquals(2, in.readInt());
        assertEquals(first.toString(), in.readUTF());
        assertEquals(second.toString(), in.readUTF());
        assertEquals("{\"text\":\"안녕\"}", in.readUTF());
        assertEquals(0, in.available());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :yeowool-federation:test`
Expected: FAIL — compilation error, `TargetedPayload` does not exist.

- [ ] **Step 3: Write the implementation**

```java
package com.yeowool.federation.chat;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.UUID;

/** Byte layout of the proxy's {@code yeowool:targeted} channel — must match YeowoolProxy#deliverTargeted's read order. */
public final class TargetedPayload {

    private TargetedPayload() {
    }

    public static byte[] encode(Collection<UUID> recipients, String componentJson) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(recipients.size());
            for (UUID recipient : recipients) {
                out.writeUTF(recipient.toString());
            }
            out.writeUTF(componentJson);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :yeowool-federation:test`
Expected: `BUILD SUCCESSFUL`, all tests pass (existing `FederationRulesTest` + new `TargetedPayloadTest`).

- [ ] **Step 5: Commit**

```bash
git add yeowool-federation/src/main/java/com/yeowool/federation/chat/TargetedPayload.java yeowool-federation/src/test/java/com/yeowool/federation/chat/TargetedPayloadTest.java
git commit -m "Add targeted-payload encoder for federation chat relay"
```

---

### Task 3: `FederationChatService` — lookup, format, deliver

**Files:**
- Create: `yeowool-federation/src/main/java/com/yeowool/federation/chat/FederationChatService.java`

**Interfaces:**
- Consumes: `TargetedPayload.encode(Collection<UUID>, String)` (Task 2); `YeowoolCoreAPI` (`punishments()`), `javax.sql.DataSource`.
- Produces:
  - `public FederationChatService(JavaPlugin plugin, YeowoolCoreAPI core, DataSource dataSource, long cooldownMillis)` — must be constructed on the main thread (registers the outgoing channel).
  - `public enum SendResult { SENT, MUTED, COOLDOWN, NO_FEDERATION }`
  - `public SendResult send(Player sender, String plainMessage) throws SQLException` — does JDBC; call OFF the main thread.
  - `public Optional<UUID> findFederationId(UUID playerUuid) throws SQLException` — does JDBC; call OFF the main thread.
  - `public Optional<String> activeMuteReason(UUID playerUuid)` — blocking; call OFF the main thread.

- [ ] **Step 1: Write the class**

```java
package com.yeowool.federation.chat;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.model.PunishmentEntry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Federation-only chat. Recipients are the owners + residents of every land in
 * the sender's federation, read straight from yeowool-land's tables (no compile
 * dependency, same pattern as {@code LandLookup}). Local recipients get the
 * message directly; the rest go to the proxy over {@code yeowool:targeted}.
 * Every public method except the constructor does blocking I/O — never call it
 * on the main thread.
 */
public final class FederationChatService {

    public static final String CHANNEL = "yeowool:targeted";

    private static final String OWNED_LAND_FEDERATION =
            "SELECT m.federation_id FROM yw_federation_members m " +
                    "JOIN yw_lands l ON l.id = m.land_id WHERE l.owner_uuid = ?";
    private static final String RESIDENT_LAND_FEDERATION =
            "SELECT m.federation_id FROM yw_federation_members m " +
                    "JOIN yw_land_members lm ON lm.land_id = m.land_id " +
                    "JOIN yw_federations f ON f.id = m.federation_id " +
                    "WHERE lm.member_uuid = ? ORDER BY f.name LIMIT 1";
    private static final String RECIPIENTS =
            "SELECT l.owner_uuid AS uuid FROM yw_lands l " +
                    "JOIN yw_federation_members m ON m.land_id = l.id WHERE m.federation_id = ? " +
                    "UNION " +
                    "SELECT lm.member_uuid AS uuid FROM yw_land_members lm " +
                    "JOIN yw_federation_members m ON m.land_id = lm.land_id WHERE m.federation_id = ?";

    public enum SendResult { SENT, MUTED, COOLDOWN, NO_FEDERATION }

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private final DataSource dataSource;
    private final long cooldownMillis;
    private final Map<UUID, Long> lastSentAt = new ConcurrentHashMap<>();

    public FederationChatService(JavaPlugin plugin, YeowoolCoreAPI core, DataSource dataSource, long cooldownMillis) {
        this.plugin = plugin;
        this.core = core;
        this.dataSource = dataSource;
        this.cooldownMillis = cooldownMillis;
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
    }

    public Optional<String> activeMuteReason(UUID playerUuid) {
        return core.punishments().activeMute(playerUuid).join().map(PunishmentEntry::reason);
    }

    public SendResult send(Player sender, String plainMessage) throws SQLException {
        if (activeMuteReason(sender.getUniqueId()).isPresent()) {
            return SendResult.MUTED;
        }
        long now = System.currentTimeMillis();
        Long last = lastSentAt.get(sender.getUniqueId());
        if (last != null && now - last < cooldownMillis) {
            return SendResult.COOLDOWN;
        }
        Optional<UUID> federationId = findFederationId(sender.getUniqueId());
        if (federationId.isEmpty()) {
            return SendResult.NO_FEDERATION;
        }
        lastSentAt.put(sender.getUniqueId(), now);

        Component message = Component.text()
                .append(Component.text("[연합] ", NamedTextColor.DARK_GREEN))
                .append(sender.playerListName())
                .append(Component.text(": ", NamedTextColor.GRAY))
                .append(Component.text(plainMessage, NamedTextColor.WHITE))
                .build();

        List<UUID> remote = new ArrayList<>();
        for (UUID recipient : findRecipients(federationId.get())) {
            Player online = Bukkit.getPlayer(recipient);
            if (online != null) {
                online.sendMessage(message);
            } else {
                remote.add(recipient);
            }
        }
        Bukkit.getConsoleSender().sendMessage(message);

        if (!remote.isEmpty()) {
            String json = GsonComponentSerializer.gson().serialize(message);
            sender.sendPluginMessage(plugin, CHANNEL, TargetedPayload.encode(remote, json));
        }
        return SendResult.SENT;
    }

    public Optional<UUID> findFederationId(UUID playerUuid) throws SQLException {
        Optional<UUID> owned = queryFederationId(OWNED_LAND_FEDERATION, playerUuid);
        return owned.isPresent() ? owned : queryFederationId(RESIDENT_LAND_FEDERATION, playerUuid);
    }

    private Optional<UUID> queryFederationId(String sql, UUID playerUuid) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(sql)) {
            select.setString(1, playerUuid.toString());
            try (ResultSet rs = select.executeQuery()) {
                return rs.next() ? Optional.of(UUID.fromString(rs.getString(1))) : Optional.empty();
            }
        }
    }

    private Set<UUID> findRecipients(UUID federationId) throws SQLException {
        Set<UUID> recipients = new HashSet<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(RECIPIENTS)) {
            select.setString(1, federationId.toString());
            select.setString(2, federationId.toString());
            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    recipients.add(UUID.fromString(rs.getString("uuid")));
                }
            }
        }
        return recipients;
    }
}
```

- [ ] **Step 2: Build**

Run: `./gradlew :yeowool-federation:build`
Expected: `BUILD SUCCESSFUL` (nothing wires this class up yet — that's Task 4).

- [ ] **Step 3: Commit**

```bash
git add yeowool-federation/src/main/java/com/yeowool/federation/chat/FederationChatService.java
git commit -m "Add FederationChatService: resolve federation, format, deliver locally and via proxy"
```

---

### Task 4: `/연합채팅` command, toggle-mode listener, wiring, help, deploy

**Files:**
- Create: `yeowool-federation/src/main/java/com/yeowool/federation/chat/FederationChatCommand.java`
- Create: `yeowool-federation/src/main/java/com/yeowool/federation/chat/FederationChatListener.java`
- Modify: `yeowool-federation/src/main/java/com/yeowool/federation/YeowoolFederation.java`
- Modify: `yeowool-federation/src/main/resources/plugin.yml`
- Modify: `yeowool-federation/src/main/resources/config.yml`
- Modify: `yeowool-federation/src/main/resources/messages.yml`
- Modify: `yeowool-core/src/main/resources/help.yml`
- Modify: `update.md`

**Interfaces:**
- Consumes: `FederationChatService` and its `SendResult` / `send` / `findFederationId` / `activeMuteReason` (Task 3); `MessageService` (`messages.send(sender, key, TagResolver...)`); `YeowoolCoreAPI.playerData().getOnline(uuid)` → `PlayerData` with `getSetting(String key, String default)` / `setSetting(String key, String value)`.
- Produces: shared constant `FederationChatCommand.MODE_SETTING = "federation.chat-mode"` used by the listener.

- [ ] **Step 1: Add the command to `plugin.yml`** — append under `commands:` (same indentation as `연합:`):

```yaml
  연합채팅:
    description: 연합원에게만 보이는 채팅 (/연합채팅 <메시지> 한 번 보내기, /연합채팅 모드 켜기/끄기)
```

- [ ] **Step 2: Replace `config.yml` contents** with:

```yaml
# YeowoolFederation 설정

chat:
  # 연합 채팅(/연합채팅, 연합 채팅 모드) 도배 방지 — 한 번 보낸 뒤 다음 메시지까지 기다려야 하는 시간(밀리초)
  cooldown-ms: 1500
```

- [ ] **Step 3: Append to `messages.yml`** under the existing `federation:` block (same 2-space indentation as the other keys):

```yaml
  chat-no-federation: "§c소속된 연합이 없어서 연합 채팅을 쓸 수 없습니다."
  chat-muted: "§c채팅이 음소거된 상태입니다. 사유: <reason>"
  chat-cooldown: "§c채팅을 너무 빠르게 보내고 있습니다."
  chat-mode-on: "§a연합 채팅 모드를 켰습니다. 이제 일반 채팅이 연합으로 전송됩니다. §7(/연합채팅 을 다시 입력하면 해제 — 켜져 있는 동안은 /채널 설정보다 우선합니다)"
  chat-mode-off: "§7연합 채팅 모드를 껐습니다. 일반 채팅은 원래 채널로 돌아갑니다."
  chat-mode-auto-off: "§7소속된 연합이 없어져서 연합 채팅 모드가 자동으로 꺼졌습니다."
```

- [ ] **Step 4: Write `FederationChatCommand.java`**

```java
package com.yeowool.federation.chat;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;

/** {@code /연합채팅 <메시지>} sends once; {@code /연합채팅} with no arguments toggles federation chat mode. */
public final class FederationChatCommand implements CommandExecutor {

    public static final String MODE_SETTING = "federation.chat-mode";
    public static final String MODE_ON = "on";

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private final MessageService messages;
    private final FederationChatService chatService;
    private final ExecutorService executor;

    public FederationChatCommand(JavaPlugin plugin, YeowoolCoreAPI core, MessageService messages,
                                 FederationChatService chatService, ExecutorService executor) {
        this.plugin = plugin;
        this.core = core;
        this.messages = messages;
        this.chatService = chatService;
        this.executor = executor;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "federation.player-only");
            return true;
        }
        if (args.length == 0) {
            toggleMode(player);
            return true;
        }
        String message = String.join(" ", args);
        executor.execute(() -> {
            try {
                sendResultFeedback(player, chatService.send(player, message));
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "연합 채팅 전송 실패", e);
            }
        });
        return true;
    }

    private void toggleMode(Player player) {
        var data = core.playerData().getOnline(player.getUniqueId());
        if (MODE_ON.equals(data.getSetting(MODE_SETTING, ""))) {
            data.setSetting(MODE_SETTING, "");
            messages.send(player, "federation.chat-mode-off");
            return;
        }
        executor.execute(() -> {
            try {
                boolean inFederation = chatService.findFederationId(player.getUniqueId()).isPresent();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!inFederation) {
                        messages.send(player, "federation.chat-no-federation");
                        return;
                    }
                    core.playerData().getOnline(player.getUniqueId()).setSetting(MODE_SETTING, MODE_ON);
                    messages.send(player, "federation.chat-mode-on");
                });
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "연합 채팅 모드 전환 실패", e);
            }
        });
    }

    /** Safe to call from any thread — {@code Player#sendMessage} is thread-safe on Paper. */
    void sendResultFeedback(Player player, FederationChatService.SendResult result) {
        switch (result) {
            case SENT -> { }
            case MUTED -> messages.send(player, "federation.chat-muted",
                    Placeholder.unparsed("reason", chatService.activeMuteReason(player.getUniqueId()).orElse("")));
            case COOLDOWN -> messages.send(player, "federation.chat-cooldown");
            case NO_FEDERATION -> messages.send(player, "federation.chat-no-federation");
        }
    }
}
```

- [ ] **Step 5: Write `FederationChatListener.java`**

```java
package com.yeowool.federation.chat;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.logging.Level;

/**
 * Federation chat mode: runs before yeowool-community's ChatListener (NORMAL,
 * ignoreCancelled), so cancelling here is enough to keep the message out of the
 * normal channels. Already on an async thread, so the JDBC lookups run inline.
 */
public final class FederationChatListener implements Listener {

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private final MessageService messages;
    private final FederationChatService chatService;
    private final FederationChatCommand command;

    public FederationChatListener(JavaPlugin plugin, YeowoolCoreAPI core, MessageService messages,
                                  FederationChatService chatService, FederationChatCommand command) {
        this.plugin = plugin;
        this.core = core;
        this.messages = messages;
        this.chatService = chatService;
        this.command = command;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        var data = core.playerData().getOnline(player.getUniqueId());
        if (!FederationChatCommand.MODE_ON.equals(data.getSetting(FederationChatCommand.MODE_SETTING, ""))) {
            return;
        }
        try {
            if (chatService.findFederationId(player.getUniqueId()).isEmpty()) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        core.playerData().getOnline(player.getUniqueId()).setSetting(FederationChatCommand.MODE_SETTING, ""));
                messages.send(player, "federation.chat-mode-auto-off");
                return;
            }
            event.setCancelled(true);
            String plainMessage = PlainTextComponentSerializer.plainText().serialize(event.message());
            command.sendResultFeedback(player, chatService.send(player, plainMessage));
        } catch (SQLException e) {
            event.setCancelled(true);
            plugin.getLogger().log(Level.SEVERE, "연합 채팅 모드 전송 실패", e);
        }
    }
}
```

Note: on a DB error the chat is cancelled rather than leaked to global chat — the player meant it for the federation only.

- [ ] **Step 6: Wire it up in `YeowoolFederation.onEnable`.**

Add as the FIRST line of `onEnable()` (before the `YeowoolCoreAPI core = ...` line), so `config.yml` exists and gains new keys:

```java
        ConfigMerger.mergeDefaults(this, "config.yml");
```

Then after the existing `/연합` command registration block (`if (command != null) { command.setExecutor(federationCommand); }`) and before the `LandDeletedEvent` listener registration, add:

```java
        FederationChatService chatService = new FederationChatService(
                this, core, core.dataSource(), getConfig().getLong("chat.cooldown-ms", 1500L));
        FederationChatCommand chatCommand = new FederationChatCommand(this, core, messages, chatService, executor);
        var chatCommandEntry = getCommand("연합채팅");
        if (chatCommandEntry != null) {
            chatCommandEntry.setExecutor(chatCommand);
        }
        getServer().getPluginManager().registerEvents(
                new FederationChatListener(this, core, messages, chatService, chatCommand), this);
```

Add imports:

```java
import com.yeowool.core.util.ConfigMerger;
import com.yeowool.federation.chat.FederationChatCommand;
import com.yeowool.federation.chat.FederationChatListener;
import com.yeowool.federation.chat.FederationChatService;
```

- [ ] **Step 7: Update `yeowool-core/src/main/resources/help.yml`.**

In `help.categories.마을연합`, append a line:

```yaml
      - "/연합채팅 <메시지> - 연합원에게만 한 번 보내기, /연합채팅 - 연합 채팅 모드 켜기/끄기"
```

In `help.categories.채팅`, append a line:

```yaml
      - "/연합채팅 <메시지> - 연합 채팅 (다른 서버의 연합원에게도 보임)"
```

- [ ] **Step 8: Build everything touched**

Run: `./gradlew :yeowool-federation:build :yeowool-core:build`
Expected: `BUILD SUCCESSFUL` (all tests pass).

- [ ] **Step 9: Deploy** — copy `yeowool-federation/build/libs/yeowool-federation-1.0.0-SNAPSHOT.jar` and `yeowool-core/build/libs/yeowool-core-1.0.0-SNAPSHOT.jar` to each of `C:/YEOWOOL/lobby/plugins/`, `C:/YEOWOOL/town/plugins/`, `C:/YEOWOOL/wild/plugins/` (overwrite). Do not start/stop any server.

- [ ] **Step 10: Log to `update.md`** — insert this new `###` section immediately after the line `## 2026-09-24` (so it becomes the first entry of that day, above the day's existing entries), matching the surrounding style:

```markdown
### 연합 채팅 추가 — 마을 연합 2단계 (yeowool-federation, yeowool-proxy, yeowool-core)
같은 연합에 속한 모든 토지의 **소유주 + 주민 전원**끼리만 보이는 채팅이 생겼습니다. 로비/마을/야생 어느 서버에 있든 서로 보입니다.
- `/연합채팅 <메시지>` — 연합 채팅으로 한 번만 보내기
- `/연합채팅` (인자 없이) — 연합 채팅 모드 켜기/끄기. 켜져 있으면 평소 채팅이 전부 연합으로 갑니다(`/채널` 설정보다 우선). 연합에서 탈퇴/추방되거나 연합이 없어지면 자동으로 꺼지고 원래 채팅으로 돌아갑니다.
- 음소거 상태면 연합 채팅도 막히고, 도배 방지 쿨다운(기본 1.5초, `config.yml`의 `chat.cooldown-ms`)이 있습니다.
- 여러 토지의 주민이라 연합이 둘 이상인 경우: 내가 소유한 토지의 연합이 우선, 없으면 연합 이름순 첫 번째 연합으로 보냅니다.
- 프록시에 "지정한 사람들에게만 메시지 전달" 채널(`yeowool:targeted`)을 새로 추가했습니다 — 나중에 연합 알림 등에도 재사용합니다.

세 서버(lobby/town/wild) `yeowool-federation`/`yeowool-core` jar, 프록시 `yeowool-proxy` jar 배포 완료 — **세 서버와 프록시 모두 재시작 필요**.
```

- [ ] **Step 11: Commit**

```bash
git add yeowool-federation/src/main/java/com/yeowool/federation/chat/FederationChatCommand.java yeowool-federation/src/main/java/com/yeowool/federation/chat/FederationChatListener.java yeowool-federation/src/main/java/com/yeowool/federation/YeowoolFederation.java yeowool-federation/src/main/resources/plugin.yml yeowool-federation/src/main/resources/config.yml yeowool-federation/src/main/resources/messages.yml yeowool-core/src/main/resources/help.yml update.md
git commit -m "Add /연합채팅 with toggle mode, wire federation chat, update help"
```

- [ ] **Step 12: Manual verification (after the user restarts proxy + 3 servers)** — two federation members on different servers (one land owner, one resident of a member land) exchange `/연합채팅 안녕`; a player with no federation sees nothing; `/연합채팅` toggles mode and plain chat goes to `[연합]`; after `/연합 탈퇴`, the next plain chat turns the mode off with the auto-off notice and goes to the normal channel.
