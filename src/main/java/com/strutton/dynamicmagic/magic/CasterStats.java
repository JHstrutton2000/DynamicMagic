package com.strutton.dynamicmagic.magic;
public record CasterStats(double control, double efficiency) {
    public CasterStats {
        control = Math.max(0.05, control);
        efficiency = Math.max(0.05, efficiency);
    }
}
