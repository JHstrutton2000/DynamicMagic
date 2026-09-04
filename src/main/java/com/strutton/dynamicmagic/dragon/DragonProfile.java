package com.strutton.dynamicmagic.dragon;

import com.strutton.dynamicmagic.magic.Element;
import java.util.Set;

/** A mod-independent description of a dragon that Dynamic Spellcraft can reason about. */
public record DragonProfile(boolean dragon, Set<Element> elements, Set<Element> weaknesses,
                            double ageScale, String lifeStage, String identity) {
    public DragonProfile {
        elements = Set.copyOf(elements);
        weaknesses = Set.copyOf(weaknesses);
    }
    public static DragonProfile notDragon() {
        return new DragonProfile(false, Set.of(), Set.of(), 0, "none", "unknown");
    }
}
