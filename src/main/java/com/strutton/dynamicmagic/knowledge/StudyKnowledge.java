package com.strutton.dynamicmagic.knowledge;

import com.strutton.dynamicmagic.dragon.DragonIntegration;
import com.strutton.dynamicmagic.dragon.DragonProfile;
import com.strutton.dynamicmagic.dragon.DragonProgression;
import com.strutton.dynamicmagic.magic.Element;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityType;

import java.util.stream.Collectors;

/** Player-specific, per-subject study cooldowns. There is deliberately no global cooldown. */
public final class StudyKnowledge {
    private static final String COOLDOWNS = "DynamicMagicStudyCooldowns";
    private static final long SUBJECT_COOLDOWN = 1_200;
    private static final String ENDERMAN_STUDY = "DynamicMagicEndermanStudy";
    private static final double ENDERMAN_BREAKTHROUGH = 64.0;
    private StudyKnowledge() {}

    public static boolean studyBlock(ServerPlayer player, ServerLevel level, BlockPos pos,
                                     Element observed, double power) {
        String subject = "b_" + Integer.toHexString(level.dimension().location().hashCode()) + '_'
                + Long.toUnsignedString(pos.asLong(), 36) + '_' + observed.name();
        if (!begin(player, subject, level.getGameTime())) return false;
        ComponentKnowledge.studyElement(player, observed, power);
        return true;
    }

    public static boolean studyEntity(ServerPlayer player, LivingEntity target, Element observed, double power) {
        DragonProfile dragon = DragonIntegration.profile(target);
        String variation = dragon.dragon() ? dragon.lifeStage() + '_' + dragon.elements() : observed.name();
        String subject = "e_" + target.getUUID().toString().replace("-", "") + '_'
                + Integer.toHexString(variation.hashCode());
        if (!begin(player, subject, player.serverLevel().getGameTime())) return false;
        if (target.getType() == EntityType.ENDERMAN) {
            studyEnderman(player, power);
            return true;
        }
        if (!dragon.dragon()) {
            ComponentKnowledge.studyElement(player, observed, power);
            return true;
        }
        double dividedPower = power / Math.max(1, dragon.elements().size());
        for (Element element : dragon.elements()) ComponentKnowledge.studyElement(player, element, dividedPower);
        DragonProgression.study(player, dragon, power);
        String elements = dragon.elements().stream().map(Element::displayName).sorted().collect(Collectors.joining(", "));
        String weaknesses = dragon.weaknesses().stream().map(Element::displayName).sorted().collect(Collectors.joining(", "));
        player.displayClientMessage(Component.literal("Dragon study (" + dragon.lifeStage() + "): " + elements
                + ". Opposing elements: " + weaknesses + '.').withStyle(ChatFormatting.AQUA), false);
        return true;
    }

    public static void copy(ServerPlayer from, ServerPlayer to) {
        if (from.getPersistentData().contains(COOLDOWNS))
            to.getPersistentData().put(COOLDOWNS, from.getPersistentData().get(COOLDOWNS).copy());
        if (from.getPersistentData().contains(ENDERMAN_STUDY))
            to.getPersistentData().putDouble(ENDERMAN_STUDY, from.getPersistentData().getDouble(ENDERMAN_STUDY));
    }

    public static double endermanProgress(ServerPlayer player) {
        return Math.min(ENDERMAN_BREAKTHROUGH, player.getPersistentData().getDouble(ENDERMAN_STUDY));
    }

    private static void studyEnderman(ServerPlayer player, double power) {
        addEndermanInsight(player, power);
    }

    public static void addEndermanInsight(ServerPlayer player, double power) {
        double total = endermanProgress(player) + Math.max(.5, power);
        player.getPersistentData().putDouble(ENDERMAN_STUDY, Math.min(ENDERMAN_BREAKTHROUGH, total));
        com.strutton.dynamicmagic.magic.ElementMastery.practice(player, Element.SPACE, Math.max(.25, power * .25));
        if (total + 1.0e-8 >= ENDERMAN_BREAKTHROUGH) {
            boolean newElement = ElementKnowledge.learn(player, Element.SPACE);
            boolean newEffect = ComponentKnowledge.learnImpact(player, com.strutton.dynamicmagic.magic.ImpactType.TELEPORT);
            if (newElement || newEffect) player.displayClientMessage(Component.literal(
                    "Enderman breakthrough: you learned Space and the Teleport effect.")
                    .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        } else player.displayClientMessage(Component.literal(String.format(java.util.Locale.ROOT,
                "Enderman teleport study: %.1f/%.0f", total, ENDERMAN_BREAKTHROUGH))
                .withStyle(ChatFormatting.AQUA), true);
    }

    private static boolean begin(ServerPlayer player, String subject, long now) {
        CompoundTag cooldowns = player.getPersistentData().getCompound(COOLDOWNS).copy();
        if (cooldowns.getLong(subject) > now) {
            player.displayClientMessage(Component.literal("This subject has nothing new to reveal yet.")
                    .withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        if (cooldowns.size() > 512)
            for (String key : java.util.List.copyOf(cooldowns.getAllKeys()))
                if (cooldowns.getLong(key) <= now) cooldowns.remove(key);
        cooldowns.putLong(subject, now + SUBJECT_COOLDOWN);
        player.getPersistentData().put(COOLDOWNS, cooldowns);
        return true;
    }
}
