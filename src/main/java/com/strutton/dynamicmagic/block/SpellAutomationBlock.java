package com.strutton.dynamicmagic.block;

import com.mojang.serialization.MapCodec;
import com.strutton.dynamicmagic.DynamicMagic;
import com.strutton.dynamicmagic.item.ManaCrystalItem;
import com.strutton.dynamicmagic.magic.CraftedSpell;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class SpellAutomationBlock extends BaseEntityBlock {
    public static final MapCodec<SpellAutomationBlock> CODEC = simpleCodec(SpellAutomationBlock::new);
    public SpellAutomationBlock(Properties properties) { super(properties); }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new SpellAutomationBlockEntity(pos, state); }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                                    Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof SpellAutomationBlockEntity node) || !(player instanceof ServerPlayer server)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (stack.getItem() instanceof ManaCrystalItem) return node.insertCrystal(server, stack) ? ItemInteractionResult.SUCCESS : ItemInteractionResult.FAIL;
        CraftedSpell spell = CraftedSpell.read(stack);
        if (spell != null) { node.setSpell(server, spell); return ItemInteractionResult.SUCCESS; }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof SpellAutomationBlockEntity node && player instanceof ServerPlayer server) {
            if (player.isShiftKeyDown()) node.removeLastCrystal(server); else node.openMenu(server); return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, DynamicMagic.SPELL_AUTOMATION_BLOCK_ENTITY.get(), SpellAutomationBlockEntity::tick);
    }
}
