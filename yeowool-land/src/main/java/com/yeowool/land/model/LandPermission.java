package com.yeowool.land.model;

/**
 * Per-member permission flags a land owner can grant/revoke individually
 * (e.g. "건축만 가능", "상자만 가능") instead of every member always having
 * full access. The owner implicitly has both regardless of what's stored.
 */
public enum LandPermission {
    BUILD,
    CONTAINERS
}
