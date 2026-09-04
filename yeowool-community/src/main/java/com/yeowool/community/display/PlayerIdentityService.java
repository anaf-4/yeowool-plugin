package com.yeowool.community.display;

import com.yeowool.community.nickname.KoreanNicknameManager;
import com.yeowool.community.rankicon.RankIconManager;
import com.yeowool.community.title.TitleDefinition;
import com.yeowool.community.title.TitleManager;
import com.yeowool.core.api.YeowoolCoreAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Single source of truth for "what should this player's displayed identity
 * look like" (rank icon + equipped title + Korean nickname-or-real-name),
 * pushed out via {@link Player#playerListName(Component)}. On modern
 * (1.19.3+) clients the floating nametag above a player's head is derived
 * from this same tab-list display name, not their raw username — so this
 * one official Paper API call "intercepts" both the tab list and the
 * nametag at once, with no fake ArmorStand entity and no scoreboard-team
 * packet juggling. Bukkit only exposes one tab-list name per player
 * (broadcast to every viewer, not settable per-viewer without a packet
 * library), so there is deliberately no "viewer" concept here.
 * {@link com.yeowool.community.chat.ChatChannelService} reuses
 * {@link #nameFor} for chat, and {@link PlaceholderTokens}'s {@code <identity>}
 * token reuses it for the sidebar scoreboard (rendered every ~second), so
 * all three surfaces stay in sync with the tab list.
 * <p>
 * The icon+title half ("prefix") is cached per player and only recomputed
 * on {@link #refresh} (join, or an admin/self command that actually
 * changes it) — {@link RankIconManager#renderIcon} calls into ItemsAdder
 * and {@code MiniMessage} parses the title, neither of which is free to
 * redo on every single sidebar/tablist tick for every online player when
 * nothing has changed. The nickname-or-name half stays uncached (a plain
 * settings lookup, cheap either way, and it's the part chat needs in a
 * caller-chosen color rather than the cached white).
 */
public final class PlayerIdentityService {

    private final YeowoolCoreAPI core;
    private final RankIconManager rankIconManager;
    private final TitleManager titleManager;
    private final KoreanNicknameManager nicknameManager;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Map<UUID, Component> prefixCache = new ConcurrentHashMap<>();

    public PlayerIdentityService(YeowoolCoreAPI core, RankIconManager rankIconManager, TitleManager titleManager, KoreanNicknameManager nicknameManager) {
        this.core = core;
        this.rankIconManager = rankIconManager;
        this.titleManager = titleManager;
        this.nicknameManager = nicknameManager;
    }

    /** Recomputes {@code target}'s icon+title prefix, caches it, and re-broadcasts their displayed identity (tab list + nametag). */
    public void refresh(Player target) {
        prefixCache.put(target.getUniqueId(), computePrefix(target));
        target.playerListName(nameFor(target));
    }

    /** Drops the cached prefix so nothing lingers for a player who's no longer online. */
    public void clearCache(UUID uuid) {
        prefixCache.remove(uuid);
    }

    /** Same icon+title+nickname composition {@link #refresh} pushes to the tab list, exposed for chat/scoreboard rendering. */
    public Component nameFor(Player target) {
        return nameFor(target, NamedTextColor.WHITE);
    }

    /** As {@link #nameFor(Player)}, but with the name portion in {@code nameColor} — used by chat so an equipped chat-color cosmetic still applies. */
    public Component nameFor(Player target, TextColor nameColor) {
        Component prefix = prefixCache.computeIfAbsent(target.getUniqueId(), uuid -> computePrefix(target));
        var data = core.playerData().getIfLoaded(target.getUniqueId());
        String nickname = data.flatMap(nicknameManager::get).orElse(null);
        Component name = Component.text(nickname != null ? nickname : target.getName(), nameColor);
        return prefix.equals(Component.empty()) ? name : prefix.append(name);
    }

    private Component computePrefix(Player target) {
        var data = core.playerData().getIfLoaded(target.getUniqueId());
        String iconId = data.map(rankIconManager::resolveIconId).orElseGet(rankIconManager::defaultIconId);
        String titleDisplay = data.flatMap(titleManager::equippedId)
                .flatMap(titleManager::find)
                .map(TitleDefinition::display)
                .orElse(null);

        Component result = Component.empty();
        Component icon = rankIconManager.renderIcon(iconId);
        if (!icon.equals(Component.empty())) {
            result = result.append(icon).appendSpace();
        }
        if (titleDisplay != null) {
            result = result.append(miniMessage.deserialize(titleDisplay)).appendSpace();
        }
        return result;
    }
}
