package com.yeowool.market.citizens;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * {@code /상점이동} — teleports the player to the shop NPC's current location instead of opening
 * the shop GUI directly (unlike {@code /상점}), so players still have to walk up and right-click
 * the NPC themselves. Looks the NPC up live via {@link CitizensAPI} each time rather than caching
 * a location, so moving the NPC later doesn't require touching this command.
 *
 * <p>The NPC only physically exists on one backend server ({@code npc-shop.npc-server-id}, lobby
 * by default). Running this on a different server sends the player there first via Velocity's
 * BungeeCord-compatible "Connect" plugin channel and marks {@link #PENDING_SETTING_KEY} on their
 * {@link com.yeowool.core.api.model.PlayerData}; {@link ShopTeleportJoinListener} — registered
 * only on the NPC's own server — finishes the teleport once they actually join there.
 */
public final class ShopLocationCommand implements CommandExecutor {

    public static final String PENDING_SETTING_KEY = "market.pending-shop-teleport";

    private final JavaPlugin plugin;
    private final YeowoolCoreAPI core;
    private final MessageService messages;
    private final int npcId;
    private final String npcServerId;
    private final String thisServerId;

    public ShopLocationCommand(JavaPlugin plugin, YeowoolCoreAPI core, MessageService messages,
                                int npcId, String npcServerId, String thisServerId) {
        this.plugin = plugin;
        this.core = core;
        this.messages = messages;
        this.npcId = npcId;
        this.npcServerId = npcServerId;
        this.thisServerId = thisServerId;
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, "BungeeCord");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return true;
        }
        if (!thisServerId.equals(npcServerId)) {
            core.playerData().getOnline(player.getUniqueId()).setSetting(PENDING_SETTING_KEY, "true");
            sendToServer(player, npcServerId);
            return true;
        }
        teleportToNpc(player);
        return true;
    }

    /** Package-private so {@link ShopTeleportJoinListener} (on the NPC's own server) reuses the exact same lookup. */
    void teleportToNpc(Player player) {
        if (npcId < 0) {
            messages.send(player, "npcshop.location-not-set");
            return;
        }
        NPC npc = CitizensAPI.getNPCRegistry().getById(npcId);
        if (npc == null || !npc.isSpawned()) {
            messages.send(player, "npcshop.location-not-set");
            return;
        }
        Location location = npc.getStoredLocation();
        if (location == null) {
            messages.send(player, "npcshop.location-not-set");
            return;
        }
        player.teleportAsync(location);
        messages.send(player, "npcshop.teleported");
    }

    private void sendToServer(Player player, String serverName) {
        try {
            ByteArrayOutputStream byteArray = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(byteArray);
            out.writeUTF("Connect");
            out.writeUTF(serverName);
            player.sendPluginMessage(plugin, "BungeeCord", byteArray.toByteArray());
        } catch (IOException e) {
            plugin.getLogger().warning("상점 NPC 서버로 이동 요청 전송 실패: " + e.getMessage());
        }
    }
}
