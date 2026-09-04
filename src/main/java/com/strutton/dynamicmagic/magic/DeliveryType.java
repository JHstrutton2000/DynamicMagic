package com.strutton.dynamicmagic.magic;

public enum DeliveryType {
    PROJECTILE("Projectile"), TOUCH("Touch"), SELF("Self"), CONTINUOUS("Continuous");

    private final String displayName;
    DeliveryType(String displayName) { this.displayName = displayName; }
    public String displayName() { return displayName; }
}
