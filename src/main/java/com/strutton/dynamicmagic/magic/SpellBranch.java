package com.strutton.dynamicmagic.magic;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** One independently scheduled IF branch and its ordered THEN operation chain. */
public record SpellBranch(
        ConditionType condition,
        int intervalTicks,
        double detectionRange,
        ProgramTargetMode targetMode,
        List<SpellInstruction> instructions) {

    public SpellBranch {
        condition = condition == null ? ConditionType.ALWAYS : condition;
        intervalTicks = Math.max(2, Math.min(200, intervalTicks));
        detectionRange = Double.isFinite(detectionRange) ? Math.max(1, Math.min(32, detectionRange)) : 8;
        targetMode = targetMode == null ? ProgramTargetMode.CASTER_AIM : targetMode;
        if (instructions == null || instructions.isEmpty()) throw new IllegalArgumentException("A branch needs an operation");
        instructions = List.copyOf(instructions.subList(0, Math.min(8, instructions.size())));
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Condition", condition.name());
        tag.putInt("Interval", intervalTicks);
        tag.putDouble("DetectionRange", detectionRange);
        tag.putString("TargetMode", targetMode.name());
        ListTag operations = new ListTag();
        for (SpellInstruction instruction : instructions) operations.add(instruction.toTag());
        tag.put("Instructions", operations);
        return tag;
    }

    public static SpellBranch fromTag(CompoundTag tag) {
        try {
            List<SpellInstruction> instructions = new ArrayList<>();
            if (tag.contains("Instructions", Tag.TAG_LIST)) {
                ListTag operations = tag.getList("Instructions", Tag.TAG_COMPOUND);
                for (int i = 0; i < Math.min(8, operations.size()); i++) {
                    SpellInstruction instruction = SpellInstruction.fromTag(operations.getCompound(i));
                    if (instruction != null) instructions.add(instruction);
                }
            }
            if (instructions.isEmpty()) return null;
            return new SpellBranch(
                    ConditionType.valueOf(tag.getString("Condition").toUpperCase(Locale.ROOT)),
                    tag.contains("Interval") ? tag.getInt("Interval") : 20,
                    tag.contains("DetectionRange") ? tag.getDouble("DetectionRange") : 8,
                    tag.contains("TargetMode") ? ProgramTargetMode.valueOf(tag.getString("TargetMode")) : ProgramTargetMode.CASTER_AIM,
                    instructions);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
