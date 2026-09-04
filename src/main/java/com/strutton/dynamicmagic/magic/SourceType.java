package com.strutton.dynamicmagic.magic;

public enum SourceType {
    CREATE("Create from mana"),
    SUMMON("Summon dimension");

    private final String displayName;
    SourceType(String displayName) { this.displayName = displayName; }
    public String displayName() { return displayName; }
}
