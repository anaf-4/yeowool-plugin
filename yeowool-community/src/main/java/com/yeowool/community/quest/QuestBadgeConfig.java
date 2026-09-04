package com.yeowool.community.quest;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * The ascending {@code quest.badges} ladder (rookie → ... → legend) from
 * {@code config.yml} — the "총 완료 퀘스트 수" badge tier shown on
 * {@link QuestBoardGui}'s profile head and {@link QuestBadgesGui}.
 */
public final class QuestBadgeConfig {

    private final List<QuestBadge> badges;

    private QuestBadgeConfig(List<QuestBadge> badges) {
        this.badges = badges;
    }

    public static QuestBadgeConfig load(FileConfiguration config) {
        List<QuestBadge> parsed = new ArrayList<>();
        for (Map<?, ?> entry : config.getMapList("quest.badges")) {
            try {
                parsed.add(new QuestBadge(
                        entry.get("name").toString(),
                        ((Number) entry.get("threshold")).longValue(),
                        entry.get("icon").toString()));
            } catch (Exception ignored) {
                // skip malformed entry
            }
        }
        parsed.sort(Comparator.comparingLong(QuestBadge::threshold));
        if (parsed.isEmpty()) {
            parsed.add(new QuestBadge("Rookie", 0, "daily_quest:rookie_badge"));
        }
        return new QuestBadgeConfig(List.copyOf(parsed));
    }

    public List<QuestBadge> badges() {
        return badges;
    }

    /** The highest badge whose threshold is {@code <=} totalCompleted. */
    public QuestBadge currentBadge(long totalCompleted) {
        QuestBadge current = badges.get(0);
        for (QuestBadge badge : badges) {
            if (badge.threshold() <= totalCompleted) {
                current = badge;
            } else {
                break;
            }
        }
        return current;
    }

    /** Null once every badge is unlocked. */
    public QuestBadge nextBadge(long totalCompleted) {
        for (QuestBadge badge : badges) {
            if (badge.threshold() > totalCompleted) {
                return badge;
            }
        }
        return null;
    }
}
