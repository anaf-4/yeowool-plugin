package com.yeowool.federation;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Pure permission/limit logic, zero Bukkit/JDBC — kept separate from
 * {@link FederationManager} specifically so it's unit-testable (established
 * convention: only zero-dependency classes get JUnit tests in this project).
 */
public final class FederationRules {

    private FederationRules() {
    }

    public static int deputyCap(int level) {
        return level / 10;
    }

    public static boolean canApprove(FederationRole role) {
        return role == FederationRole.LEADER || role == FederationRole.DEPUTY;
    }

    /** Deputies can kick plain members but not each other or the leader; only the leader can kick a deputy. */
    public static boolean canKick(FederationRole actor, FederationRole target) {
        if (target == FederationRole.LEADER) {
            return false;
        }
        if (target == FederationRole.DEPUTY) {
            return actor == FederationRole.LEADER;
        }
        return actor == FederationRole.LEADER || actor == FederationRole.DEPUTY;
    }

    /** Who inherits leadership when the leader's land disappears: a deputy first, then whoever joined earliest. */
    public static Optional<FederationMember> pickSuccessor(List<FederationMember> remaining) {
        return remaining.stream()
                .min(Comparator.comparing((FederationMember m) -> m.role() != FederationRole.DEPUTY)
                        .thenComparingLong(FederationMember::joinedAt));
    }
}
