package dev.mapselect.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.*;
import net.minecraft.block.entity.*;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.*;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public final class SettingsPanelBlock extends BlockWithEntity {
    private static final MapCodec<SettingsPanelBlock> CODEC = createCodec(SettingsPanelBlock::new);
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    public SettingsPanelBlock(Settings settings) { super(settings); setDefaultState(stateManager.getDefaultState().with(FACING, Direction.NORTH)); }
    @Override protected MapCodec<? extends BlockWithEntity> getCodec() { return CODEC; }
    @Override public BlockState getPlacementState(ItemPlacementContext context) { return getDefaultState().with(FACING, context.getHorizontalPlayerFacing().getOpposite()); }
    @Override public BlockEntity createBlockEntity(BlockPos pos, BlockState state) { return new SettingsPanelBlockEntity(pos, state); }
    @Override protected BlockRenderType getRenderType(BlockState state) { return BlockRenderType.MODEL; }
    @Override protected void appendProperties(StateManager.Builder<Block, BlockState> builder) { builder.add(FACING); }
    @Override protected BlockState rotate(BlockState state, BlockRotation rotation) { return state.with(FACING, rotation.rotate(state.get(FACING))); }
    @Override protected BlockState mirror(BlockState state, BlockMirror mirror) { return state.rotate(mirror.getRotation(state.get(FACING))); }
    @Override public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.onPlaced(world, pos, state, placer, stack);
        if (world instanceof ServerWorld server) {
            for (Direction direction : new Direction[]{state.get(FACING).rotateYClockwise(), state.get(FACING).rotateYCounterclockwise(), Direction.UP, Direction.DOWN}) {
                BlockPos neighbor = pos.offset(direction);
                if (world.getBlockState(neighbor).isOf(this) && world.getBlockState(neighbor).get(FACING) == state.get(FACING)
                    && world.getBlockEntity(neighbor) instanceof SettingsPanelBlockEntity existing
                    && world.getBlockEntity(pos) instanceof SettingsPanelBlockEntity placed) {
                    placed.selectMap(existing.selectedMap());
                    placed.display(existing.section(), existing.offset(), existing.total(), existing.rows()); break;
                }
            }
            SettingsPanel panel = SettingsPanel.find(world, pos);
            var controller = panel == null ? null : panel.controller(world);
            if (controller != null) SettingsPanelControls.refresh(server, panel, controller.section(), controller.offset());
        }
    }
    @Override protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient && !player.hasPermissionLevel(2)) {
            player.sendMessage(net.minecraft.text.Text.literal("Only operators can use the settings panel."), true);
            return ActionResult.CONSUME;
        }
        SettingsPanel panel = SettingsPanel.find(world, pos);
        if (panel == null || hit.getSide() != panel.facing()) return ActionResult.PASS;
        double x = (panel.screenHorizontalCoordinate(hit.getPos()) - SettingsPanelLayout.MARGIN) / SettingsPanelLayout.SCALE;
        double y = (panel.height() - panel.verticalCoordinate(hit.getPos()) - SettingsPanelLayout.MARGIN) / SettingsPanelLayout.SCALE;
        int action = SettingsPanelLayout.action(x, y, SettingsPanelLayout.width(panel.width()), SettingsPanelLayout.height(panel.height()), panel.controller(world)==null?0:panel.controller(world).section());
        if (action == Integer.MIN_VALUE) return ActionResult.CONSUME;
        if (player instanceof ServerPlayerEntity serverPlayer) SettingsPanelControls.use(serverPlayer, panel, action);
        return ActionResult.SUCCESS;
    }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (world.isClient) return null;
        return validateTicker(type, dev.mapselect.registry.MapSelectBlockEntities.SETTINGS_PANEL, (w, pos, s, entity) -> {
            if (w.getTime() % 100 != Math.floorMod(pos.asLong(), 100)) return;
            SettingsPanel panel = SettingsPanel.find(w, pos);
            if (panel != null && panel.isOrigin(pos)) SettingsPanelControls.refresh((ServerWorld)w, panel, entity.section(), entity.offset());
        });
    }
}
