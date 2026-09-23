package com.yeowool.federation;

import java.util.UUID;

public record Federation(
        UUID id,
        String name,
        String description,
        int level,
        UUID leaderLandId,
        long createdAt
) {
    public Federation withDescription(String newDescription) {
        return new Federation(id, name, newDescription, level, leaderLandId, createdAt);
    }

    public Federation withLeaderLandId(UUID newLeaderLandId) {
        return new Federation(id, name, description, level, newLeaderLandId, createdAt);
    }
}
