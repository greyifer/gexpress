package dev.mapselect.client.role.juggernaut;

import dev.doctor4t.wathe.block_entity.DoorBlockEntity;
import dev.doctor4t.wathe.index.WatheBlocks;
import dev.mapselect.client.game.ClientRoleRevealState;
import dev.mapselect.client.role.pelican.ClientVultureState;
import dev.mapselect.item.KeycardItem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;

/** Dark-red full-door hover outline shown only to a Juggernaut with a valid broad-face target. */
public final class ClientJuggernautDoorHover {
	private static final double RANGE_SQUARED = 5.25D * 5.25D;

	private ClientJuggernautDoorHover() {}

	public static void register() {
		WorldRenderEvents.BEFORE_DEBUG_RENDER.register(ClientJuggernautDoorHover::render);
	}

	private static void render(WorldRenderContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		DoorTarget target = target(client);
		if (target == null || context.matrixStack() == null || context.camera() == null) return;

		VertexConsumerProvider consumers = context.consumers();
		boolean flush = false;
		if (consumers == null) {
			consumers = client.getBufferBuilders().getEntityVertexConsumers();
			flush = true;
		}
		VertexConsumer lines = consumers.getBuffer(RenderLayer.getLines());
		MatrixStack matrices = context.matrixStack();
		Vec3d camera = context.camera().getPos();
		BlockPos pos = target.door().getPos();
		WorldRenderer.drawShapeOutline(matrices, lines, target.shape(),
			pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z,
			0.36F, 0.015F, 0.02F, 1.0F, true);
		if (flush && consumers instanceof VertexConsumerProvider.Immediate immediate) immediate.draw();
	}

	private static DoorTarget target(MinecraftClient client) {
		if (client == null || client.player == null || client.world == null
				|| client.currentScreen != null || ClientVultureState.isLocalStashed(client)
				|| !ClientRoleRevealState.canUseRoleAbility(client)
				|| !ClientJuggernautState.isLocalJuggernaut(client)
				|| !(client.crosshairTarget instanceof BlockHitResult hit)
				|| hit.getType() != HitResult.Type.BLOCK
				|| client.player.getEyePos().squaredDistanceTo(hit.getPos()) > RANGE_SQUARED) {
			return null;
		}
		DoorBlockEntity door = KeycardItem.findDoor(client.world, hit.getBlockPos());
		if (door == null || door.isBlasted()
				|| !door.getCachedState().isOf(WatheBlocks.SMALL_WOOD_DOOR)) return null;
		Direction facing = door.getFacing();
		if (facing == null || hit.getSide().getAxis() != facing.getAxis()) return null;

		BlockPos lower = door.getPos();
		BlockState state = client.world.getBlockState(lower);
		VoxelShape shape = state.getOutlineShape(client.world, lower, ShapeContext.of(client.player));
		return shape.isEmpty() ? null : new DoorTarget(door, shape);
	}

	private record DoorTarget(DoorBlockEntity door, VoxelShape shape) {}
}
