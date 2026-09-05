package com.strutton.dynamicmagic.skill;

public enum MagicSkill {
    HUNGER_WARD("Hunger Ward", "Your hunger no longer decreases.", 18),
    MANA_WELL("Mana Well", "Your mana regenerates 75% faster.", 24),
    DIVINE_VITALITY("Divine Vitality", "Your body heals more quickly.", 30),
    SUN_WARD("Sun Ward", "Vampirism no longer burns you in sunlight.", 28),
    BLOOD_WARD("Blood Ward", "Resist blood magic and become immune to vampirism.", 36),
    DRAGON_KILLER("Dragon Killer", "Deal increased damage to dragons.", 45, false),
    DRAGON_SLAYER("Dragon Slayer", "Your experience greatly increases damage dealt to dragons.", 65, false),
    DRAGONBORN("Dragonborn", "Absorb limited elemental affinities from slain dragons.", 80, false),
    VAMPIRE_IMMUNITY("Vampire Immunity", "Vampires cannot damage or infect you.", 55),
    WEREWOLF_IMMUNITY("Werewolf Immunity", "Werewolves cannot damage or infect you.", 55),
    FIRE_RESISTANCE("Inner Flame", "Permanently resist fire and lava damage.", 32),
    WATER_BREATHING("Water Lungs", "Breathe underwater without limit.", 26),
    ELEMENTAL_ATTUNEMENT("Elemental Attunement", "Known elements replenish mana while you bathe in them.", 42),
    EXPERIENCE_HARVEST("Experience Harvest", "Creatures release 35% more experience when you defeat them.", 48),
    EFFICIENT_STUDY("Efficient Study", "Skill tomes cost 25% less experience to comprehend.", 60),
    EXPLOSION_RESISTANCE("Explosion Resistance", "Take half damage from explosions; surviving blasts develops immunity.", 38),
    EXPLOSION_IMMUNITY("Explosion Immunity", "Explosions cannot damage you.", 70, false),
    MORPHING("Morphing", "Assume acquired forms by sustaining them with mana.", 58),
    MANA_BREWING("Mana Brewing", "Infuse potions with your own mana; mastery reduces time and mana cost.", 40, false);

    private final String displayName;
    private final String description;
    private final int experienceLevelCost;
    private final boolean randomDrop;
    MagicSkill(String displayName, String description, int experienceLevelCost) {
        this(displayName, description, experienceLevelCost, true);
    }
    MagicSkill(String displayName, String description, int experienceLevelCost, boolean randomDrop) {
        this.displayName = displayName;
        this.description = description;
        this.experienceLevelCost = experienceLevelCost;
        this.randomDrop = randomDrop;
    }
    public String displayName() { return displayName; }
    public String description() { return description; }
    public int experienceLevelCost() { return experienceLevelCost; }
    public boolean randomDrop() { return randomDrop; }
}
