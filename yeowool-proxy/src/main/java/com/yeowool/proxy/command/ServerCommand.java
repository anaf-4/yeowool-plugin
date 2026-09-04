package com.yeowool.proxy.command;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.yeowool.proxy.queue.QueueManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@code /서버 <이름>} (section 8.2 YeowoolServer): moves the player to a
 * backend server, respecting the configured maintenance list and the
 * per-server player cap (falling back to {@link QueueManager} when full).
 */
public final class ServerCommand implements SimpleCommand {

    private final ProxyServer proxy;
    private final QueueManager queueManager;
    private final Set<String> maintenanceServers;
    private final int maxPlayersPerServer;

    public ServerCommand(ProxyServer proxy, QueueManager queueManager, Set<String> maintenanceServers, int maxPlayersPerServer) {
        this.proxy = proxy;
        this.queueManager = queueManager;
        this.maintenanceServers = maintenanceServers;
        this.maxPlayersPerServer = maxPlayersPerServer;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player player)) {
            return;
        }
        String[] args = invocation.arguments();
        if (args.length != 1) {
            player.sendMessage(Component.text("사용법: /서버 <이름>", NamedTextColor.RED));
            return;
        }

        String name = args[0];
        if (maintenanceServers.contains(name)) {
            player.sendMessage(Component.text(name + " 서버는 현재 점검 중입니다.", NamedTextColor.RED));
            return;
        }

        RegisteredServer target = proxy.getServer(name).orElse(null);
        if (target == null) {
            player.sendMessage(Component.text("존재하지 않는 서버입니다: " + name, NamedTextColor.RED));
            return;
        }

        if (target.getPlayersConnected().size() >= maxPlayersPerServer) {
            queueManager.enqueue(player, target);
            return;
        }

        player.createConnectionRequest(target).connect().thenAccept(result -> {
            // 이 시점의 실패는 거의 항상 "그 서버가 재시작/점검 중이라 아직 응답이 없음"이므로,
            // 그냥 에러로 끝내지 않고 대기열에 등록해서 서버가 돌아오면 자동으로 들여보낸다.
            if (!result.isSuccessful()) {
                queueManager.enqueue(player, target);
            }
        });
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        if (invocation.arguments().length > 1) {
            return List.of();
        }
        return proxy.getAllServers().stream()
                .map(server -> server.getServerInfo().getName())
                .collect(Collectors.toList());
    }
}
