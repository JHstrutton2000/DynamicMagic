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
    MANA_BREWING("Mana Brewing", "Infuse potions with your own mana; mastery reduces time and mana cost.", 40, false),
    MANA_COMBINING("Mana Combining", "Create named element presets and combine multiple elements at chosen mana ratios.", 54),
    MULTICAST("Multicast", "Bind up to four spells to one grimoire slot for 10% additional mana cost.", 72),
    TRACKING("Arcane Tracking", "Apply tracking to projectiles so they pursue a target in your aim cone.", 38),
    KI_MANA_FUSION("Ki-Mana Fusion", "Channel Eternal Cultivation qi as a separate contribution to strengthen magic.", 68, false),
    MANA_EFFICIENCY("Mana Efficiency", "All spell mana costs are reduced by 10%.", 44),
    CONCENTRATION("Concentration", "Sustained and programmed spell upkeep costs 25% less mana and qi.", 48),
    SPELL_AMPLIFICATION("Spell Amplification", "Your completed spells have 15% greater effective force.", 58),
    ARCANE_RECOVERY("Arcane Recovery", "A successful spell recovers 5% of its release mana cost.", 52),
    PROJECTILE_MASTERY("Projectile Mastery", "Projectile spells gain 20% range and acquire tracked targets more reliably.", 50),
    ELEMENTAL_SAVANT("Elemental Savant", "Gain elemental mastery 25% faster from successful spell use.", 62),
    STABLE_CHANNELING("Stable Channeling", "Reduce spell instability and miscast chance by 30%.", 56),
    QUICK_CASTING("Quick Casting", "Reach full spell charge 25% sooner.", 54),
    OVERCHANNEL("Overchannel", "Charge spells beyond their normal limit, risking health at extreme power.", 74),
    MANA_SHIELD("Mana Shield", "Spend mana to absorb 25% of incoming damage while mana remains.", 66),
    WARDING("Mystic Warding", "Reduce armor-bypassing magical harm by 15%.", 60),
    SECOND_WIND("Second Wind", "Mana regeneration accelerates while you are below one-third health.", 46),
    SPELL_CRITICAL("Spell Critical", "Damaging casts have a 10% chance to surge to 150% force.", 70);

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
