package com.yeowool.community.chat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * 전체(GLOBAL) 채팅을 {@code yeowool-proxy}(Velocity)로 중계해서 다른 서버 플레이어에게도
 * 보이게 한다. 이 서버 안의 로컬 브로드캐스트는 {@link ChatChannelService#send}가 그대로
 * 처리하고, 이 클래스는 "다른 서버로도 전달"만 담당 — BungeeCord 스타일 커스텀 채널
 * ({@code yeowool:chat})에 (보낸 서버 이름, 완성된 컴포넌트를 JSON 직렬화한 것)을 실어
 * 보내면, 프록시가 페이로드를 그대로 다른 서버 플레이어에게 보여준다(재포맷 없음 -
 * 닉네임 색상/칭호 등 이 서버에서 이미 완성한 서식을 그대로 유지).
 */
public final class CrossServerChatBridge {

    private static final String CHANNEL = "yeowool:chat";

    private final JavaPlugin plugin;
    private final String serverName;

    public CrossServerChatBridge(JavaPlugin plugin, String serverName) {
        this.plugin = plugin;
        this.serverName = serverName;
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
    }

    /** {@code sender}(채팅을 보낸 플레이어, 항상 온라인)의 연결을 통해 프록시로 페이로드를 전달한다. */
    public void relay(Player sender, Component message) {
        try {
            ByteArrayOutputStream byteArray = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(byteArray);
            out.writeUTF(serverName);
            out.writeUTF(GsonComponentSerializer.gson().serialize(message));
            sender.sendPluginMessage(plugin, CHANNEL, byteArray.toByteArray());
        } catch (IOException e) {
            plugin.getLogger().warning("크로스서버 채팅 전송 실패: " + e.getMessage());
        }
    }
}
