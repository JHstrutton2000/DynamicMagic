package com.strutton.dynamicmagic.storage;

import com.strutton.dynamicmagic.magic.CastDirection;
import com.strutton.dynamicmagic.magic.CasterMastery;
import com.strutton.dynamicmagic.magic.CraftedSpell;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/** Personal summoning contracts. The pact difficulty is strength + aggression + spatial size. */
public final class MagicContracts {
    private static final String KEY = "DynamicMagicContracts";
    private static final int CAPACITY = 16;
    private MagicContracts() {}

    public static void contractTarget(ServerPlayer player, CraftedSpell spell, double power) {
        Vec3 start = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        double range = 8 + power * 2;
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player.serverLevel(), player, start,
                start.add(look.scale(range)), player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.25),
                e -> e instanceof LivingEntity && e != player && !(e instanceof EnderDragon), (float)(range * range));
        if (hit == null) { message(player, "No contractable entity is in your aim.", ChatFormatting.GRAY); return; }
        LivingEntity target = (LivingEntity) hit.getEntity();
        ListTag contracts = entries(player);
        if (contracts.size() >= CAPACITY) { message(player, "Your contract ledger is full.", ChatFormatting.RED); return; }
        double aggression = target instanceof Enemy ? 8 : target instanceof Mob mob && mob.getTarget() != null ? 5 : 1;
        double volume = Math.max(1, target.getBbWidth() * target.getBbWidth() * target.getBbHeight());
        double difficulty = target.getMaxHealth() / 6.0 + target.getArmorValue() * .4 + aggression + volume * 1.5;
        double authority = CasterMastery.stats(player).control() + power * 3;
        if (authority < difficulty) {
            if (target instanceof Mob mob) mob.setTarget(player);
            message(player, "The contract was rejected (control " + (int)authority + "/" + (int)Math.ceil(difficulty) + ").", ChatFormatting.RED);
            return;
        }
        CompoundTag entityData = new CompoundTag();
        if (!target.save(entityData)) return;
        entityData.remove("UUID");
        CompoundTag contract = new CompoundTag();
        contract.put("Entity", entityData);
        contract.putString("Name", target.getDisplayName().getString());
        contract.putDouble("Upkeep", Math.max(.5, difficulty * .08));
        contracts.add(contract);
        save(player, contracts);
        message(player, "Contract formed with " + target.getDisplayName().getString() + ".", ChatFormatting.GOLD);
    }

    public static boolean summon(ServerPlayer player, CastDirection ignored) {
        ListTag contracts = entries(player);
        if (contracts.isEmpty()) { message(player, "You have no entity contracts.", ChatFormatting.GRAY); return false; }
        CompoundTag contract = contracts.getCompound(contracts.size() - 1);
        ServerLevel level = player.serverLevel();
        Vec3 at = player.position().add(player.getLookAngle().scale(2.5));
        Entity summoned = EntityType.loadEntityRecursive(contract.getCompound("Entity"), level, entity -> {
            entity.moveTo(at.x, at.y + .25, at.z, player.getYRot(), entity.getXRot());
            entity.getPersistentData().putUUID("DynamicMagicContractOwner", player.getUUID());
            entity.getPersistentData().putDouble("DynamicMagicContractUpkeep", contract.getDouble("Upkeep"));
            return entity;
        });
        if (summoned == null || !level.addFreshEntity(summoned)) return false;
        message(player, "Summoned contracted " + contract.getString("Name") + ".", ChatFormatting.GOLD);
        return true;
    }

    public static void copy(ServerPlayer from, ServerPlayer to) { save(to, entries(from)); }
    private static ListTag entries(ServerPlayer player) { return player.getPersistentData().getList(KEY, Tag.TAG_COMPOUND).copy(); }
    private static void save(ServerPlayer player, ListTag value) { player.getPersistentData().put(KEY, value); }
    private static void message(ServerPlayer p, String text, ChatFormatting color) { p.displayClientMessage(Component.literal(text).withStyle(color), true); }
}
