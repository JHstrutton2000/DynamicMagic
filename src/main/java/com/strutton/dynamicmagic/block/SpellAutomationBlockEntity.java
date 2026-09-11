package com.strutton.dynamicmagic.block;

import com.strutton.dynamicmagic.DynamicMagic;
import com.strutton.dynamicmagic.item.ManaCrystalItem;
import com.strutton.dynamicmagic.magic.*;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import java.util.Comparator;
import java.util.UUID;
import java.util.List;

/** Four-crystal autonomous caster. It runs the same saved spell grammar and pays entirely from crystal mana. */
public final class SpellAutomationBlockEntity extends BlockEntity {
    private final NonNullList<ItemStack> crystals = NonNullList.withSize(4, ItemStack.EMPTY);
    private CraftedSpell spell;
    private UUID owner;
    public SpellAutomationBlockEntity(BlockPos pos, BlockState state) { super(DynamicMagic.SPELL_AUTOMATION_BLOCK_ENTITY.get(), pos, state); }
    public boolean insertCrystal(ServerPlayer player, ItemStack held) {
        for (int i = 0; i < crystals.size(); i++) if (crystals.get(i).isEmpty()) { ItemStack inserted = held.copyWithCount(1); crystals.set(i, inserted); held.shrink(1); owner = player.getUUID(); setChanged(); showStatus(player); return true; }
        return false;
    }
    public void removeLastCrystal(ServerPlayer player) {
        for (int i = crystals.size() - 1; i >= 0; i--) if (!crystals.get(i).isEmpty()) { if (!player.getInventory().add(crystals.get(i))) player.drop(crystals.get(i), false); crystals.set(i, ItemStack.EMPTY); setChanged(); break; }
    }
    public void setSpell(ServerPlayer player, CraftedSpell value) { spell = value; owner = player.getUUID(); setChanged(); player.displayClientMessage(Component.literal("Spell block selected: " + value.name()).withStyle(ChatFormatting.LIGHT_PURPLE), true); }
    public void setSpellFromMemory(ServerPlayer player, String name) { CraftedSpell value = SavedSpellLibrary.find(player, name); if (value != null) setSpell(player, value); openMenu(player); }
    public void eject(ServerPlayer player, int slot) { if (slot >= 0 && slot < crystals.size() && !crystals.get(slot).isEmpty()) { if (!player.getInventory().add(crystals.get(slot))) player.drop(crystals.get(slot), false); crystals.set(slot, ItemStack.EMPTY); setChanged(); } openMenu(player); }
    public void openMenu(ServerPlayer player) { List<String> names = SavedSpellLibrary.spells(player).stream().map(CraftedSpell::name).toList(); List<String> slots = crystals.stream().map(stack -> stack.isEmpty() ? "Empty" : (int)ManaCrystalItem.charge(stack) + " mana").toList(); net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new com.strutton.dynamicmagic.network.OpenSpellNodePayload(getBlockPos(), names, spell == null ? "" : spell.name(), slots)); }
    public void showStatus(ServerPlayer player) { player.displayClientMessage(Component.literal("Spell block: " + (spell == null ? "no spell" : spell.name()) + " • " + crystals.stream().filter(s -> !s.isEmpty()).count() + "/4 crystals • " + (int)mana() + " mana").withStyle(ChatFormatting.AQUA), true); }
    public static void tick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, SpellAutomationBlockEntity node) {
        if (!(level instanceof ServerLevel server) || node.spell == null || node.owner == null || server.getGameTime() % 20 != 0) return;
        ServerPlayer owner = server.getServer().getPlayerList().getPlayer(node.owner); if (owner == null || owner.serverLevel() != server) return;
        java.util.List<SpellInstruction> operations = node.spell.programmed()
                ? SpellProgramRunner.matchingBranches(owner, node.spell, (int)server.getGameTime()).stream().flatMap(match -> match.branch().instructions().stream()).toList()
                : node.spell.allInstructions();
        if (operations.isEmpty()) return;
        LivingEntity target = server.getEntitiesOfClass(LivingEntity.class, new AABB(pos).inflate(Math.max(8, node.spell.instructions().get(0).range())), entity -> entity != owner && entity.isAlive() && entity instanceof net.minecraft.world.entity.monster.Enemy)
                .stream().min(Comparator.comparingDouble(entity -> entity.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5))).orElse(null);
        if (target == null) return;
        double cost = SpellCostCalculator.calculate(node.spell.definition(), CasterMastery.stats(owner)).release(); if (!node.consume(cost)) return;
        for (SpellInstruction instruction : operations) SpellExecutor.applyDirect(owner, target, instruction);
        server.sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANT, pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5, 12, .3, .5, .3, .04); node.setChanged();
    }
    private double mana() { return crystals.stream().mapToDouble(ManaCrystalItem::charge).sum(); }
    private boolean consume(double amount) { if (mana() + 1e-8 < amount) return false; double left = amount; for (ItemStack crystal : crystals) { left -= ManaCrystalItem.drain(crystal, left); if (left <= 1e-8) return true; } return true; }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider); if (owner != null) tag.putUUID("Owner", owner); if (spell != null) tag.put("Spell", spell.toTag());
        net.minecraft.world.ContainerHelper.saveAllItems(tag, crystals, provider);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider); owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null; spell = tag.contains("Spell") ? CraftedSpell.fromTag(tag.getCompound("Spell")) : null;
        net.minecraft.world.ContainerHelper.loadAllItems(tag, crystals, provider);
    }
}
