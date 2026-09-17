package com.yeowool.raid;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

import java.lang.reflect.Constructor;
import java.util.UUID;

/**
 * Relays raid life/result updates to BetterHud, via reflection — same seam as
 * {@code yeowool-quest}'s {@code QuestDialogueService}: BetterHud has no reliable Maven artifact
 * and this plugin must keep working (chat fallback) even when BetterHud isn't installed.
 */
public final class RaidHudService {

    private static final String CUSTOM_POPUP_EVENT_CLASS = "kr.toxicity.hud.api.bukkit.event.CustomPopupEvent";

    private final String bossWarningPopup;
    private final String resultPopup;

    public RaidHudService(String bossWarningPopup, String resultPopup) {
        this.bossWarningPopup = bossWarningPopup;
        this.resultPopup = resultPopup;
    }

    public void notifyLifeLost(RaidSession session, int remainingLives) {
        forEachOnlineMember(session, player -> {
            player.sendMessage(Component.text("파티 부활 횟수 " + remainingLives + "회 남음", NamedTextColor.RED));
            fireCustomPopup(player, bossWarningPopup);
        });
    }

    public void notifyResult(RaidSession session, boolean won) {
        forEachOnlineMember(session, player -> {
            player.sendMessage(won
                    ? Component.text("레이드 클리어! 보상이 우편함으로 발송되었습니다.", NamedTextColor.GOLD)
                    : Component.text("레이드 실패했습니다.", NamedTextColor.RED));
            fireCustomPopup(player, resultPopup);
        });
    }

    private void forEachOnlineMember(RaidSession session, java.util.function.Consumer<Player> action) {
        for (UUID member : session.getPartyMembers()) {
            Player player = Bukkit.getPlayer(member);
            if (player != null) {
                action.accept(player);
            }
        }
    }

    private static void fireCustomPopup(Player player, String popupName) {
        try {
            Class<?> eventClass = Class.forName(CUSTOM_POPUP_EVENT_CLASS);
            Constructor<?> constructor = eventClass.getConstructor(Player.class, String.class);
            Bukkit.getPluginManager().callEvent((Event) constructor.newInstance(player, popupName));
        } catch (ClassNotFoundException ignored) {
            // BetterHud not installed - chat fallback above already covers this player.
        } catch (ReflectiveOperationException e) {
            Bukkit.getLogger().warning("BetterHud 팝업 호출 실패 (" + popupName + "): " + e.getMessage());
        }
    }
}
