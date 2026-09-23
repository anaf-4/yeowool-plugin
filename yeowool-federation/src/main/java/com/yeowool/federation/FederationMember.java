package com.yeowool.federation;

import java.util.UUID;

public record FederationMember(
        UUID federationId,
        UUID landId,
        FederationRole role,
        long joinedAt
) {
}
