package com.yeowool.federation.land;

import java.util.UUID;

/** A read-only snapshot of a `yeowool-land` land, fetched via raw JDBC (no compile dependency on yeowool-land). */
public record LandInfo(
        UUID id,
        UUID ownerUuid,
        String ownerUsername,
        String landName,
        int landLevel
) {
}
