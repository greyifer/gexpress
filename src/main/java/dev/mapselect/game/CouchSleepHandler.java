package dev.mapselect.game;

import dev.mapselect.preset.map.MapPreset;
import dev.mapselect.preset.map.PresetStorage;
import dev.mapselect.weather.MapWeatherComponent;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.io.IOException;

public final class CouchSleepHandler {
	private CouchSleepHandler() {}

	public static void register() {
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (!player.isSneaking() || !isWatheCouch(world.getBlockState(hit.getBlockPos()))) {
				return ActionResult.PASS;
			}
			if (isWatheCouchItem(player.getStackInHand(hand))) {
				return ActionResult.PASS;
			}
			if (world.isClient) return ActionResult.SUCCESS;
			if (!(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.PASS;

			if (!canUseCouchSleep(serverPlayer, world)) {
				serverPlayer.sendMessage(Text.literal("Couch sleeping is disabled on this map."), true);
				return ActionResult.SUCCESS;
			}

			BlockPos headPos = hit.getBlockPos();
			Direction connectedDirection = connectedCouchDirection(world, headPos);
			if (connectedDirection == null) {
				serverPlayer.sendMessage(Text.literal("This couch is too short to sleep on."), true);
				return ActionResult.SUCCESS;
			}

			serverPlayer.stopRiding();
			serverPlayer.setYaw(connectedDirection.getOpposite().asRotation());
			serverPlayer.trySleep(headPos).ifLeft(reason -> {
				if (reason.getMessage() != null) {
					serverPlayer.sendMessage(reason.getMessage(), true);
				}
			});
			return ActionResult.SUCCESS;
		});
	}

	private static boolean canUseCouchSleep(ServerPlayerEntity player, World world) {
		if (player.isCreative()) return true;
		if (!(world instanceof ServerWorld serverWorld)) return false;

		MapWeatherComponent weather = MapWeatherComponent.KEY.getNullable(serverWorld);
		String currentMapName = weather == null ? null : weather.getCurrentMapName();
		if (currentMapName == null || currentMapName.isBlank()) return false;

		try {
			MapPreset preset = PresetStorage.load(serverWorld.getServer(), currentMapName);
			return preset != null && preset.isCouchSleepingEnabled();
		} catch (IOException e) {
			return false;
		}
	}

	public static boolean isSleepableCouch(World world, BlockPos pos) {
		return isWatheCouch(world.getBlockState(pos)) && connectedCouchDirection(world, pos) != null;
	}

	public static Direction getCouchSleepingDirection(World world, BlockPos pos) {
		if (!isWatheCouch(world.getBlockState(pos))) return null;
		Direction connected = connectedCouchDirection(world, pos);
		if (connected != null) return connected.getOpposite();
		BlockState state = world.getBlockState(pos);
		return state.contains(HorizontalFacingBlock.FACING) ? state.get(HorizontalFacingBlock.FACING) : null;
	}

	private static Direction connectedCouchDirection(World world, BlockPos pos) {
		for (Direction direction : Direction.Type.HORIZONTAL) {
			if (isWatheCouch(world.getBlockState(pos.offset(direction)))) return direction;
		}
		return null;
	}

	public static boolean isWatheCouch(BlockState state) {
		if (state == null || state.isAir()) return false;
		Identifier id = Registries.BLOCK.getId(state.getBlock());
		return id != null && "wathe".equals(id.getNamespace()) && id.getPath().contains("couch");
	}

	private static boolean isWatheCouchItem(ItemStack stack) {
		if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) return false;
		Identifier id = Registries.BLOCK.getId(blockItem.getBlock());
		return id != null && "wathe".equals(id.getNamespace()) && id.getPath().contains("couch");
	}
}
