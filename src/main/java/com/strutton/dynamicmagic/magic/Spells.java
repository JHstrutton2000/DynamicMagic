package com.strutton.dynamicmagic.magic;
import java.util.List;
public final class Spells {
    private Spells() {}
    public static SpellDefinition fireBolt(double power) {
        return new SpellDefinition("Fire Bolt", List.of(
                new SpellEffect(EffectType.CREATE, Element.FIRE, 3 * power, 0.25),
                new SpellEffect(EffectType.SHAPE, Element.FIRE, 2 * power, 0.35),
                new SpellEffect(EffectType.MAINTAIN, Element.FIRE, power, 0.15),
                new SpellEffect(EffectType.PROJECT, Element.FIRE, 2 * power, 0.4),
                new SpellEffect(EffectType.IMPACT, Element.FIRE, 3 * power, 0.3)));
    }
}
