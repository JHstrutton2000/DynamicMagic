package com.strutton.dynamicmagic.magic;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;

/** Every vanilla 1.21.1 status effect, paired with the element whose law can produce it. */
public enum PotionEffectType {
    SPEED(Element.QUICK), SLOWNESS(Element.ICE), HASTE(Element.METAL), MINING_FATIGUE(Element.EARTH),
    STRENGTH(Element.BLOOD), INSTANT_HEALTH(Element.DIVINE), INSTANT_DAMAGE(Element.UNDEAD),
    JUMP_BOOST(Element.AIR), NAUSEA(Element.SHADOW), REGENERATION(Element.DIVINE),
    RESISTANCE(Element.EARTH), FIRE_RESISTANCE(Element.WATER), WATER_BREATHING(Element.WATER),
    INVISIBILITY(Element.SHADOW), BLINDNESS(Element.SHADOW), NIGHT_VISION(Element.LIGHT),
    HUNGER(Element.BLOOD), WEAKNESS(Element.UNDEAD), POISON(Element.EARTH), WITHER(Element.UNDEAD),
    HEALTH_BOOST(Element.BLOOD), ABSORPTION(Element.DIVINE), SATURATION(Element.SPIRIT),
    GLOWING(Element.LIGHT), LEVITATION(Element.AIR), LUCK(Element.ARCANE), UNLUCK(Element.SHADOW),
    SLOW_FALLING(Element.AIR), CONDUIT_POWER(Element.WATER), DOLPHINS_GRACE(Element.WATER),
    BAD_OMEN(Element.UNDEAD), HERO_OF_THE_VILLAGE(Element.SPIRIT), DARKNESS(Element.SHADOW),
    TRIAL_OMEN(Element.ARCANE), RAID_OMEN(Element.UNDEAD), WIND_CHARGED(Element.AIR),
    WEAVING(Element.SPIRIT), OOZING(Element.WATER), INFESTED(Element.EARTH);

    private final Element element;
    PotionEffectType(Element element) { this.element = element; }
    public Element element() { return element; }
    public String displayName() {
        String text = name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
    public Holder<MobEffect> effect() { return switch (this) {
        case SPEED -> MobEffects.MOVEMENT_SPEED; case SLOWNESS -> MobEffects.MOVEMENT_SLOWDOWN;
        case HASTE -> MobEffects.DIG_SPEED; case MINING_FATIGUE -> MobEffects.DIG_SLOWDOWN;
        case STRENGTH -> MobEffects.DAMAGE_BOOST; case INSTANT_HEALTH -> MobEffects.HEAL;
        case INSTANT_DAMAGE -> MobEffects.HARM; case JUMP_BOOST -> MobEffects.JUMP;
        case NAUSEA -> MobEffects.CONFUSION; case REGENERATION -> MobEffects.REGENERATION;
        case RESISTANCE -> MobEffects.DAMAGE_RESISTANCE; case FIRE_RESISTANCE -> MobEffects.FIRE_RESISTANCE;
        case WATER_BREATHING -> MobEffects.WATER_BREATHING; case INVISIBILITY -> MobEffects.INVISIBILITY;
        case BLINDNESS -> MobEffects.BLINDNESS; case NIGHT_VISION -> MobEffects.NIGHT_VISION;
        case HUNGER -> MobEffects.HUNGER; case WEAKNESS -> MobEffects.WEAKNESS; case POISON -> MobEffects.POISON;
        case WITHER -> MobEffects.WITHER; case HEALTH_BOOST -> MobEffects.HEALTH_BOOST;
        case ABSORPTION -> MobEffects.ABSORPTION; case SATURATION -> MobEffects.SATURATION;
        case GLOWING -> MobEffects.GLOWING; case LEVITATION -> MobEffects.LEVITATION; case LUCK -> MobEffects.LUCK;
        case UNLUCK -> MobEffects.UNLUCK; case SLOW_FALLING -> MobEffects.SLOW_FALLING;
        case CONDUIT_POWER -> MobEffects.CONDUIT_POWER; case DOLPHINS_GRACE -> MobEffects.DOLPHINS_GRACE;
        case BAD_OMEN -> MobEffects.BAD_OMEN; case HERO_OF_THE_VILLAGE -> MobEffects.HERO_OF_THE_VILLAGE;
        case DARKNESS -> MobEffects.DARKNESS; case TRIAL_OMEN -> MobEffects.TRIAL_OMEN;
        case RAID_OMEN -> MobEffects.RAID_OMEN; case WIND_CHARGED -> MobEffects.WIND_CHARGED;
        case WEAVING -> MobEffects.WEAVING; case OOZING -> MobEffects.OOZING; case INFESTED -> MobEffects.INFESTED;
    }; }
}
