package com.strutton.dynamicmagic.magic;

/** Low-level movement operations exposed by Minecraft's entity physics API. */
public enum PhysicsOperation {
    ADD_VELOCITY("Add velocity"), SET_VELOCITY("Set velocity"), MULTIPLY_VELOCITY("Multiply velocity"),
    REVERSE_VELOCITY("Reverse velocity"), STOP_MOTION("Stop motion"), RESET_FALL_DISTANCE("Reset fall distance"),
    DISABLE_GRAVITY("Disable gravity"), ENABLE_GRAVITY("Enable gravity");
    private final String displayName;
    PhysicsOperation(String displayName) { this.displayName = displayName; }
    public String displayName() { return displayName; }
}
