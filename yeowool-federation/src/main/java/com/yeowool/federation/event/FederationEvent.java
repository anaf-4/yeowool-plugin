package com.yeowool.federation.event;

/** One 연합대항 row. {@code result} is the announcement text, set once by the server that finished it. */
public record FederationEvent(long id, long startsAt, long endsAt, boolean ended, Long endedAt, String result) {
}
