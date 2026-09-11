package com.strutton.dynamicmagic.magic;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Central ratio law for combinations; presets and live spells obey the same opposing-element rules. */
public final class ElementInteractions {
    private ElementInteractions() {}
    public static Map<Element, Double> effectiveForces(List<SpellInstruction> instructions, double charge) {
        EnumMap<Element, Double> forces = new EnumMap<>(Element.class);
        for (SpellInstruction instruction : instructions)
            forces.merge(instruction.element(), instruction.power() * instruction.repetitions() * charge, Double::sum);
        double fire = hot(forces), water = forces.getOrDefault(Element.WATER, 0.0);
        if (fire > 0 && water > 0) {
            double clash = Math.min(fire, water);
            scaleHot(forces, Math.max(0, fire - clash * .85) / fire);
            forces.put(Element.WATER, Math.max(0, water - clash * .30));
        }
        double earth = forces.getOrDefault(Element.EARTH, 0.0);
        water = forces.getOrDefault(Element.WATER, 0.0);
        if (earth > 0 && water > 0) {
            double bond = Math.min(earth, water);
            forces.put(Element.EARTH, earth + bond * .35);
            forces.put(Element.WATER, Math.max(0, water - bond * .25));
        }
        return Map.copyOf(forces);
    }
    private static double hot(Map<Element, Double> forces) { return forces.getOrDefault(Element.FIRE, 0.0) + forces.getOrDefault(Element.SCORCH, 0.0) + forces.getOrDefault(Element.LAVA, 0.0) + forces.getOrDefault(Element.PLASMA, 0.0); }
    private static void scaleHot(EnumMap<Element, Double> forces, double scale) { for (Element element : new Element[]{Element.FIRE, Element.SCORCH, Element.LAVA, Element.PLASMA}) if (forces.containsKey(element)) forces.put(element, forces.get(element) * scale); }
}
