package com.strutton.dynamicmagic.magic;

import net.minecraft.nbt.CompoundTag;

import java.util.Locale;

/** One independently configured operation in an ordered spell recipe. */
public record SpellInstruction(
        Element element,
        ImpactType impact,
        double power,
        CastDirection direction,
        TargetMode targetMode,
        PhysicsOperation physicsOperation,
        double range,
        double radius,
        double durationSeconds,
        int repetitions,
        PotionEffectType potionEffect) {

    public SpellInstruction {
        element = element == null ? Element.ARCANE : element;
        impact = impact == null ? ImpactType.DAMAGE : impact;
        power = finite(power, 1.0, .5, 10);
        direction = direction == null ? CastDirection.LOOK : direction;
        targetMode = targetMode == null ? TargetMode.AIM : targetMode;
        physicsOperation = physicsOperation == null ? PhysicsOperation.ADD_VELOCITY : physicsOperation;
        range = finite(range, 12, 1, 64);
        radius = finite(radius, 0, 0, 12);
        durationSeconds = finite(durationSeconds, 3, .5, 60);
        repetitions = Math.max(1, Math.min(8, repetitions));
        potionEffect = potionEffect == null ? PotionEffectType.SPEED : potionEffect;
    }

    public SpellInstruction(Element element, ImpactType impact, double power, CastDirection direction,
                            TargetMode targetMode, PhysicsOperation physicsOperation, double range,
                            double radius, double durationSeconds, int repetitions) {
        this(element, impact, power, direction, targetMode, physicsOperation, range, radius,
                durationSeconds, repetitions, PotionEffectType.SPEED);
    }

    public static SpellInstruction legacy(Element element, ImpactType impact, double power,
                                           CastDirection direction, TargetMode targetMode,
                                           PhysicsOperation physicsOperation, SpellForm form,
                                           DeliveryType delivery) {
        double range = switch (delivery) {
            case TOUCH -> 3;
            case CONTINUOUS -> 10 + power * 2;
            case SELF -> 1;
            case PROJECTILE -> 8 + power * 6;
        };
        double radius = form == SpellForm.BURST || form == SpellForm.SHIELD
                ? 2.5 + power : Math.max(0, power * .45);
        return new SpellInstruction(element, impact, power, direction, targetMode, physicsOperation,
                range, radius, 2 + power * 2, 1);
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Element", element.name());
        tag.putString("Impact", impact.name());
        tag.putDouble("Power", power);
        tag.putString("Direction", direction.name());
        tag.putString("TargetMode", targetMode.name());
        tag.putString("PhysicsOperation", physicsOperation.name());
        tag.putDouble("Range", range);
        tag.putDouble("Radius", radius);
        tag.putDouble("Duration", durationSeconds);
        tag.putInt("Repetitions", repetitions);
        tag.putString("PotionEffect", potionEffect.name());
        return tag;
    }

    public static SpellInstruction fromTag(CompoundTag tag) {
        try {
            return new SpellInstruction(
                    Element.valueOf(tag.getString("Element").toUpperCase(Locale.ROOT)),
                    CraftedSpell.parseImpact(tag.getString("Impact")),
                    tag.getDouble("Power"),
                    tag.contains("Direction") ? CastDirection.valueOf(tag.getString("Direction")) : CastDirection.LOOK,
                    tag.contains("TargetMode") ? TargetMode.valueOf(tag.getString("TargetMode")) : TargetMode.AIM,
                    tag.contains("PhysicsOperation") ? PhysicsOperation.valueOf(tag.getString("PhysicsOperation")) : PhysicsOperation.ADD_VELOCITY,
                    tag.contains("Range") ? tag.getDouble("Range") : 12,
                    tag.contains("Radius") ? tag.getDouble("Radius") : 0,
                    tag.contains("Duration") ? tag.getDouble("Duration") : 3,
                    tag.contains("Repetitions") ? tag.getInt("Repetitions") : 1,
                    tag.contains("PotionEffect") ? PotionEffectType.valueOf(tag.getString("PotionEffect")) : PotionEffectType.SPEED);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public SpellInstruction withElement(Element value) { return new SpellInstruction(value, impact, power, direction, targetMode, physicsOperation, range, radius, durationSeconds, repetitions, potionEffect); }
    public SpellInstruction withImpact(ImpactType value) { return new SpellInstruction(element, value, power, direction, targetMode, physicsOperation, range, radius, durationSeconds, repetitions, potionEffect); }
    public SpellInstruction withPower(double value) { return new SpellInstruction(element, impact, value, direction, targetMode, physicsOperation, range, radius, durationSeconds, repetitions, potionEffect); }
    public SpellInstruction withDirection(CastDirection value) { return new SpellInstruction(element, impact, power, value, targetMode, physicsOperation, range, radius, durationSeconds, repetitions, potionEffect); }
    public SpellInstruction withTargetMode(TargetMode value) { return new SpellInstruction(element, impact, power, direction, value, physicsOperation, range, radius, durationSeconds, repetitions, potionEffect); }
    public SpellInstruction withPhysicsOperation(PhysicsOperation value) { return new SpellInstruction(element, impact, power, direction, targetMode, value, range, radius, durationSeconds, repetitions, potionEffect); }
    public SpellInstruction withRange(double value) { return new SpellInstruction(element, impact, power, direction, targetMode, physicsOperation, value, radius, durationSeconds, repetitions, potionEffect); }
    public SpellInstruction withRadius(double value) { return new SpellInstruction(element, impact, power, direction, targetMode, physicsOperation, range, value, durationSeconds, repetitions, potionEffect); }
    public SpellInstruction withDuration(double value) { return new SpellInstruction(element, impact, power, direction, targetMode, physicsOperation, range, radius, value, repetitions, potionEffect); }
    public SpellInstruction withRepetitions(int value) { return new SpellInstruction(element, impact, power, direction, targetMode, physicsOperation, range, radius, durationSeconds, value, potionEffect); }
    public SpellInstruction withPotionEffect(PotionEffectType value) { return new SpellInstruction(element, impact, power, direction, targetMode, physicsOperation, range, radius, durationSeconds, repetitions, value); }

    private static double finite(double value, double fallback, double minimum, double maximum) {
        return Double.isFinite(value) ? Math.max(minimum, Math.min(maximum, value)) : fallback;
    }
}
