package com.strutton.dynamicmagic.magic;

/** Independent of delivery: every spell can be aimed or deliberately applied to its caster. */
public enum TargetMode {
    AIM("Aim"), SELF("Self");
    private final String displayName;
    TargetMode(String displayName) { this.displayName = displayName; }
    public String displayName() { return displayName; }
}
