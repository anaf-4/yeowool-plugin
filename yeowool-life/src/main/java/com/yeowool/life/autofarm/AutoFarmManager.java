package com.yeowool.life.autofarm;

import com.yeowool.core.api.YeowoolCoreAPI;
import com.yeowool.core.api.model.PlayerData;
import org.bukkit.entity.Player;

/**
 * Remaining 자동줍기/자동심기 charge counts, stored as ordinary
 * {@link PlayerData} statistics ({@link AutoFarmType#statKey()}) — no
 * dedicated table needed, and any module can read them the same way (e.g.
 * the scoreboard, see {@code PlaceholderTokens}). "무제한" (granted via
 * {@code -1} on the admin command) is represented as a very large sentinel
 * rather than a literal {@code -1}, since {@code -1} would otherwise read as
 * "0 charges, decremented once" the next time something consumes one.
 * <p>
 * Both counts fight over the same UI slot (the vanilla level number above
 * the hotbar), so only one is shown at a time — whichever is non-zero,
 * preferring {@link AutoFarmType#PICKUP}. The player's real level is
 * snapshotted into a {@code PlayerData} setting the moment either count
 * first becomes non-zero, and restored once both are back to zero.
 */
public final class AutoFarmManager {

    private static final long INFINITE = AutoFarmType.INFINITE;
    private static final String SAVED_LEVEL_KEY = "autofarm.saved-level";

    private final YeowoolCoreAPI core;

    public AutoFarmManager(YeowoolCoreAPI core) {
        this.core = core;
    }

    public YeowoolCoreAPI core() {
        return core;
    }

    public long remaining(PlayerData data, AutoFarmType type) {
        return data.getStatistic(type.statKey());
    }

    public boolean isInfinite(long remaining) {
        return remaining >= INFINITE;
    }

    /** {@code amount < 0} means infinite (the admin grant command's {@code -1} convention). */
    public void grant(Player player, PlayerData data, AutoFarmType type, long amount) {
        long current = data.getStatistic(type.statKey());
        if (amount < 0) {
            if (current < INFINITE) {
                data.addStatistic(type.statKey(), INFINITE - current);
            }
        } else if (!isInfinite(current)) {
            data.addStatistic(type.statKey(), amount);
        }
        refreshDisplay(player, data);
    }

    /** Consumes one charge if available; returns whether it actually had one to spend. */
    public boolean consume(Player player, PlayerData data, AutoFarmType type) {
        long current = data.getStatistic(type.statKey());
        if (current <= 0) {
            return false;
        }
        if (!isInfinite(current)) {
            data.addStatistic(type.statKey(), -1);
        }
        refreshDisplay(player, data);
        return true;
    }

    /** Re-applies (or clears) the level-number hijack for {@code player} based on their current counts — safe to call any time, including on join. */
    public void refreshDisplay(Player player, PlayerData data) {
        long pickup = remaining(data, AutoFarmType.PICKUP);
        long plant = remaining(data, AutoFarmType.PLANT);
        long shown = pickup != 0 ? pickup : plant;

        if (shown == 0) {
            String saved = data.getSetting(SAVED_LEVEL_KEY, "");
            if (!saved.isBlank()) {
                try {
                    player.setLevel(Integer.parseInt(saved));
                } catch (NumberFormatException ignored) {
                    // corrupt/unset save - leave the player's current level alone
                }
                data.setSetting(SAVED_LEVEL_KEY, "");
            }
            return;
        }

        if (data.getSetting(SAVED_LEVEL_KEY, "").isBlank()) {
            data.setSetting(SAVED_LEVEL_KEY, String.valueOf(player.getLevel()));
        }
        player.setLevel(isInfinite(shown) ? 999 : (int) Math.min(shown, Integer.MAX_VALUE));
    }
}
