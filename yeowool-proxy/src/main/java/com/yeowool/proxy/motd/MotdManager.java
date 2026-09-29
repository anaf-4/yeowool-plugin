package com.yeowool.proxy.motd;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Server-list MOTD set live from the game servers ({@code /motd} in YeowoolAdmin → {@code yeowool:motd}).
 * With several entries they rotate every {@code intervalSeconds}; with none, velocity.toml's motd is used.
 * Saved to {@code motd.json} so a proxy restart keeps it.
 */
public final class MotdManager {

    /** Two MiniMessage lines; {@code {online}} / {@code {max}} are filled in per ping. */
    record Entry(String line1, String line2) {
    }

    private static final class State {
        List<Entry> entries = new ArrayList<>();
        int intervalSeconds = 10;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final ProxyServer server;
    private final Logger logger;
    private final Path file;
    private volatile State state = new State();

    public MotdManager(ProxyServer server, Logger logger, Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.file = dataDirectory.resolve("motd.json");
        load();
    }

    /** The MOTD to show right now, or null to keep velocity.toml's. */
    public Component current(int online, int max) {
        State snapshot = state;
        if (snapshot.entries.isEmpty()) {
            return null;
        }
        long slot = System.currentTimeMillis() / 1000 / Math.max(1, snapshot.intervalSeconds);
        return render(snapshot.entries.get((int) (slot % snapshot.entries.size())), online, max);
    }

    /**
     * Payload from a game server: sender UUID (or "console"), action, number, line 1, line 2 —
     * must match YeowoolAdmin's MotdCommand write order.
     */
    public synchronized void handle(byte[] data) {
        ByteArrayDataInput in = ByteStreams.newDataInput(data);
        String sender;
        String action;
        int number;
        String line1;
        String line2;
        try {
            sender = in.readUTF();
            action = in.readUTF();
            number = in.readInt();
            line1 = in.readUTF();
            line2 = in.readUTF();
        } catch (Exception e) {
            logger.warn("잘못된 MOTD 페이로드를 받았습니다: {}", e.toString());
            return;
        }
        State next = copy(state);
        String reply;
        switch (action) {
            case "ADD" -> {
                next.entries.add(new Entry(line1, line2));
                reply = "<green>MOTD #" + next.entries.size() + "을(를) 추가했습니다.</green>";
            }
            case "EDIT" -> {
                if (number < 1 || number > next.entries.size()) {
                    reply(sender, "<red>#" + number + " MOTD가 없습니다.</red>");
                    return;
                }
                next.entries.set(number - 1, new Entry(line1, line2));
                reply = "<green>MOTD #" + number + "을(를) 수정했습니다.</green>";
            }
            case "REMOVE" -> {
                if (number < 1 || number > next.entries.size()) {
                    reply(sender, "<red>#" + number + " MOTD가 없습니다.</red>");
                    return;
                }
                next.entries.remove(number - 1);
                reply = "<green>MOTD #" + number + "을(를) 삭제했습니다.</green>";
            }
            case "INTERVAL" -> {
                next.intervalSeconds = Math.max(1, number);
                reply = "<green>MOTD 순환 간격을 " + next.intervalSeconds + "초로 바꿨습니다.</green>";
            }
            case "CLEAR" -> {
                next.entries.clear();
                reply = "<green>MOTD를 모두 지웠습니다. 이제 velocity.toml의 기본 MOTD가 보입니다.</green>";
            }
            case "LIST" -> reply = null;
            default -> {
                logger.warn("알 수 없는 MOTD 동작: {}", action);
                return;
            }
        }
        if (reply != null) {
            state = next;
            save();
            reply(sender, reply);
        }
        sendList(sender);
    }

    private void sendList(String sender) {
        State snapshot = state;
        if (snapshot.entries.isEmpty()) {
            reply(sender, "<gray>등록된 MOTD가 없습니다 (velocity.toml 기본값 사용).</gray>");
            return;
        }
        reply(sender, "<gold>MOTD " + snapshot.entries.size() + "개 · " + snapshot.intervalSeconds + "초마다 순환</gold>");
        int online = server.getPlayerCount();
        int max = server.getConfiguration().getShowMaxPlayers();
        for (int i = 0; i < snapshot.entries.size(); i++) {
            Component preview = Component.text("#" + (i + 1) + " ").append(render(snapshot.entries.get(i), online, max));
            send(sender, preview);
        }
    }

    private static Component render(Entry entry, int online, int max) {
        Component first = MINI.deserialize(fill(entry.line1(), online, max));
        return entry.line2().isBlank() ? first
                : first.append(Component.newline()).append(MINI.deserialize(fill(entry.line2(), online, max)));
    }

    private static String fill(String line, int online, int max) {
        return line.replace("{online}", String.valueOf(online)).replace("{max}", String.valueOf(max));
    }

    private void reply(String sender, String miniMessage) {
        send(sender, MINI.deserialize(miniMessage));
    }

    private void send(String sender, Component message) {
        try {
            server.getPlayer(UUID.fromString(sender)).ifPresent(player -> player.sendMessage(message));
        } catch (IllegalArgumentException e) {
            server.getConsoleCommandSource().sendMessage(message); // sent from a game server's console
        }
    }

    private static State copy(State source) {
        State copy = new State();
        copy.entries = new ArrayList<>(source.entries);
        copy.intervalSeconds = source.intervalSeconds;
        return copy;
    }

    private void load() {
        if (!Files.exists(file)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            State loaded = GSON.fromJson(reader, State.class);
            if (loaded != null && loaded.entries != null) {
                state = loaded;
            }
        } catch (IOException | RuntimeException e) {
            logger.error("motd.json을 불러오지 못했습니다 — 기본 MOTD를 씁니다.", e);
        }
    }

    private void save() {
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(state, writer);
            }
        } catch (IOException e) {
            logger.error("motd.json 저장 실패", e);
        }
    }
}
