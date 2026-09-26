package com.yeowool.federation.event;

/** A server-wide broadcast to make on the main thread: one messages.yml key with one placeholder. */
public record Announcement(String messageKey, String placeholder, String value) {
}
