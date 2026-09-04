package com.strutton.dynamicmagic.magic;

/** Central implementation of the magic laws. Keep gameplay numbers here, not in items. */
public final class SpellCostCalculator {
    private SpellCostCalculator() {}
    public static SpellCost calculate(SpellDefinition spell, CasterStats caster) {
        double formation = 0, upkeep = 0, release = 0, totalComplexity = 0;
        for (SpellEffect effect : spell.effects()) {
            double base = Math.pow(effect.magnitude(), 1.12)
                    * (1.0 + effect.complexity() * effect.complexity())
                    * elementMultiplier(effect.element());
            totalComplexity += effect.complexity() * (1 + Math.sqrt(effect.magnitude()) * .12);
            switch (effect.type()) {
                case MAINTAIN -> upkeep += base * 0.18;
                case REPEAT -> upkeep += base * 0.65;
                case PROJECT, IMPACT -> release += base * 0.35;
                case SUMMON_ELEMENT -> formation += base * 0.55;
                case SUMMON_ENTITY -> formation += base * 1.4;
                case CONSTRUCT -> { formation += base; upkeep += base * 0.12; }
                case DETECT -> upkeep += base * 0.22;
                case CONDITIONAL -> { formation += base * .35; upkeep += base * .12; }
                case PROGRAM -> { formation += base * .5; upkeep += base * .3; }
                default -> formation += base;
            }
        }
        double combinationTax = 1 + Math.max(0, spell.effects().size() - 3) * .07;
        formation *= combinationTax;
        upkeep *= combinationTax;
        release *= combinationTax;
        double requiredControl = Math.max(1.0, totalComplexity + spell.effects().size() * 0.4);
        double instability = Math.max(0, requiredControl - caster.control()) / requiredControl;
        double controlTax = 1.0 + instability * instability * 2.0;
        return new SpellCost(formation / caster.efficiency() * controlTax,
                upkeep / caster.efficiency() * controlTax,
                release / caster.efficiency() * controlTax, instability);
    }

    private static double elementMultiplier(Element element) {
        return switch (element) {
            case AIR, EARTH, WATER -> 1.0;
            case FIRE, ICE -> 1.1;
            case LIGHT, SHADOW -> 1.3;
            case LIGHTNING -> 1.55;
            case ARCANE -> 1.7;
            case SPACE -> 2.4;
            case TIME -> 3.5;
            case DIVINE -> 2.0;
            case METAL -> 1.35;
            case GLASS, QUICK, SCORCH, LAVA -> 1.5;
            case PLASMA, STORM -> 1.9;
            case SPIRIT, UNDEAD -> 2.25;
            case SAND -> 1.25;
            case BLOOD -> 2.2;
        };
    }
}
