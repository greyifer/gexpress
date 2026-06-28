package dev.mapselect.tutorial;

import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.mapselect.item.TutorialWeaponItem;
import dev.mapselect.entity.TutorialPassengerEntity;
import dev.mapselect.network.tutorial.TutorialControlPayload;
import dev.mapselect.network.tutorial.TutorialStatePayload;
import dev.mapselect.permissions.GexpressPermissions;
import dev.mapselect.registry.MapSelectItems;
import dev.mapselect.registry.MapSelectEntities;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.Identifier;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class TutorialManager {
	private static final int PICKUP = 0;
	private static final int SHOOT_INNOCENT = 1;
	private static final int SHOOT_EVIL = 2;
	private static final int TRANSITION = 3;
	private static final int KNIFE = 4;
	private static final int COMPLETE = 5;
	private static final String ENTITY_TAG = "gexpress_tutorial_entity";
	private static final Identifier ROOM_TEMPLATE = Identifier.of("gexpress", "tutorial_room");
	private static final Vec3i ROOM_SIZE = new Vec3i(11, 7, 34);
	private static final Map<UUID, Session> SESSIONS = new HashMap<>();

	private TutorialManager() {}

	public static void register() {
		PayloadTypeRegistry.playC2S().register(TutorialControlPayload.ID, TutorialControlPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(TutorialStatePayload.ID, TutorialStatePayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(TutorialControlPayload.ID, (payload, context) ->
			context.server().execute(() -> {
				if (payload.action() == TutorialControlPayload.START) start(context.player(), false);
				else if (payload.action() == TutorialControlPayload.EXIT) finish(context.player(), false);
				else if (payload.action() == TutorialControlPayload.EDIT_ROOM
						&& GexpressPermissions.canUseMapTools(context.player())) {
					start(context.player(), true);
				} else if (payload.action() == TutorialControlPayload.SAVE_ROOM
						&& GexpressPermissions.canUseMapTools(context.player())) {
					saveRoom(context.player());
				}
			}));
		ServerTickEvents.END_WORLD_TICK.register(TutorialManager::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> cleanupDisconnected(handler.player, server));
		GameEvents.ON_FINISH_INITIALIZE.register((world, game) -> {
			if (world instanceof ServerWorld serverWorld) cancelInWorld(serverWorld);
		});
	}

	public static void useWeapon(ServerPlayerEntity player, TutorialWeaponItem.Kind kind) {
		Session session = player == null ? null : SESSIONS.get(player.getUuid());
		if (session == null || player.getServerWorld() != session.tutorialWorld) return;
		Entity target = lookedAtTarget(player, session, kind == TutorialWeaponItem.Kind.KNIFE ? 3.5D : 18.0D);
		if (target == null) {
			player.sendMessage(Text.literal("Aim at the highlighted training passenger.").formatted(Formatting.GRAY), true);
			return;
		}
		if (session.stage == SHOOT_INNOCENT && kind == TutorialWeaponItem.Kind.REVOLVER) {
			target.discard();
			player.getServerWorld().playSound(null, player.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(),
				SoundCategory.PLAYERS, 0.35F, 1.5F);
			player.sendMessage(Text.literal("An innocent death drops the revolver and exposes your mistake.")
				.formatted(Formatting.RED), false);
			session.stage = SHOOT_EVIL;
			spawnTarget(session, false, session.layout.evilTarget());
			sendState(player, session, "Now shoot the hostile passenger with the Training Revolver.", 0, false);
			return;
		}
		if (session.stage == SHOOT_EVIL && kind == TutorialWeaponItem.Kind.REVOLVER) {
			target.discard();
			player.getServerWorld().playSound(null, player.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(),
				SoundCategory.PLAYERS, 0.35F, 0.9F);
			session.stage = TRANSITION;
			session.nextActionAt = session.tutorialWorld.getTime() + 20L;
			sendState(player, session, "Correct. Moving to close-quarters training...", 40, false);
			return;
		}
		if (session.stage == KNIFE && kind == TutorialWeaponItem.Kind.KNIFE) {
			target.discard();
			player.getServerWorld().playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_STRONG,
				SoundCategory.PLAYERS, 0.8F, 1.0F);
			session.stage = COMPLETE;
			session.nextActionAt = session.tutorialWorld.getTime() + 50L;
			sendState(player, session, "Training complete. Returning you to the server...", 40, true);
		}
	}

	private static void start(ServerPlayerEntity player, boolean editing) {
		if (player == null || SESSIONS.containsKey(player.getUuid())) return;
		ServerWorld world = player.getServerWorld();
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
		if (game != null && game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE) {
			player.sendMessage(Text.literal("The tutorial is only available between rounds.").formatted(Formatting.RED), false);
			return;
		}

		BlockPos base = chooseBase(world, player.getUuid());
		Box arena = new Box(base.getX() - 5, base.getY(), base.getZ(),
			base.getX() + 6, base.getY() + 7, base.getZ() + 34);
		Session session = new Session(player, world, base, arena);
		session.editing = editing;
		SESSIONS.put(player.getUuid(), session);
		buildArena(session);
		preparePlayer(player, session);
		if (editing) {
			player.changeGameMode(GameMode.CREATIVE);
			player.getInventory().clear();
			player.playerScreenHandler.syncState();
			sendState(player, session,
				"Axiom room edit: move the marker blocks, reshape the room, then use Dev > Tutorial Room Editor > Save.",
				40, false);
			return;
		}
		session.layout.clearMarkers(world);
		spawnRevolver(session);
		sendState(player, session, "Walk forward and pick up the Training Revolver.", 40, false);
	}

	private static void tick(ServerWorld world) {
		if (SESSIONS.isEmpty()) return;
		long now = world.getTime();
		for (Session session : List.copyOf(SESSIONS.values())) {
			if (session.tutorialWorld != world) continue;
			ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(session.playerId);
			if (player == null) continue;
			if (session.editing) continue;
			if (session.stage == PICKUP && hasItem(player, MapSelectItems.TUTORIAL_REVOLVER)) {
				session.stage = SHOOT_INNOCENT;
				spawnTarget(session, true, session.layout.innocentTarget());
				sendState(player, session, "Right-click the innocent passenger with the Training Revolver.", 0, false);
			} else if (session.stage == TRANSITION && now >= session.nextActionAt) {
				beginKnifeRoom(player, session);
			} else if (session.stage == COMPLETE && now >= session.nextActionAt) {
				finish(player, true);
			}
		}
	}

	private static void beginKnifeRoom(ServerPlayerEntity player, Session session) {
		session.stage = KNIFE;
		player.getInventory().clear();
		player.getInventory().insertStack(MapSelectItems.TUTORIAL_KNIFE.getDefaultStack());
		player.getInventory().selectedSlot = 0;
		player.playerScreenHandler.syncState();
		BlockPos start = session.layout.knifeStart();
		player.teleport(session.tutorialWorld, start.getX() + 0.5D, start.getY(), start.getZ() + 0.5D,
			0.0F, 0.0F);
		spawnTarget(session, false, session.layout.knifeTarget());
		sendState(player, session, "Get close and right-click the passenger with the Training Knife.", 40, false);
	}

	private static void preparePlayer(ServerPlayerEntity player, Session session) {
		player.stopRiding();
		player.closeHandledScreen();
		player.getInventory().clear();
		player.getInventory().markDirty();
		player.changeGameMode(GameMode.ADVENTURE);
		BlockPos start = session.layout.playerStart();
		player.teleport(session.tutorialWorld, start.getX() + 0.5D, start.getY(), start.getZ() + 0.5D,
			0.0F, 0.0F);
		player.setVelocity(Vec3d.ZERO);
		player.velocityModified = true;
		player.setHealth(player.getMaxHealth());
		player.setFireTicks(0);
		player.fallDistance = 0.0F;
	}

	private static void buildArena(Session session) {
		ServerWorld world = session.tutorialWorld;
		BlockPos base = session.base;
		for (int x = -5; x <= 5; x++) {
			for (int z = 0; z <= 33; z++) {
				for (int y = 0; y <= 6; y++) {
					BlockPos pos = base.add(x, y, z);
					session.originals.put(pos.toImmutable(), world.getBlockState(pos));
				}
			}
		}

		BlockPos minimum = roomMinimum(base);
		StructureTemplate template = world.getStructureTemplateManager().getTemplate(ROOM_TEMPLATE).orElse(null);
		if (template != null && template.getSize().getX() > 0) {
			template.place(world, minimum, minimum, new StructurePlacementData(), world.getRandom(), Block.NOTIFY_ALL);
		} else {
			buildDefaultArena(world, base);
		}
		session.layout = RoomLayout.scan(world, base);
		if (session.editing) session.layout.showMarkers(world);
	}

	private static void buildDefaultArena(ServerWorld world, BlockPos base) {
		for (int x = -5; x <= 5; x++) {
			for (int z = 0; z <= 33; z++) {
				for (int y = 0; y <= 6; y++) {
					BlockPos pos = base.add(x, y, z);
					BlockState state = Blocks.AIR.getDefaultState();
					if (y == 0) {
						state = Math.abs(x) <= 1
							? Blocks.POLISHED_DEEPSLATE.getDefaultState()
							: Blocks.POLISHED_ANDESITE.getDefaultState();
					} else if (z == 0 || z == 33 || x == -5 || x == 5) {
						state = Blocks.BRICKS.getDefaultState();
					} else if (y == 6) {
						state = z % 6 == 0
							? Blocks.POLISHED_DEEPSLATE.getDefaultState()
							: Blocks.DARK_OAK_PLANKS.getDefaultState();
					}
					if ((x == -5 || x == 5) && y >= 2 && y <= 4 && z % 6 >= 2 && z % 6 <= 4) {
						state = Blocks.TINTED_GLASS.getDefaultState();
					}
					if (y == 6 && x == 0 && z % 6 == 3) state = Blocks.SEA_LANTERN.getDefaultState();
					world.setBlockState(pos, state, Block.NOTIFY_ALL);
				}
			}
		}
		for (int y = 1; y <= 5; y++) {
			world.setBlockState(base.add(-4, y, 18), Blocks.POLISHED_DEEPSLATE.getDefaultState(), Block.NOTIFY_ALL);
			world.setBlockState(base.add(4, y, 18), Blocks.POLISHED_DEEPSLATE.getDefaultState(), Block.NOTIFY_ALL);
		}
		for (int x = -4; x <= 4; x++) {
			world.setBlockState(base.add(x, 5, 18), Blocks.POLISHED_DEEPSLATE.getDefaultState(), Block.NOTIFY_ALL);
		}
		world.setBlockState(base.add(0, 0, 2), Blocks.EMERALD_BLOCK.getDefaultState(), Block.NOTIFY_ALL);
		world.setBlockState(base.add(0, 0, 6), Blocks.GOLD_BLOCK.getDefaultState(), Block.NOTIFY_ALL);
		world.setBlockState(base.add(0, 0, 11), Blocks.LIGHT_BLUE_CONCRETE.getDefaultState(), Block.NOTIFY_ALL);
		world.setBlockState(base.add(0, 0, 14), Blocks.RED_CONCRETE.getDefaultState(), Block.NOTIFY_ALL);
		world.setBlockState(base.add(0, 0, 21), Blocks.ORANGE_CONCRETE.getDefaultState(), Block.NOTIFY_ALL);
		world.setBlockState(base.add(0, 0, 27), Blocks.BLACK_CONCRETE.getDefaultState(), Block.NOTIFY_ALL);
	}

	private static void spawnRevolver(Session session) {
		BlockPos pos = session.layout.revolver();
		ItemEntity item = new ItemEntity(session.tutorialWorld, pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D,
			MapSelectItems.TUTORIAL_REVOLVER.getDefaultStack());
		item.setCustomName(Text.literal("Training Revolver").formatted(Formatting.GOLD));
		item.setCustomNameVisible(true);
		item.setPickupDelay(10);
		item.addCommandTag(ENTITY_TAG);
		session.tutorialWorld.spawnEntity(item);
	}

	private static void spawnTarget(Session session, boolean innocent, BlockPos pos) {
		TutorialPassengerEntity target = new TutorialPassengerEntity(MapSelectEntities.TUTORIAL_PASSENGER,
			session.tutorialWorld);
		target.refreshPositionAndAngles(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 180.0F, 0.0F);
		target.setNoGravity(true);
		target.setCustomName(Text.literal(innocent ? "Innocent Passenger" : "Hostile Passenger")
			.formatted(innocent ? Formatting.AQUA : Formatting.RED));
		target.setCustomNameVisible(true);
		target.addCommandTag(ENTITY_TAG);
		target.addCommandTag(session.targetTag);
		session.tutorialWorld.spawnEntity(target);
	}

	private static Entity lookedAtTarget(ServerPlayerEntity player, Session session, double range) {
		Vec3d start = player.getEyePos();
		Vec3d direction = player.getRotationVec(1.0F).normalize();
		Vec3d end = start.add(direction.multiply(range));
		Box search = player.getBoundingBox().stretch(direction.multiply(range)).expand(1.0D);
		EntityHitResult hit = ProjectileUtil.raycast(player, start, end, search,
			entity -> entity.getCommandTags().contains(session.targetTag), range * range);
		return hit == null ? null : hit.getEntity();
	}

	private static boolean hasItem(ServerPlayerEntity player, net.minecraft.item.Item item) {
		for (int slot = 0; slot < player.getInventory().size(); slot++) {
			if (player.getInventory().getStack(slot).isOf(item)) return true;
		}
		return false;
	}

	private static void finish(ServerPlayerEntity player, boolean completed) {
		Session session = player == null ? null : SESSIONS.remove(player.getUuid());
		if (session == null) return;
		restoreArena(session);
		restorePlayer(player, session);
		send(player, TutorialStatePayload.clear(completed));
	}

	private static void restorePlayer(ServerPlayerEntity player, Session session) {
		ServerWorld origin = player.getServer().getWorld(session.originWorld);
		if (origin == null) origin = player.getServerWorld();
		player.changeGameMode(session.gameMode);
		player.teleport(origin, session.originPos.x, session.originPos.y, session.originPos.z,
			session.originYaw, session.originPitch);
		player.getInventory().clear();
		player.getInventory().readNbt((NbtList) session.inventory.copy());
		player.getInventory().selectedSlot = session.selectedSlot;
		player.getInventory().markDirty();
		player.getHungerManager().readNbt(session.hunger.copy());
		player.setHealth(Math.min(player.getMaxHealth(), Math.max(1.0F, session.health)));
		player.setVelocity(Vec3d.ZERO);
		player.velocityModified = true;
		player.playerScreenHandler.syncState();
	}

	private static void restoreArena(Session session) {
		for (Entity entity : session.tutorialWorld.getOtherEntities(null, session.arena,
			entity -> entity.getCommandTags().contains(ENTITY_TAG))) entity.discard();
		for (Map.Entry<BlockPos, BlockState> entry : session.originals.entrySet()) {
			session.tutorialWorld.setBlockState(entry.getKey(), entry.getValue(), Block.NOTIFY_ALL);
		}
	}

	private static void saveRoom(ServerPlayerEntity player) {
		Session session = player == null ? null : SESSIONS.get(player.getUuid());
		if (session == null || !session.editing) {
			if (player != null) player.sendMessage(Text.literal("Enter the tutorial room editor before saving.")
				.formatted(Formatting.RED), false);
			return;
		}
		StructureTemplate template = session.tutorialWorld.getStructureTemplateManager().getTemplateOrBlank(ROOM_TEMPLATE);
		template.saveFromWorld(session.tutorialWorld, roomMinimum(session.base), ROOM_SIZE, false, Blocks.STRUCTURE_VOID);
		boolean saved = session.tutorialWorld.getStructureTemplateManager().saveTemplate(ROOM_TEMPLATE);
		player.sendMessage(Text.literal(saved
			? "Tutorial room saved. New tutorial sessions will use this layout."
			: "The tutorial room could not be saved.").formatted(saved ? Formatting.GREEN : Formatting.RED), false);
	}

	private static void cleanupDisconnected(ServerPlayerEntity player, MinecraftServer server) {
		Session session = SESSIONS.remove(player.getUuid());
		if (session != null) {
			restoreArena(session);
			restorePlayer(player, session);
		}
	}

	private static void cancelInWorld(ServerWorld world) {
		for (Session session : List.copyOf(SESSIONS.values())) {
			if (session.tutorialWorld != world) continue;
			ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(session.playerId);
			if (player != null) finish(player, false);
			else {
				SESSIONS.remove(session.playerId);
				restoreArena(session);
			}
		}
	}

	private static BlockPos chooseBase(ServerWorld world, UUID playerId) {
		double borderRoom = Math.max(0.0D, world.getWorldBorder().getSize() / 2.0D - 96.0D);
		int spread = (int) Math.min(100000.0D, borderRoom * 0.65D);
		int signX = (playerId.hashCode() & 1) == 0 ? 1 : -1;
		int signZ = (playerId.hashCode() & 2) == 0 ? 1 : -1;
		int lane = Math.floorMod(playerId.hashCode(), 8) * 40;
		int x = (int) Math.floor(world.getWorldBorder().getCenterX()) + signX * Math.max(0, spread - lane);
		int z = (int) Math.floor(world.getWorldBorder().getCenterZ()) + signZ * Math.max(0, spread - lane);
		int y = Math.min(world.getTopY() - 8, Math.max(world.getBottomY() + 16, 240));
		return new BlockPos(x, y, z);
	}

	private static BlockPos roomMinimum(BlockPos base) {
		return base.add(-5, 0, 0);
	}

	private static void sendState(ServerPlayerEntity player, Session session, String instruction,
			int fadeTicks, boolean completed) {
		send(player, new TutorialStatePayload(true, session.stage, instruction, fadeTicks, completed));
	}

	private static void send(ServerPlayerEntity player, TutorialStatePayload payload) {
		if (player != null && ServerPlayNetworking.canSend(player, TutorialStatePayload.ID)) {
			ServerPlayNetworking.send(player, payload);
		}
	}

	private record RoomLayout(BlockPos playerMarker, BlockPos revolverMarker, BlockPos innocentMarker,
			BlockPos evilMarker, BlockPos knifeStartMarker, BlockPos knifeTargetMarker) {
		private static RoomLayout scan(ServerWorld world, BlockPos base) {
			return new RoomLayout(
				find(world, base, Blocks.EMERALD_BLOCK, base.add(0, 0, 2)),
				find(world, base, Blocks.GOLD_BLOCK, base.add(0, 0, 6)),
				find(world, base, Blocks.LIGHT_BLUE_CONCRETE, base.add(0, 0, 11)),
				find(world, base, Blocks.RED_CONCRETE, base.add(0, 0, 14)),
				find(world, base, Blocks.ORANGE_CONCRETE, base.add(0, 0, 21)),
				find(world, base, Blocks.BLACK_CONCRETE, base.add(0, 0, 27)));
		}

		private static BlockPos find(ServerWorld world, BlockPos base, Block marker, BlockPos fallback) {
			for (int y = 0; y <= 5; y++) {
				for (int z = 0; z <= 33; z++) {
					for (int x = -5; x <= 5; x++) {
						BlockPos pos = base.add(x, y, z);
						if (world.getBlockState(pos).isOf(marker)) return pos.toImmutable();
					}
				}
			}
			return fallback.toImmutable();
		}

		private BlockPos playerStart() { return playerMarker.up(); }
		private BlockPos revolver() { return revolverMarker.up(2); }
		private BlockPos innocentTarget() { return innocentMarker.up(); }
		private BlockPos evilTarget() { return evilMarker.up(); }
		private BlockPos knifeStart() { return knifeStartMarker.up(); }
		private BlockPos knifeTarget() { return knifeTargetMarker.up(); }

		private void showMarkers(ServerWorld world) {
			set(world, playerMarker, Blocks.EMERALD_BLOCK);
			set(world, revolverMarker, Blocks.GOLD_BLOCK);
			set(world, innocentMarker, Blocks.LIGHT_BLUE_CONCRETE);
			set(world, evilMarker, Blocks.RED_CONCRETE);
			set(world, knifeStartMarker, Blocks.ORANGE_CONCRETE);
			set(world, knifeTargetMarker, Blocks.BLACK_CONCRETE);
		}

		private void clearMarkers(ServerWorld world) {
			for (BlockPos marker : List.of(playerMarker, revolverMarker, innocentMarker, evilMarker,
					knifeStartMarker, knifeTargetMarker)) {
				world.setBlockState(marker, Blocks.POLISHED_DEEPSLATE.getDefaultState(), Block.NOTIFY_ALL);
			}
		}

		private void set(ServerWorld world, BlockPos pos, Block block) {
			world.setBlockState(pos, block.getDefaultState(), Block.NOTIFY_ALL);
		}
	}

	private static final class Session {
		private final UUID playerId;
		private final ServerWorld tutorialWorld;
		private final BlockPos base;
		private final Box arena;
		private final String targetTag;
		private final RegistryKey<World> originWorld;
		private final Vec3d originPos;
		private final float originYaw;
		private final float originPitch;
		private final GameMode gameMode;
		private final int selectedSlot;
		private final NbtList inventory;
		private final NbtCompound hunger;
		private final float health;
		private final LinkedHashMap<BlockPos, BlockState> originals = new LinkedHashMap<>();
		private RoomLayout layout;
		private boolean editing;
		private int stage = PICKUP;
		private long nextActionAt;

		private Session(ServerPlayerEntity player, ServerWorld world, BlockPos base, Box arena) {
			this.playerId = player.getUuid();
			this.tutorialWorld = world;
			this.base = base;
			this.arena = arena;
			this.targetTag = "gexpress_tutorial_target_" + player.getUuidAsString();
			this.originWorld = player.getWorld().getRegistryKey();
			this.originPos = player.getPos();
			this.originYaw = player.getYaw();
			this.originPitch = player.getPitch();
			this.gameMode = player.interactionManager.getGameMode();
			this.selectedSlot = player.getInventory().selectedSlot;
			this.inventory = (NbtList) player.getInventory().writeNbt(new NbtList()).copy();
			this.hunger = new NbtCompound();
			player.getHungerManager().writeNbt(this.hunger);
			this.health = player.getHealth();
		}
	}
}
