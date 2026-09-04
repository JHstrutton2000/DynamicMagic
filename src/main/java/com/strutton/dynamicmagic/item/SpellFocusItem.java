package com.strutton.dynamicmagic.item;

import com.strutton.dynamicmagic.magic.*;
import com.strutton.dynamicmagic.mana.Mana;
import com.strutton.dynamicmagic.knowledge.ElementKnowledge;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import java.util.List;

public final class SpellFocusItem extends Item {
    public SpellFocusItem(Properties properties) { super(properties); }
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            if (!ElementKnowledge.knows(player, Element.FIRE)) {
                serverPlayer.displayClientMessage(Component.literal("You have not learned the Fire element")
                        .withStyle(ChatFormatting.RED), true);
                return InteractionResultHolder.fail(stack);
            }
            SpellCost cost = SpellCostCalculator.calculate(Spells.fireBolt(0.5), CasterMastery.stats(serverPlayer));
            if (!Mana.consume(serverPlayer, cost.formation())) {
                serverPlayer.displayClientMessage(Component.literal("Not enough mana to form the spell")
                        .withStyle(ChatFormatting.RED), true);
                return InteractionResultHolder.fail(stack);
            }
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }
    @Override public int getUseDuration(ItemStack stack, LivingEntity entity) { return 72_000; }
    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.BOW; }
    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingTicks) {
        if (!(entity instanceof ServerPlayer player)) return;
        int usedTicks = getUseDuration(stack, entity) - remainingTicks;
        double power = powerFor(usedTicks);
        SpellCost cost = SpellCostCalculator.calculate(Spells.fireBolt(power), CasterMastery.stats(player));
        if (usedTicks % 20 == 0 && !Mana.consume(player, cost.maintenancePerSecond())) {
            player.displayClientMessage(Component.literal("Not enough mana to maintain the spell")
                    .withStyle(ChatFormatting.RED), true);
            player.stopUsingItem();
            return;
        }
        if (usedTicks >= 40 && usedTicks % 10 == 0 && Mana.consume(player, cost.release() * 0.35)) {
            castFire(player, Math.max(0.35, power * 0.35));
        }
    }
    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof ServerPlayer player)) return;
        int usedTicks = getUseDuration(stack, entity) - timeLeft;
        double power = powerFor(usedTicks);
        SpellCost cost = SpellCostCalculator.calculate(Spells.fireBolt(power), CasterMastery.stats(player));
        if (!Mana.consume(player, cost.release())) {
            player.displayClientMessage(Component.literal("Not enough mana (" + (int) Mana.get(player) + ")")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        castFire(player, power);
        CasterMastery.practice(player, Spells.fireBolt(power), cost.instability());
        ElementMastery.practice(player, Element.FIRE, .5 + power * .1);
        player.displayClientMessage(Component.literal("Mana: " + Mana.display(player)), true);
    }
    private static double powerFor(int usedTicks) {
        return Math.max(0.5, Math.min(3.0, 0.5 + usedTicks / 20.0));
    }
    private static void castFire(ServerPlayer player, double power) {
        ServerLevel level = player.serverLevel();
        HitResult hit = player.pick(8 + power * 8, 0, false);
        Vec3 start = player.getEyePosition(), end = hit.getLocation(), delta = end.subtract(start);
        int particles = Math.max(6, (int) (delta.length() * 2));
        for (int i = 1; i <= particles; i++) {
            Vec3 point = start.add(delta.scale(i / (double) particles));
            level.sendParticles(ParticleTypes.FLAME, point.x, point.y, point.z, 1, .03, .03, .03, .005);
        }
        level.explode(player, end.x, end.y, end.z, (float) (.4 + power * .45), Level.ExplosionInteraction.NONE);
        if (hit instanceof BlockHitResult blockHit) {
            BlockPos firePos = blockHit.getBlockPos().relative(blockHit.getDirection());
            if (level.getBlockState(firePos).isAir())
                level.setBlockAndUpdate(firePos, net.minecraft.world.level.block.Blocks.FIRE.defaultBlockState());
        }
        level.playSound(null, player.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, .8f, 1f);
    }
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Hold to form; release to cast").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Holding longer increases power and mana cost").withStyle(ChatFormatting.DARK_GRAY));
    }
}
