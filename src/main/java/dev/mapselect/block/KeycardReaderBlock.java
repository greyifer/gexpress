package dev.mapselect.block;

import com.mojang.serialization.MapCodec;
import dev.doctor4t.wathe.block.SmallDoorBlock;
import dev.doctor4t.wathe.block_entity.DoorBlockEntity;
import dev.doctor4t.wathe.block_entity.SmallDoorBlockEntity;
import dev.doctor4t.wathe.index.WatheSounds;
import dev.mapselect.item.KeycardItem;
import dev.mapselect.registry.MapSelectBlockEntities;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;
import org.jetbrains.annotations.Nullable;

public class KeycardReaderBlock extends BlockWithEntity {
	private static final MapCodec<KeycardReaderBlock> CODEC = createCodec(KeycardReaderBlock::new);
	public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
	private static final VoxelShape NORTH_SHAPE = Block.createCuboidShape(5.5D, 0.75D, 13.75D, 11.5D, 8.75D, 16.0D);
	private static final VoxelShape SOUTH_SHAPE = Block.createCuboidShape(4.5D, 0.75D, 0.0D, 10.5D, 8.75D, 2.25D);
	private static final VoxelShape EAST_SHAPE = Block.createCuboidShape(0.0D, 0.75D, 5.5D, 2.25D, 8.75D, 11.5D);
	private static final VoxelShape WEST_SHAPE = Block.createCuboidShape(13.75D, 0.75D, 4.5D, 16.0D, 8.75D, 10.5D);

	public KeycardReaderBlock(AbstractBlock.Settings settings) {
		super(settings);
		setDefaultState(stateManager.getDefaultState().with(FACING, Direction.NORTH));
	}

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() {
		return CODEC;
	}

	@Override
	public @Nullable BlockState getPlacementState(ItemPlacementContext ctx) {
		Direction side = ctx.getSide();
		if (side.getAxis() == Direction.Axis.Y) return null;
		BlockState state = getDefaultState().with(FACING, side);
		return state.canPlaceAt(ctx.getWorld(), ctx.getBlockPos()) ? state : null;
	}

