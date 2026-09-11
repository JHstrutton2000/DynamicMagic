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
import net.minecraft.world.level.block.Blocks;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/** Player-specific, per-element study cooldowns. There is deliberately no global cooldown. */
public final class StudyKnowledge {
    private static final String COOLDOWNS = "DynamicMagicStudyCooldowns";
    public static final long ELEMENT_COOLDOWN_TICKS = 12_000;
    private static final String ENDERMAN_STUDY = "DynamicMagicEndermanStudy";
    private static final double ENDERMAN_BREAKTHROUGH = 64.0;
    private StudyKnowledge() {}

    public static boolean studyBlock(ServerPlayer player, ServerLevel level, BlockPos pos,
                                     Element observed, double power) {
        if (beginElements(player, Set.of(observed), level.getGameTime()).isEmpty()) return false;
        ComponentKnowledge.studyElement(player, observed, power);
        DimensionKnowledge.observeBlock(player, level, pos);
        return true;
    }

    public static boolean studyPortal(ServerPlayer player, ServerLevel level, BlockPos pos, double power) {
        if (beginElements(player, Set.of(Element.SPACE), level.getGameTime()).isEmpty()) return false;
        var state = level.getBlockState(pos);
        double resonance = Math.max(.5, power) * (state.is(Blocks.END_GATEWAY) ? 5
                : state.is(Blocks.END_PORTAL) ? 4 : state.is(Blocks.NETHER_PORTAL) ? 2 : 1.5);
        ComponentKnowledge.studyElement(player, Element.SPACE, resonance);
        DimensionKnowledge.observePortal(player, level, pos);
        player.displayClientMessage(Component.literal(String.format(java.util.Locale.ROOT,
                "Portal study: gained %.1f Space insight.", resonance))
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        return true;
    }

    public static boolean studyEntity(ServerPlayer player, LivingEntity target, Element observed, double power) {
        power *= com.strutton.dynamicmagic.compat.SoloLevelingIntegration.studyMultiplier(target);
        DragonProfile dragon = DragonIntegration.profile(target);
        if (target.getType() == EntityType.ENDERMAN) {
            if (beginElements(player, Set.of(Element.SPACE), player.serverLevel().getGameTime()).isEmpty()) return false;
            studyEnderman(player, power);
            return true;
        }
        if (!dragon.dragon()) {
            if (beginElements(player, Set.of(observed), player.serverLevel().getGameTime()).isEmpty()) return false;
            ComponentKnowledge.studyElement(player, observed, power);
            DimensionKnowledge.observeEntity(player, target);
            return true;
        }
        EnumSet<Element> available = beginElements(player, dragon.elements(), player.serverLevel().getGameTime());
        if (available.isEmpty()) return false;
        double dividedPower = power / Math.max(1, dragon.elements().size());
        for (Element element : available) ComponentKnowledge.studyElement(player, element, dividedPower);
        DimensionKnowledge.observeEntity(player, target);
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
        DimensionKnowledge.copy(from, to);
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

    public static long cooldownRemaining(ServerPlayer player, Element element) {
        return Math.max(0, player.getPersistentData().getCompound(COOLDOWNS)
                .getLong(key(element)) - player.serverLevel().getGameTime());
    }

    private static EnumSet<Element> beginElements(ServerPlayer player, Set<Element> elements, long now) {
        CompoundTag cooldowns = player.getPersistentData().getCompound(COOLDOWNS).copy();
        EnumSet<Element> available = EnumSet.noneOf(Element.class);
        long soonest = Long.MAX_VALUE;
        for (Element element : elements) {
            long readyAt = cooldowns.getLong(key(element));
            if (readyAt <= now) available.add(element);
            else soonest = Math.min(soonest, readyAt);
        }
        if (available.isEmpty()) {
            long seconds = Math.max(1, (soonest - now + 19) / 20);
            player.displayClientMessage(Component.literal("You must wait " + formatDuration(seconds)
                    + " before studying " + elementNames(elements) + " again.")
                    .withStyle(ChatFormatting.GRAY), false);
            return available;
        }
        if (cooldowns.size() > 512)
            for (String key : java.util.List.copyOf(cooldowns.getAllKeys()))
                if (cooldowns.getLong(key) <= now) cooldowns.remove(key);
        for (Element element : available) cooldowns.putLong(key(element), now + ELEMENT_COOLDOWN_TICKS);
        player.getPersistentData().put(COOLDOWNS, cooldowns);
        return available;
    }

    private static String key(Element element) { return "element_" + element.name(); }

    private static String elementNames(Set<Element> elements) {
        return elements.stream().map(Element::displayName).sorted().collect(Collectors.joining("/"));
    }

    private static String formatDuration(long seconds) {
        long minutes = seconds / 60;
        long remainder = seconds % 60;
        return minutes > 0 ? minutes + "m " + remainder + "s" : remainder + "s";
    }
}
