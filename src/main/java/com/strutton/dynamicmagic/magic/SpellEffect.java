package com.strutton.dynamicmagic.magic;

/** One composable instruction in a spell. Magnitude is intentionally unitless. */
public record SpellEffect(EffectType type, Element element, double magnitude, double complexity) {
    public SpellEffect {
        if (magnitude < 0 || complexity < 0) {
            throw new IllegalArgumentException("Spell effect values cannot be negative");
        }
    }
}
