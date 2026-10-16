package dev.mapselect.block;

import com.mojang.serialization.MapCodec;
import dev.doctor4t.wathe.api.event.AllowPlayerOpenLockedDoor;
import dev.doctor4t.wathe.index.WatheItems;
import dev.doctor4t.wathe.index.WatheSounds;
import dev.mapselect.item.KeycardItem;
import dev.mapselect.registry.MapSelectBlockEntities;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.Blocks;
import net.minecraft.block.EntityShapeContext;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.EnumProperty;
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
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import org.jetbrains.annotations.Nullable;

public class LargeWoodDoorBlock extends BlockWithEntity {
	private static final MapCodec<LargeWoodDoorBlock> CODEC = createCodec(LargeWoodDoorBlock::new);
	public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
	public static final BooleanProperty OPEN = Properties.OPEN;
	public static final EnumProperty<LargeDoorPart> PART = EnumProperty.of("part", LargeDoorPart.class);

	private static final int OPEN_SLIDE_PIXELS = 14;
	private static final VoxelShape[][] CLOSED_SHAPES = createDoorShapes(false);
	private static final VoxelShape[][] OPEN_SHAPES = createDoorShapes(true);

	public LargeWoodDoorBlock(AbstractBlock.Settings settings) {
		super(settings);
		setDefaultState(stateManager.getDefaultState()
			.with(FACING, Direction.NORTH)
			.with(OPEN, false)
			.with(PART, LargeDoorPart.LOWER));
	}

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() {
		return CODEC;
	}

	@Override
	public @Nullable BlockState getPlacementState(ItemPlacementContext ctx) {
		BlockPos pos = ctx.getBlockPos();
		World world = ctx.getWorld();
		if (pos.getY() >= world.getTopY() - 2) return null;
		if (!world.getBlockState(pos.up()).canReplace(ctx) || !world.getBlockState(pos.up(2)).canReplace(ctx)) return null;
		return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
	}

	@Override
	public void onPlaced(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack itemStack) {
		if (!world.isClient) {
			world.setBlockState(pos.up(), state.with(PART, LargeDoorPart.MIDDLE), Block.NOTIFY_ALL);
			world.setBlockState(pos.up(2), state.with(PART, LargeDoorPart.UPPER), Block.NOTIFY_ALL);
		}
	}

