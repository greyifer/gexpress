package dev.mapselect.block;

import dev.mapselect.mixin.DisplayEntityAccessor;
import dev.mapselect.mixin.TextDisplayEntityAccessor;
import dev.mapselect.network.floatingtext.FloatingTextEditOpenPayload;
import dev.mapselect.network.floatingtext.FloatingTextEditSavePayload;
import dev.mapselect.permissions.GexpressPermissions;
import dev.mapselect.registry.MapSelectBlocks;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.Brightness;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.decoration.DisplayEntity.TextDisplayEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class FloatingTextManager {
	private static final String TAG = "gexpress_floating_label";
	private static final String KEY_PREFIX = "gexpress_floating_label_key_";
	private static final int CHUNK_RADIUS = 2;
	private static final int SYNC_INTERVAL = 10;

	private FloatingTextManager() {}

	public static void register() {
		PayloadTypeRegistry.playS2C().register(FloatingTextEditOpenPayload.ID, FloatingTextEditOpenPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(FloatingTextEditSavePayload.ID, FloatingTextEditSavePayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(FloatingTextEditSavePayload.ID,
			(payload, context) -> context.server().execute(() -> save(context.player(), payload)));
		ServerTickEvents.END_WORLD_TICK.register(FloatingTextManager::tick);
	}

	public static void openEditor(ServerPlayerEntity player, BlockPos pos) {
		if (!(player.getWorld().getBlockEntity(pos) instanceof FloatingTextBlockEntity blockEntity)) return;
		if (ServerPlayNetworking.canSend(player, FloatingTextEditOpenPayload.ID)) {
			ServerPlayNetworking.send(player, new FloatingTextEditOpenPayload(pos.toImmutable(), blockEntity.text()));
		}
	}

	private static void save(ServerPlayerEntity player, FloatingTextEditSavePayload payload) {
		if (!GexpressPermissions.canEditSetupOptions(player)) return;
		if (player.squaredDistanceTo(Vec3d.ofCenter(payload.pos())) > 12.0D * 12.0D) return;
		if (player.getWorld().getBlockEntity(payload.pos()) instanceof FloatingTextBlockEntity blockEntity) {
			blockEntity.setText(payload.text());
		}
	}

	private static void tick(ServerWorld world) {
		if (world.getTime() % SYNC_INTERVAL != 0L) return;
		Map<String, Label> desired = desiredLabels(world);
		Set<String> handled = new HashSet<>();
		for (Entity entity : world.iterateEntities()) {
			if (!(entity instanceof TextDisplayEntity display) || !entity.getCommandTags().contains(TAG)) continue;
			String key = key(entity);
			Label label = key == null ? null : desired.get(key);
			if (label == null || !handled.add(key)) {
				entity.discard();
			} else {
				update(display, label);
			}
		}
		for (Label label : desired.values()) {
			if (!handled.contains(label.key())) spawn(world, label);
		}
	}

	private static Map<String, Label> desiredLabels(ServerWorld world) {
		Map<String, Label> labels = new HashMap<>();
		Set<Long> visitedChunks = new HashSet<>();
		for (ServerPlayerEntity player : world.getPlayers()) {
			ChunkPos center = player.getChunkPos();
			for (int dz = -CHUNK_RADIUS; dz <= CHUNK_RADIUS; dz++) {
				for (int dx = -CHUNK_RADIUS; dx <= CHUNK_RADIUS; dx++) {
					int chunkX = center.x + dx;
					int chunkZ = center.z + dz;
					long packed = ChunkPos.toLong(chunkX, chunkZ);
					if (!visitedChunks.add(packed)) continue;
					var chunk = world.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
					if (!(chunk instanceof WorldChunk worldChunk)) continue;
					for (BlockEntity blockEntity : worldChunk.getBlockEntities().values()) {
						Label label = labelFor(blockEntity);
						if (label != null) labels.put(label.key(), label);
					}
				}
			}
		}
		return labels;
	}

	private static Label labelFor(BlockEntity blockEntity) {
		BlockPos pos = blockEntity.getPos();
		if (blockEntity instanceof FloatingTextBlockEntity floating
				&& blockEntity.getCachedState().isOf(MapSelectBlocks.FLOATING_TEXT)) {
			String value = floating.text();
			if (value.isBlank()) return null;
			return new Label("text_" + posKey(pos), Vec3d.ofCenter(pos), Text.literal(value), 2.0F, 1.0F);
		}
		return null;
	}

	private static void spawn(ServerWorld world, Label label) {
		TextDisplayEntity display = new TextDisplayEntity(EntityType.TEXT_DISPLAY, world);
		display.addCommandTag(TAG);
		display.addCommandTag(KEY_PREFIX + label.key());
		display.setNoGravity(true);
		display.setSilent(true);
		display.setInvulnerable(true);
		update(display, label);
		world.spawnEntity(display);
	}

	private static void update(TextDisplayEntity display, Label label) {
		display.refreshPositionAndAngles(label.pos().x, label.pos().y, label.pos().z, 0.0F, 0.0F);
		DisplayEntityAccessor base = (DisplayEntityAccessor) display;
		base.gexpress$setBillboardMode(DisplayEntity.BillboardMode.CENTER);
		base.gexpress$setViewRange(32.0F);
		base.gexpress$setDisplayWidth(label.width());
		base.gexpress$setDisplayHeight(label.height());
		base.gexpress$setShadowRadius(0.0F);
		base.gexpress$setShadowStrength(0.0F);
		base.gexpress$setBrightness(Brightness.FULL);
		base.gexpress$setTeleportDuration(1);
		TextDisplayEntityAccessor text = (TextDisplayEntityAccessor) display;
		text.gexpress$setText(label.text());
		text.gexpress$setLineWidth(240);
		text.gexpress$setTextOpacity((byte) -1);
		text.gexpress$setBackground(0x00000000);
		text.gexpress$setDisplayFlags((byte) TextDisplayEntity.SHADOW_FLAG);
	}

	private static String key(Entity entity) {
		for (String tag : entity.getCommandTags()) {
			if (tag.startsWith(KEY_PREFIX)) return tag.substring(KEY_PREFIX.length());
		}
		return null;
	}

	private static String posKey(BlockPos pos) {
		return Long.toUnsignedString(pos.asLong(), 16);
	}

	private record Label(String key, Vec3d pos, Text text, float width, float height) {}
}
