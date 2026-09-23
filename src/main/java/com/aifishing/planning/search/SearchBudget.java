package com.aifishing.planning.search;

/**
 * Shared expansion budget for full-route and rolling-horizon search.
 * Exhausting expansions or the emergency wall-clock never waives deadline,
 * return, wind, range, or regulations.
 */
public final class SearchBudget {

    private final int maxExpansions;
    private final long deadlineNanos;
    private int used;
    private boolean reached;
    private boolean timeGuardHit;

    public SearchBudget(int maxExpansions) {
        this(maxExpansions, 0);
    }

    public SearchBudget(int maxExpansions, long maxWallClockMs) {
        this.maxExpansions = Math.max(0, maxExpansions);
        this.deadlineNanos = maxWallClockMs > 0
                ? System.nanoTime() + maxWallClockMs * 1_000_000L
                : Long.MAX_VALUE;
    }

    public boolean tryConsume() {
        if (expired()) {
            return false;
        }
        if (used >= maxExpansions) {
            reached = true;
            return false;
        }
        used++;
        if (used >= maxExpansions) {
            reached = true;
        }
        return true;
    }

    public boolean remaining() {
        return used < maxExpansions && !expired();
    }

    public int remainingCount() {
        return Math.max(0, maxExpansions - used);
    }

    public int used() {
        return used;
    }

    public int maxExpansions() {
        return maxExpansions;
    }

    public boolean reached() {
        return reached;
    }

    public boolean timeGuardHit() {
        return timeGuardHit;
    }

    public void markReached() {
        reached = true;
    }

    private boolean expired() {
        if (System.nanoTime() >= deadlineNanos) {
            timeGuardHit = true;
            return true;
        }
        return false;
    }
}
