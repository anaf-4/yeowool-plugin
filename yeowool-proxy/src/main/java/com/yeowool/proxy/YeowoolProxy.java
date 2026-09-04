package com.yeowool.proxy;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.yeowool.proxy.command.ServerCommand;
import com.yeowool.proxy.queue.QueueManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * 서버 이동, 대기열, 점검 상태 (기획서 8.2/8.3). Velocity 프록시에서 실행되므로
 * YeowoolCore에 직접 의존할 수 없다 — 공유가 필요한 데이터(대기열 상태 등)는
 * 지금은 프록시 메모리에만 두고, 필요해지면 플러그인 메시징이나 같은 MySQL
 * 인스턴스에 대한 별도 조회로 확장한다.
 */
@Plugin(
        id = "yeowool-proxy",
        name = "YeowoolProxy",
        version = "1.0.0-SNAPSHOT",
        description = "서버 이동, 대기열, 점검 상태 (Velocity)"
)
public final class YeowoolProxy {

    private static final MinecraftChannelIdentifier CHAT_CHANNEL = MinecraftChannelIdentifier.create("yeowool", "chat");

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;

    private Set<String> maintenanceServers = Set.of();
    private int maxPlayersPerServer = 100;
    private QueueManager queueManager;

    @Inject
    public YeowoolProxy(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        loadConfig();

        this.queueManager = new QueueManager(server);
        CommandMeta meta = server.getCommandManager().metaBuilder("서버").build();
        server.getCommandManager().register(meta, new ServerCommand(server, queueManager, maintenanceServers, maxPlayersPerServer));

        server.getChannelRegistrar().register(CHAT_CHANNEL);

        server.getScheduler().buildTask(this, () ->
                server.getAllServers().forEach(registered -> queueManager.tick(registered, maxPlayersPerServer))
        ).repeat(Duration.ofSeconds(5)).schedule();

        logger.info("YeowoolProxy가 활성화되었습니다. (점검 서버: {}, 서버당 최대 인원: {})", maintenanceServers, maxPlayersPerServer);
    }

    /**
     * 백엔드 서버(YeowoolCommunity)가 전체 채팅을 {@code yeowool:chat} 채널로 보내면, 보낸
     * 서버를 제외한 나머지 서버에 접속 중인 모든 플레이어에게 그대로 전달한다 - 로컬
     * 브로드캐스트는 이미 보낸 서버 쪽에서 처리했으므로 여기서 다시 보내면 중복된다.
     */
    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getIdentifier().equals(CHAT_CHANNEL)) {
            return;
        }
        event.setResult(PluginMessageEvent.ForwardResult.handled());

        ByteArrayDataInput in = ByteStreams.newDataInput(event.getData());
        String sourceServer = in.readUTF();
        Component message;
        try {
            message = GsonComponentSerializer.gson().deserialize(in.readUTF());
        } catch (Exception e) {
            logger.warn("잘못된 크로스서버 채팅 페이로드를 받았습니다: {}", e.getMessage());
            return;
        }

        for (Player player : server.getAllPlayers()) {
            String playerServer = player.getCurrentServer().map(conn -> conn.getServerInfo().getName()).orElse("");
            if (!playerServer.equals(sourceServer)) {
                player.sendMessage(message);
            }
        }
    }

    /** A player who disconnects from the proxy entirely while still queued must be dropped from it, or everyone behind them waits one spot longer than necessary forever. */
    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        queueManager.removePlayer(event.getPlayer().getUniqueId());
    }

    /**
     * velocity.toml의 {@code try}(최초 접속 서버, 보통 lobby)가 재시작/점검 중이라 응답이
     * 없으면, 그대로 접속을 실패시키는 대신 "queue" 서버로 최초 접속시키고 대기열에
     * 등록해서 그 서버가 돌아오는 즉시 자동으로 들여보낸다.
     */
    @Subscribe
    public EventTask onChooseInitialServer(PlayerChooseInitialServerEvent event) {
        List<String> tryOrder = server.getConfiguration().getAttemptConnectionOrder();
        RegisteredServer queueServer = server.getServer("queue").orElse(null);
        if (tryOrder.isEmpty() || queueServer == null) {
            return null;
        }
        RegisteredServer primary = server.getServer(tryOrder.get(0)).orElse(null);
        if (primary == null) {
            return null;
        }
        return EventTask.resumeWhenComplete(primary.ping().handle((ping, error) -> {
            if (error != null) {
                event.setInitialServer(queueServer);
                queueManager.enqueueForInitialConnect(event.getPlayer(), primary);
            }
            return null;
        }));
    }

    /**
     * 접속해있던 서버가 갑자기 죽었을 때(재시작 등)의 처리. 기본 동작은 그냥 프록시에서까지
     * 통째로 끊어버리는 것이라("lobby에서 추방됐습니다: Server closed" 후 접속 종료), 그 서버가
     * 진짜로 응답이 없는 상태인지 ping으로 먼저 확인하고 맞으면 queue로 돌려서 대기열에
     * 등록한다 — 밴/친목 킥처럼 서버는 멀쩡한데 그냥 쫓겨난 경우는 ping이 성공하므로
     * 건드리지 않고 원래대로 끊긴다.
     */
    @Subscribe
    public EventTask onKickedFromServer(KickedFromServerEvent event) {
        RegisteredServer originalServer = event.getServer();
        RegisteredServer queueServer = server.getServer("queue").orElse(null);
        if (queueServer == null || originalServer.equals(queueServer)) {
            return null;
        }
        return EventTask.resumeWhenComplete(originalServer.ping().handle((ping, error) -> {
            if (error != null) {
                event.setResult(KickedFromServerEvent.RedirectPlayer.create(queueServer));
                queueManager.enqueueForInitialConnect(event.getPlayer(), originalServer);
            }
            return null;
        }));
    }

    private void loadConfig() {
        try {
            Files.createDirectories(dataDirectory);
            Path configFile = dataDirectory.resolve("config.properties");
            if (!Files.exists(configFile)) {
                try (InputStream in = getClass().getClassLoader().getResourceAsStream("config.properties")) {
                    if (in != null) {
                        Files.copy(in, configFile);
                    }
                }
            }
            if (!Files.exists(configFile)) {
                return;
            }
            Properties config = new Properties();
            try (InputStream in = Files.newInputStream(configFile)) {
                config.load(in);
            }
            String maintenance = config.getProperty("maintenance-servers", "");
            this.maintenanceServers = maintenance.isBlank() ? Set.of()
                    : Set.copyOf(Arrays.asList(maintenance.split(",")));
            this.maxPlayersPerServer = Integer.parseInt(config.getProperty("max-players-per-server", "100").trim());
        } catch (IOException e) {
            logger.error("설정 파일을 불러오지 못했습니다.", e);
        }
    }
}