	@Override
	public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new KeycardReaderBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		return null;
	}

	@Override
	protected BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.MODEL;
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return shapeFor(state.get(FACING));
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return shapeFor(state.get(FACING));
	}

	@Override
	protected boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
		Direction facing = state.get(FACING);
		BlockPos supportPos = pos.offset(facing.getOpposite());
		return world.getBlockState(supportPos).isSideSolidFullSquare(world, supportPos, facing);
	}

	@Override
	protected BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState,
			WorldAccess world, BlockPos pos, BlockPos neighborPos) {
		if (direction == state.get(FACING).getOpposite() && !state.canPlaceAt(world, pos)) {
			return Blocks.AIR.getDefaultState();
		}
		return super.getStateForNeighborUpdate(state, direction, neighborState, world, pos, neighborPos);
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (!(world.getBlockEntity(pos) instanceof KeycardReaderBlockEntity reader)) return ActionResult.PASS;
		if (world.isClient) return ActionResult.SUCCESS;
		if (!(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.CONSUME;

		KeycardItem.PendingDoor pendingDoor = KeycardItem.pendingDoor(serverPlayer, world);
		if (pendingDoor != null && KeycardItem.canAssign(serverPlayer)) {
			reader.link(pendingDoor.doorPos(), pendingDoor.keyName(), pendingDoor.tier());
			KeycardItem.clearPendingDoor(serverPlayer);
			serverPlayer.sendMessage(Text.literal("Keycard reader linked to " + pendingDoor.tier().displayName()
				+ " '" + pendingDoor.keyName() + "'.")
				.formatted(Formatting.AQUA), true);
			return ActionResult.CONSUME;
		}

		ItemStack stack = player.getMainHandStack();
		if (!KeycardItem.isKeycard(stack)) return ActionResult.PASS;
		String cardKeyName = KeycardItem.keyName(stack);
		if (cardKeyName.isBlank()) {
			decline(world, pos, serverPlayer, "Keycard declined");
			return ActionResult.CONSUME;
		}
		if (reader.linkedDoorPos() == null || reader.keyName().isBlank() || reader.keycardTier() == null) {
			decline(world, pos, serverPlayer, "Keycard reader is not linked");
			return ActionResult.CONSUME;
		}
		if (!cardKeyName.equals(reader.keyName()) || KeycardItem.tier(stack) != reader.keycardTier()) {
			decline(world, pos, serverPlayer, "Keycard declined");
			return ActionResult.CONSUME;
		}
		DoorToggleResult toggleResult = toggleLinkedDoor(world, reader);
		if (toggleResult != DoorToggleResult.SUCCESS) {
			if (toggleResult == DoorToggleResult.JAMMED) {
				world.playSound(null, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
					WatheSounds.BLOCK_DOOR_LOCKED, SoundCategory.BLOCKS, 1.0F, 1.0F);
				serverPlayer.sendMessage(Text.translatable("tip.door.jammed").formatted(Formatting.RED), true);
			} else {
				decline(world, pos, serverPlayer, toggleResult == DoorToggleResult.MISSING
					? "Keycard reader cannot find its door"
					: "Keycard declined");
			}
			return ActionResult.CONSUME;
		}

		world.playSound(null, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
			WatheSounds.ITEM_KEY_DOOR, SoundCategory.BLOCKS, 1.0F, 1.0F);
		serverPlayer.sendMessage(Text.literal("Keycard accepted").formatted(Formatting.GREEN), true);
		return ActionResult.CONSUME;
	}

	@Override
	protected BlockState rotate(BlockState state, BlockRotation rotation) {
		return state.with(FACING, rotation.rotate(state.get(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, BlockMirror mirror) {
		return state.rotate(mirror.getRotation(state.get(FACING)));
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	private static DoorToggleResult toggleLinkedDoor(World world, KeycardReaderBlockEntity reader) {
		DoorBlockEntity door = KeycardItem.findDoor(world, reader.linkedDoorPos());
		if (door == null || door.isBlasted()) return DoorToggleResult.MISSING;
		if (door.isJammed()) return DoorToggleResult.JAMMED;

		String doorKey = KeycardItem.sanitizeKeyName(door.getKeyName());
		if (!doorKey.isBlank() && !doorKey.equals(reader.keyName())) {
			return DoorToggleResult.DECLINED;
		}

		BlockPos doorPos = door.getPos();
		BlockState doorState = world.getBlockState(doorPos);
		if (door instanceof LargeWoodDoorBlockEntity largeDoor) {
			LargeWoodDoorBlock.toggleDoor(doorState, world, largeDoor, doorPos);
			return DoorToggleResult.SUCCESS;
		}
		if (door instanceof SmallDoorBlockEntity smallDoor) {
			SmallDoorBlock.toggleDoor(doorState, world, smallDoor, doorPos);
			return DoorToggleResult.SUCCESS;
		}
		return DoorToggleResult.MISSING;
	}

	private enum DoorToggleResult {
		SUCCESS,
		MISSING,
		JAMMED,
		DECLINED
	}

	private static void decline(World world, BlockPos pos, ServerPlayerEntity player, String message) {
		world.playSound(null, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
			WatheSounds.BLOCK_DOOR_LOCKED, SoundCategory.BLOCKS, 1.0F, 1.0F);
		player.sendMessage(Text.literal(message).formatted(Formatting.RED), true);
	}

	private static VoxelShape shapeFor(Direction facing) {
		return switch (facing) {
			case SOUTH -> SOUTH_SHAPE;
			case EAST -> EAST_SHAPE;
			case WEST -> WEST_SHAPE;
			default -> NORTH_SHAPE;
		};
	}
}
