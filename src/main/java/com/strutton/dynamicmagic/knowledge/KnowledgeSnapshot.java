package com.strutton.dynamicmagic.knowledge;

import com.strutton.dynamicmagic.magic.*;

public record KnowledgeSnapshot(long elements, long forms, long deliveries, long impacts,
                                long directions, long conditions, boolean programming,
                                double programmingMastery, int maxProgramBranches) {
    public boolean knows(Element value) { return has(elements, value.ordinal()); }
    public boolean knows(SpellForm value) { return has(forms, value.ordinal()); }
    public boolean knows(DeliveryType value) { return has(deliveries, value.ordinal()); }
    public boolean knows(ImpactType value) { return has(impacts, value.ordinal()); }
    public boolean knows(CastDirection value) { return has(directions, value.ordinal()); }
    public boolean knows(ConditionType value) { return has(conditions, value.ordinal()); }
    public boolean knows(ProgramTargetMode value) { return switch (value) {
        case CASTER_AIM -> programming;
        case DIRECTION_TO_DETECTED -> maxProgramBranches >= 2;
        case DETECTED_ENTITY -> maxProgramBranches >= 3;
    }; }
    public boolean canSummonDimension() { return knows(Element.SPACE); }
    private static boolean has(long mask, int ordinal) { return (mask & (1L << ordinal)) != 0; }
}
