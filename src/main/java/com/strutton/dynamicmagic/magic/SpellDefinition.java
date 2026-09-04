package com.strutton.dynamicmagic.magic;
import java.util.List;
public record SpellDefinition(String name, List<SpellEffect> effects) {
    public SpellDefinition {
        effects = List.copyOf(effects);
        if (effects.isEmpty()) throw new IllegalArgumentException("A spell needs at least one effect");
    }
}
