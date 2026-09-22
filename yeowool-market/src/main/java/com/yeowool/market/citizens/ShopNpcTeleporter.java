package com.yeowool.market.citizens;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.service.MessageService;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Teleports a player to a specific shop's Citizens NPC (used by
 * {@link com.yeowool.market.npcshop.ShopMainMenuGui} — clicking a category
 * now jumps to that shop's own NPC instead of opening its GUI directly, same
 * spirit as {@link ShopLocationCommand} but per-shop instead of one fixed
 * district point). All shop NPCs are assumed to live on the one server named
 * by {@code npc-shop.npc-server-id}, same assumption {@link ShopLocationCommand}
 * already makes for the district itself.
 *
 * <p>Cross-server case: marks {@link #PENDING_NPC_SETTING_KEY} with the
 * target npc id and sends the player there via the BungeeCord "Connect"
 * channel; {@link ShopTeleportJoinListener} finishes the jump on join.
 */
public final class ShopNpcTeleporter {

    public static final String PENDING_NPC_SETTING_KEY = "market.pending-shop-npc-teleport";

    private ShopNpcTeleporter() {
    }

    public static void teleportToShopNpc(JavaPlugin plugin, YeowoolCoreAPI core, MessageService messages,
                                          Player player, int npcId, String npcServerId, String thisServerId) {
        if (!thisServerId.equals(npcServerId)) {
            core.playerData().getOnline(player.getUniqueId()).setSetting(PENDING_NPC_SETTING_KEY, String.valueOf(npcId));
            sendToServer(plugin, player, npcServerId);
            return;
        }
        teleportNow(player, npcId, messages);
    }

    static void teleportNow(Player player, int npcId, MessageService messages) {
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

    private static void sendToServer(JavaPlugin plugin, Player player, String serverName) {
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
