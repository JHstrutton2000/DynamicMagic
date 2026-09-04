package com.strutton.dynamicmagic.magic;

public enum SpellForm {
    BOLT("Bolt"), BEAM("Beam"), BURST("Burst"), SHIELD("Shield"), WEAPON("Weapon"),
    RUNE("Rune"), WALL("Wall"), CLOAK("Cloak");

    private final String displayName;
    SpellForm(String displayName) { this.displayName = displayName; }
    public String displayName() { return displayName; }
}
