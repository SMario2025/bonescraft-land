package de.bonescraft.land;

/**
 * Per-claim permission level for trusted members.
 *
 * A player must BOTH:
 *  - be trusted on the claim with a sufficient TrustLevel
 *  - have the matching global "ring" permission (see config.yml)
 *
 * to perform the action.
 */
public enum TrustLevel {
    BUILD,
    BREAK,
    CONTAINER_VIEW,
    CONTAINER_TAKE;

    public boolean allowsBuild() {
        return true; // any trust level implies build access
    }

    public boolean allowsBreak() {
        return this == BREAK || this == CONTAINER_VIEW || this == CONTAINER_TAKE;
    }

    public boolean allowsContainerView() {
        return this == CONTAINER_VIEW || this == CONTAINER_TAKE;
    }

    public boolean allowsContainerTake() {
        return this == CONTAINER_TAKE;
    }

    public static TrustLevel parse(String raw) {
        if (raw == null) return BUILD;
        try {
            return TrustLevel.valueOf(raw.trim().toUpperCase());
        } catch (Exception ignored) {
            return BUILD;
        }
    }
}
