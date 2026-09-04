package com.strutton.dynamicmagic.magic;

public enum ConditionType {
    ALWAYS("Always"), ENTITY_NEARBY("Entity nearby"), HOSTILE_NEARBY("Hostile nearby"), ENTITY_IN_SIGHT("Entity in sight"),
    HEALTH_BELOW_HALF("Health below 50%"), ON_GROUND("On ground"), FALLING("Falling"), IN_WATER("In water"),
    HEALTH_BELOW_QUARTER("Health below 25%"), POISONED("Poisoned or withered"), BURNING("Burning"),
    MANA_BELOW_HALF("Mana below 50%"), HUNGER_BELOW_HALF("Hunger below 50%");
    private final String displayName;
    ConditionType(String displayName) { this.displayName = displayName; }
    public String displayName() { return displayName; }
}
