package com.pauljang.towerDefense.towers;

public enum TargetingMode {
    FIRST("First"),
    LAST("Last"),
    STRONG("Strong"),
    WEAK("Weak"),
    CLOSE("Close");

    private final String displayName;

    TargetingMode(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
