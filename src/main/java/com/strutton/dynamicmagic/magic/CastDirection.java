package com.strutton.dynamicmagic.magic;

public enum CastDirection {
    LOOK("Look direction"), UP("Up"), DOWN("Down"), FORWARD("Horizontal forward"), BACKWARD("Horizontal backward");
    private final String displayName;
    CastDirection(String displayName) { this.displayName = displayName; }
    public String displayName() { return displayName; }
}