	@Override
	public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return state.get(PART) == LargeDoorPart.LOWER ? new LargeWoodDoorBlockEntity(pos, state) : null;
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		if (state.get(PART) != LargeDoorPart.LOWER) return null;
		return world.isClient
			? validateTicker(type, MapSelectBlockEntities.LARGE_WOOD_DOOR, LargeWoodDoorBlockEntity::clientTick)
			: validateTicker(type, MapSelectBlockEntities.LARGE_WOOD_DOOR, LargeWoodDoorBlockEntity::serverTick);
	}

	@Override
	protected BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.INVISIBLE;
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return getDoorShape(state);
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return getDoorShape(state);
	}

	@Override
	public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
		if (!world.isClient && player.isCreative()) {
			removeOtherPartsWithoutDrops(world, lowerPos(state, pos), pos);
		}
		return super.onBreak(world, pos, state, player);
	}

	@Override
	protected BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState,
			WorldAccess world, BlockPos pos, BlockPos neighborPos) {
		LargeDoorPart part = state.get(PART);
		if (direction == Direction.UP && part != LargeDoorPart.UPPER && !matchesDoorPart(neighborState, part.above())) {
			return Blocks.AIR.getDefaultState();
		}
		if (direction == Direction.DOWN && part != LargeDoorPart.LOWER && !matchesDoorPart(neighborState, part.below())) {
			return Blocks.AIR.getDefaultState();
		}
		return super.getStateForNeighborUpdate(state, direction, neighborState, world, pos, neighborPos);
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		BlockPos lowerPos = lowerPos(state, pos);
		BlockState lowerState = world.getBlockState(lowerPos);
		if (!(lowerState.getBlock() instanceof LargeWoodDoorBlock) || lowerState.get(PART) != LargeDoorPart.LOWER) {
			return ActionResult.PASS;
		}
		if (!(world.getBlockEntity(lowerPos) instanceof LargeWoodDoorBlockEntity entity)) {
			return ActionResult.PASS;
		}
		if (entity.isBlasted()) return ActionResult.PASS;
		if (world.isClient) return ActionResult.SUCCESS;

		ItemStack stack = player.getMainHandStack();
		if (player instanceof ServerPlayerEntity serverPlayer
				&& KeycardItem.isKeycard(stack)
				&& KeycardItem.canAssign(serverPlayer)) {
			String keyName = KeycardItem.keyName(stack);
			KeycardItem.Tier tier = KeycardItem.tier(stack);
			if (keyName.isBlank()) {
				serverPlayer.sendMessage(Text.literal("This keycard has no access label.").formatted(Formatting.RED), true);
			} else if (tier != null) {
				KeycardItem.assignDoorForLink(serverPlayer, world, entity, keyName, tier);
			}
			return ActionResult.CONSUME;
		}

		if (player.isCreative() || AllowPlayerOpenLockedDoor.EVENT.invoker().allowOpen(player) || entity.isOpen()) {
			toggleDoor(lowerState, world, entity, lowerPos);
			return ActionResult.CONSUME;
		}
		if (entity.isJammed()) {
			playLocked(world, lowerPos);
			sendTip(player, "tip.door.jammed");
			return ActionResult.CONSUME;
		}

		String keyName = entity.getKeyName();
		if (keyName == null || keyName.isBlank()) {
			toggleDoor(lowerState, world, entity, lowerPos);
			return ActionResult.CONSUME;
		}

		if (stack.isOf(WatheItems.CROWBAR)) return ActionResult.CONSUME;
		if (KeycardItem.isKeycard(stack)) {
			playLocked(world, lowerPos);
			sendLiteralTip(player, "Keycard declined");
			return ActionResult.CONSUME;
		}

		boolean hasLockpick = stack.isOf(WatheItems.LOCKPICK);
		if (hasLockpick || keyMatches(stack, keyName)) {
			world.playSound(null, lowerPos.getX() + 0.5D, lowerPos.getY() + 1.0D, lowerPos.getZ() + 0.5D,
				hasLockpick ? WatheSounds.ITEM_LOCKPICK_DOOR : WatheSounds.ITEM_KEY_DOOR,
				SoundCategory.BLOCKS, 1.0F, 1.0F);
			toggleDoor(lowerState, world, entity, lowerPos);
			return ActionResult.CONSUME;
		}

		playLocked(world, lowerPos);
		if (KeycardItem.isKeycard(stack)) {
			sendLiteralTip(player, "Keycard declined");
		} else {
			sendTip(player, stack.isEmpty() ? "tip.door.requires_key" : "tip.door.requires_different_key");
		}
		return ActionResult.CONSUME;
	}

	public static void toggleDoor(BlockState state, World world, LargeWoodDoorBlockEntity entity, BlockPos lowerPos) {
		entity.toggle(false);
		LargeWoodDoorBlockEntity neighbor = getNeighborDoorEntity(state, world, lowerPos);
		if (neighbor != null) {
			neighbor.toggle(true);
		}
	}

	public static @Nullable LargeWoodDoorBlockEntity getNeighborDoorEntity(BlockState state, World world, BlockPos lowerPos) {
		if (state == null || world == null || !state.contains(FACING)) return null;
		Direction facing = state.get(FACING);
		LargeWoodDoorBlockEntity clockwise = getNeighborDoorEntity(state, world, lowerPos, facing.rotateYClockwise());
		if (clockwise != null) return clockwise;
		return getNeighborDoorEntity(state, world, lowerPos, facing.rotateYCounterclockwise());
	}

	private static @Nullable LargeWoodDoorBlockEntity getNeighborDoorEntity(BlockState state, World world, BlockPos lowerPos, Direction side) {
		Direction facing = state.get(FACING);
		BlockPos neighborPos = lowerPos.offset(side);
		BlockState neighborState = world.getBlockState(neighborPos);
		if (!neighborState.isOf(state.getBlock())
				|| neighborState.get(PART) != LargeDoorPart.LOWER
				|| neighborState.get(FACING) != facing.getOpposite()) {
			return null;
		}
		BlockEntity neighborEntity = world.getBlockEntity(neighborPos);
		return neighborEntity instanceof LargeWoodDoorBlockEntity door ? door : null;
	}

	private static boolean matchesDoorPart(BlockState state, LargeDoorPart part) {
		return state.getBlock() instanceof LargeWoodDoorBlock && state.get(PART) == part;
	}

	private static BlockPos lowerPos(BlockState state, BlockPos pos) {
		return pos.down(state.get(PART).offsetFromLower());
	}

	private static VoxelShape getDoorShape(BlockState state) {
		VoxelShape[][] shapes = state.get(OPEN) ? OPEN_SHAPES : CLOSED_SHAPES;
		return shapes[state.get(FACING).getHorizontal()][state.get(PART).ordinal()];
	}

	private static VoxelShape[][] createDoorShapes(boolean open) {
		VoxelShape[][] shapes = new VoxelShape[4][LargeDoorPart.values().length];
		for (Direction facing : Direction.Type.HORIZONTAL) {
			Direction slide = openSlideDirection(facing);
			double xShift = open ? slide.getOffsetX() * OPEN_SLIDE_PIXELS : 0.0D;
			double zShift = open ? slide.getOffsetZ() * OPEN_SLIDE_PIXELS : 0.0D;
			boolean xAxis = facing.getAxis() == Direction.Axis.X;
			for (LargeDoorPart part : LargeDoorPart.values()) {
				double yMin = -16.0D * part.offsetFromLower();
				double yMax = yMin + 48.0D;
				shapes[facing.getHorizontal()][part.ordinal()] = xAxis
					? createCuboidShape(7.0D + xShift, yMin, 0.0D + zShift, 9.0D + xShift, yMax, 16.0D + zShift)
					: createCuboidShape(0.0D + xShift, yMin, 7.0D + zShift, 16.0D + xShift, yMax, 9.0D + zShift);
			}
		}
		return shapes;
	}

	public static Direction openSlideDirection(Direction facing) {
		return facing.rotateYClockwise();
	}

	private static void removeOtherPartsWithoutDrops(World world, BlockPos lowerPos, BlockPos exceptPos) {
		int flags = Block.NOTIFY_LISTENERS | Block.SKIP_DROPS | Block.FORCE_STATE;
		for (LargeDoorPart part : new LargeDoorPart[] { LargeDoorPart.UPPER, LargeDoorPart.MIDDLE, LargeDoorPart.LOWER }) {
			BlockPos partPos = lowerPos.up(part.offsetFromLower());
			if (partPos.equals(exceptPos)) continue;
			BlockState partState = world.getBlockState(partPos);
			if (partState.getBlock() instanceof LargeWoodDoorBlock) {
				world.setBlockState(partPos, Blocks.AIR.getDefaultState(), flags);
			}
		}
	}

	private static boolean keyMatches(ItemStack stack, String keyName) {
		if (KeycardItem.isKeycard(stack)) return false;
		LoreComponent lore = stack.get(DataComponentTypes.LORE);
		return lore != null && !lore.lines().isEmpty() && lore.lines().getFirst().getString().equals(keyName);
	}

	private static void playLocked(World world, BlockPos pos) {
		world.playSound(null, pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D,
			WatheSounds.BLOCK_DOOR_LOCKED, SoundCategory.BLOCKS, 1.0F, 1.0F);
	}

	private static void sendTip(PlayerEntity player, String translationKey) {
		if (player instanceof ServerPlayerEntity serverPlayer) {
			serverPlayer.sendMessage(Text.translatable(translationKey), true);
		}
	}

	private static void sendLiteralTip(PlayerEntity player, String message) {
		if (player instanceof ServerPlayerEntity serverPlayer) {
			serverPlayer.sendMessage(Text.literal(message).formatted(Formatting.RED), true);
		}
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
		builder.add(FACING, OPEN, PART);
	}
}
