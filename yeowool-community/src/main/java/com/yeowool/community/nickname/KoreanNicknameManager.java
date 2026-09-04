package com.yeowool.community.nickname;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.model.PlayerData;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Stores each player's chosen Korean nickname the same way
 * {@code TitleManager}/{@code CosmeticManager}/{@code RankIconManager} store
 * their per-player state — a single {@link PlayerData} setting, no separate
 * table needed. Only holds/validates the nickname itself; applying it to
 * chat/tab-list/nametag is {@code PlayerIdentityService}'s job, and
 * redeeming the voucher item is {@code NicknameVoucherListener}'s job.
 */
public final class KoreanNicknameManager {

    private static final String NICKNAME_SETTING = "nickname.korean";
    private static final Pattern KOREAN_NICKNAME = Pattern.compile("^[가-힣]{2,8}$");

    private final YeowoolCoreAPI core;

    public KoreanNicknameManager(YeowoolCoreAPI core) {
        this.core = core;
    }

    public boolean isValidFormat(String nickname) {
        return nickname != null && KOREAN_NICKNAME.matcher(nickname).matches();
    }

    public Optional<String> get(PlayerData data) {
        String nickname = data.getSetting(NICKNAME_SETTING, "");
        return nickname.isBlank() ? Optional.empty() : Optional.of(nickname);
    }

    public void set(Player player, String nickname) {
        core.playerData().getOnline(player.getUniqueId()).setSetting(NICKNAME_SETTING, nickname);
    }

    public void clear(Player player) {
        core.playerData().getOnline(player.getUniqueId()).setSetting(NICKNAME_SETTING, "");
    }
}
