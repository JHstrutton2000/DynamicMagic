package com.strutton.dynamicmagic.morph;

/** Deterministic balancing rules for MorphV2 maintenance costs. */
public final class MorphManaCost {
    private MorphManaCost() {}

    public static double calculate(double maxHealth, double width, double height,
                                   boolean hostile, boolean flying, boolean boss) {
        double cost = 0.75;
        cost += Math.clamp((maxHealth - 10.0) / 20.0, 0.0, 3.0);
        double volume = Math.max(0.0, width) * Math.max(0.0, width) * Math.max(0.0, height);
        cost += Math.min(2.0, Math.max(0.0, volume - 1.0) * 0.15);
        if (hostile) cost += 0.75;
        if (flying) cost += 1.25;
        if (boss) cost += 5.0;
        return Math.round(cost * 100.0) / 100.0;
    }
}
