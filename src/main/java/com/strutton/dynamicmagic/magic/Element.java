package com.strutton.dynamicmagic.magic;
public enum Element {
    FIRE, WATER, AIR, EARTH, ICE, LIGHTNING, LIGHT, SHADOW, ARCANE, SPACE, TIME, DIVINE,
    METAL, GLASS, PLASMA, QUICK, SCORCH, LAVA, STORM, SPIRIT, UNDEAD, SAND, BLOOD;

    public String displayName() {
        if (this == AIR) return "Wind";
        String value = name().toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
