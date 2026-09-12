package com.yeowool.life.fishing;

import com.yeowool.core.api.service.MessageService;
import com.yeowool.life.fishing.customfishing.CustomFishingBridge;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** {@code /물고기지급} (yeowool.admin) — {@link FishAdminCommand}와 동일한 {@link FishAdminGui}를 인자 없이 바로 연다. */
public final class FishGiveCommand implements CommandExecutor {

    private final MessageService messages;
    private final List<FishRarity> rarities;
    private final boolean customFishingEnabled;
    private final int backgroundOffsetPx;

    public FishGiveCommand(MessageService messages, List<FishRarity> rarities, boolean customFishingEnabled, int backgroundOffsetPx) {
        this.messages = messages;
        this.rarities = rarities;
        this.customFishingEnabled = customFishingEnabled;
        this.backgroundOffsetPx = backgroundOffsetPx;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return true;
        }
        if (!player.hasPermission("yeowool.admin")) {
            messages.send(player, "general.no-permission");
            return true;
        }
        // CustomFishing 물고기는 매번 새로 합침 — 플러그인 활성화 시점에 캐싱해두면
        // CustomFishing 쪽 로딩(loot 등록)이 아직 안 끝난 상태를 그대로 굳혀버릴 수 있음.
        List<FishRarity> combined = rarities;
        if (customFishingEnabled) {
            combined = new ArrayList<>(rarities);
            combined.add(CustomFishingBridge.buildRarity());
        }
        new FishAdminGui(player, combined, 0, backgroundOffsetPx).open(player);
        return true;
    }
}
