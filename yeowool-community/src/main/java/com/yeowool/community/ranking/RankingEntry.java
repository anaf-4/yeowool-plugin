package com.yeowool.community.ranking;

import java.util.UUID;

/** One leaderboard row — {@code value} means different things per {@link RankingCategory} (온, 토지 레벨, 접속 분). */
public record RankingEntry(UUID uuid, String username, long value) {
}
