package com.strutton.dynamicmagic.magic;

public enum ImpactType {
    DAMAGE("Damage"), EXPLODE("Explode"), IGNITE("Ignite"), FREEZE("Freeze"), PROTECT("Protect"),
    STORE_ITEM("Item storage chest"), STORE_ENTITY("Store entity"), RELEASE_STORAGE("Entity storage menu"),
    STOP_TIME("Stop time"), SPEED_TIME("Accelerate time"), SLOW_TIME("Slow time"),
    HEAL("Heal"), EXTINGUISH("Extinguish"), ROOT("Root"), CHAIN("Chain"), ILLUMINATE("Illuminate"),
    BLIND("Blind"), TELEPORT("Teleport"), CONTRACT_ENTITY("Contract entity"), SUMMON_CONTRACT("Summon contract"),
    CONJURE_ITEM("Conjure seen item"), DISPEL("Dispel effects"), PHYSICS("Physics"),
    CLEANSE("Cleanse ailments"), TRANSMUTE_BLOCK("Shape matter"), WEATHER_RAIN("Call rain"),
    WEATHER_STORM("Call storm"), SUMMON_LIGHTNING("Summon lightning"), DETECT_ORES("Detect ores"),
    STUDY("Study element"), RESURRECT("Resurrect"), ASTRAL_PROJECTION("Spirit projection"),
    DETECT_LIFE("Detect life"), FERTILITY("Awaken fertility"), PACIFY_UNDEAD("Pacify undead"),
    CORRUPT_LIFE("Corrupt life"), DETECT_UNDEAD("Detect undead"), APPLY_EFFECT("Apply potion effect"),
    DRAIN_LIFE("Drain life"), TURN_UNDEAD("Turn undead"), REANIMATE("Reanimate"),
    SUMMON_CREATURE("Summon creature"), SOUL_TRAP("Soul trap"), MIND_CALM("Calm"),
    MIND_FEAR("Fear"), MIND_FRENZY("Frenzy"), CONVERT_HEALTH_TO_MANA("Equilibrium");

    private final String displayName;
    ImpactType(String displayName) { this.displayName = displayName; }
    public String displayName() { return displayName; }
}
