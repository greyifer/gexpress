package dev.mapselect.game;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public final class CouchSleepHandler {
	private CouchSleepHandler() {}

	public static void register() {
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (!player.isSneaking() || !isWatheCouch(world.getBlockState(hit.getBlockPos()))) {
				return ActionResult.PASS;
			}
			if (world.isClient) return ActionResult.SUCCESS;
			if (!(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.PASS;

			BlockPos headPos = hit.getBlockPos();
			Direction legsDirection = connectedCouchDirection(world, headPos);
			if (legsDirection == null) {
				serverPlayer.sendMessage(Text.literal("This couch is too short to sleep on."), true);
				return ActionResult.SUCCESS;
			}

			serverPlayer.stopRiding();
			serverPlayer.setYaw(legsDirection.asRotation());
			serverPlayer.sleep(headPos);
			return ActionResult.SUCCESS;
		});
	}

	private static Direction connectedCouchDirection(World world, BlockPos pos) {
		for (Direction direction : Direction.Type.HORIZONTAL) {
			if (isWatheCouch(world.getBlockState(pos.offset(direction)))) return direction;
		}
		return null;
	}

	private static boolean isWatheCouch(BlockState state) {
		if (state == null || state.isAir()) return false;
		Identifier id = Registries.BLOCK.getId(state.getBlock());
		return id != null && "wathe".equals(id.getNamespace()) && id.getPath().contains("couch");
	}
}
