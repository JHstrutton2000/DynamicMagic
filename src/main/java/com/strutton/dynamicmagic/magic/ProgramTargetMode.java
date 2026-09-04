package com.strutton.dynamicmagic.magic;

public enum ProgramTargetMode {
    CASTER_AIM("Caster aim"), DIRECTION_TO_DETECTED("Direction to detected"), DETECTED_ENTITY("Target detected entity");
    private final String displayName;
    ProgramTargetMode(String displayName) { this.displayName = displayName; }
    public String displayName() { return displayName; }
}
