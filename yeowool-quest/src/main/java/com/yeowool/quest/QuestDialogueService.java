package com.yeowool.quest;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

import java.lang.reflect.Constructor;

/**
 * Drives one player through one quest's dialogue lines, then either
 * auto-completes it (no objective) or hands off to {@code /퀘스트수락}
 * {@code /퀘스트거절}.
 *
 * <p><b>The BetterHud integration seam:</b> showing a popup to a player is
 * the one thing this plugin can verify (fires BetterHud's own
 * {@code kr.toxicity.hud.api.bukkit.event.CustomPopupEvent}, its documented
 * event for other plugins). That class is invoked via reflection instead of
 * a compile-time dependency: BetterHud has no working Maven/JitPack build,
 * and its release jar is compiled for a newer JDK than this project builds
 * with, so it can't sit on the compile classpath at all — reflection also
 * means BetterHud staying uninstalled is just a caught
 * {@code ClassNotFoundException}, not a missing dependency. Everything about
 * what the popup actually *looks like* — the dialogue box, the "SHIFT ▷"
 * continue prompt, the accept/reject buttons — has to be built in BetterHud
 * itself (two popup files named by {@code betterhud.dialogue-popup} /
 * {@code betterhud.decision-popup} in config.yml), reading the placeholders
 * {@link QuestPlaceholderExpansion} exposes, with its buttons bound to run
 * {@code /퀘스트대사다음}, {@code /퀘스트수락}, {@code /퀘스트거절} as this
 * server's console/player commands. That part can't be done from here —
 * BetterHud wasn't installed yet when this was written, so its popup config
 * schema couldn't be verified against a running instance. Every line here
 * also goes out over chat as a fallback, so the quest flow is fully usable
 * (and testable) even before BetterHud's popups are wired up.
 */
public final class QuestDialogueService {

    private static final String CUSTOM_POPUP_EVENT_CLASS = "kr.toxicity.hud.api.bukkit.event.CustomPopupEvent";

    private final QuestManager questManager;
    private final String dialoguePopup;
    private final String decisionPopup;
    private QuestPlaceholderExpansion placeholders;

    public QuestDialogueService(QuestManager questManager, String dialoguePopup, String decisionPopup) {
        this.questManager = questManager;
        this.dialoguePopup = dialoguePopup;
        this.decisionPopup = decisionPopup;
    }

    /** Set once PlaceholderAPI's expansion is registered (may never be called if PlaceholderAPI isn't installed). */
    public void setPlaceholders(QuestPlaceholderExpansion placeholders) {
        this.placeholders = placeholders;
    }

    public void start(Player player, Quest quest) {
        if (quest.dialogue().isEmpty()) {
            showDecision(player, quest, 0);
            return;
        }
        questManager.save(new QuestProgress(player.getUniqueId(), quest.id(), QuestState.DIALOGUE, 0, 0));
        showLine(player, quest, 0);
    }

    /** {@code /퀘스트대사다음} — BetterHud's popup "continue" button should run this. */
    public void advance(Player player, Quest quest) {
        var progress = questManager.progress(player.getUniqueId(), quest.id());
        if (progress.isEmpty() || progress.get().state() != QuestState.DIALOGUE) {
            return;
        }
        int nextIndex = progress.get().dialogueIndex() + 1;
        if (nextIndex >= quest.dialogue().size()) {
            showDecision(player, quest, progress.get().dialogueIndex());
            return;
        }
        questManager.save(progress.get().withDialogueIndex(nextIndex));
        showLine(player, quest, nextIndex);
    }

    private void showLine(Player player, Quest quest, int index) {
        player.sendMessage(Component.text(quest.name() + " » ", NamedTextColor.GOLD)
                .append(Component.text(quest.dialogue().get(index), NamedTextColor.WHITE)));
        if (placeholders != null) {
            placeholders.track(player.getUniqueId(), quest.id());
        }
        fireCustomPopup(player, dialoguePopup);
    }

    private void showDecision(Player player, Quest quest, int lastDialogueIndex) {
        questManager.save(new QuestProgress(player.getUniqueId(), quest.id(), QuestState.DIALOGUE, 0, lastDialogueIndex));
        player.sendMessage(Component.text("[" + quest.name() + "] 이 퀘스트를 수락하시겠습니까? ", NamedTextColor.YELLOW)
                .append(Component.text("/퀘스트수락 " + quest.name(), NamedTextColor.GREEN))
                .append(Component.text(" · ", NamedTextColor.GRAY))
                .append(Component.text("/퀘스트거절 " + quest.name(), NamedTextColor.RED)));
        if (placeholders != null) {
            placeholders.track(player.getUniqueId(), quest.id());
        }
        fireCustomPopup(player, decisionPopup);
    }

    /** Fires BetterHud's {@code CustomPopupEvent} via reflection — see the class doc for why. No-op if BetterHud isn't installed. */
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
