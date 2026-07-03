package com.meranked.model;

public enum AlertSeverity {
    LOW, MEDIUM, HIGH, CRITICAL;

    public boolean isAtLeast(AlertSeverity other) {
        return this.ordinal() >= other.ordinal();
    }
}
