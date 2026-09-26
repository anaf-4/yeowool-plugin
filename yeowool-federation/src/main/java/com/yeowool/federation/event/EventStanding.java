package com.yeowool.federation.event;

import java.util.UUID;

/** A federation's activity gained since the event's snapshot. */
public record EventStanding(UUID federationId, String name, long gained) {
}
