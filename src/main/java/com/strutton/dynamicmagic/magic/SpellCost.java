package com.strutton.dynamicmagic.magic;
public record SpellCost(double formation, double maintenancePerSecond, double release, double instability) {
    public double initialCost() { return formation + release; }
}
