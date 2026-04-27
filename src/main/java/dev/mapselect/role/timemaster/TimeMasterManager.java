package dev.mapselect.role.timemaster;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameTimeComponent;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.cca.PlayerNoteComponent;
import dev.doctor4t.wathe.cca.PlayerPoisonComponent;
import dev.doctor4t.wathe.cca.PlayerPsychoComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.compat.TrainVoicePlugin;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheEntities;
import dev.mapselect.MapSelect;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.network.TimeMasterRewindPayload;
import dev.mapselect.network.TimeMasterUsePayload;
import dev.mapselect.registry.MapSelectRoles;
import dev.mapselect.role.bombspecialist.C4BackComponent;
import dev.mapselect.role.juggernaut.JuggernautManager;
import dev.mapselect.role.medic.MedicShieldComponent;
import dev.mapselect.role.puppetmaster.PuppetmasterManager;
import dev.mapselect.role.silent.SilentShadowComponent;
import dev.mapselect.role.snitch.SnitchManager;
import dev.mapselect.role.trickster.TricksterManager;
import dev.mapselect.role.vulture.VultureManager;
import dev.mapselect.role.warlock.WarlockComponent;
import dev.mapselect.testing.GexpressTestState;
import dev.mapselect.voice.VoiceMuteState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Property;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import org.ladysnake.cca.api.v3.component.Component;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TimeMasterManager {
	private static final int SNAPSHOT_INTERVAL_TICKS = 5;
	private static final int HISTORY_PADDING_TICKS = 40;
	private static final int REWIND_ANIMATION_TICKS = 36;
	private static final Map<RegistryKey<World>, Deque<WorldSnapshot>> HISTORY = new ConcurrentHashMap<>();
	private static final Map<RegistryKey<World>, ActiveRewind> ACTIVE_REWINDS = new ConcurrentHashMap<>();

	private static Field cooldownEntriesField;
	private static Field cooldownTickField;
	private static Field cooldownEndTickField;
	private static boolean lookedUpCooldownFields;

	private TimeMasterManager() {}

	public static void register() {
		PayloadTypeRegistry.playC2S().register(TimeMasterUsePayload.ID, TimeMasterUsePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(TimeMasterRewindPayload.ID, TimeMasterRewindPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(TimeMasterUsePayload.ID,
			(payload, context) -> context.server().execute(() -> tryRewind(context.player())));
		ServerTickEvents.END_WORLD_TICK.register(TimeMasterManager::tick);
	}

	private static void tick(ServerWorld world) {
		if (world.getRegistryKey() != World.OVERWORLD) return;
		ActiveRewind active = ACTIVE_REWINDS.get(world.getRegistryKey());
		if (active != null) {
			if (active.tick(world)) ACTIVE_REWINDS.remove(world.getRegistryKey());
			return;
		}

		Deque<WorldSnapshot> history = HISTORY.computeIfAbsent(world.getRegistryKey(), key -> new ArrayDeque<>());
		if (!canTrack(world)) {
			history.clear();
			TimeMasterComponent comp = TimeMasterComponent.KEY.getNullable(world);
			if (comp != null) comp.clearAll();
			return;
		}

		TimeMasterComponent comp = TimeMasterComponent.KEY.getNullable(world);
		if (comp != null) {
			for (ServerPlayerEntity player : world.getPlayers()) {
				if (isTimeMaster(player)) comp.ensurePlayer(player.getUuid());
			}
		}

		long now = world.getTime();
		WorldSnapshot newest = history.peekLast();
		if (newest == null || now - newest.tick() >= SNAPSHOT_INTERVAL_TICKS) {
			history.addLast(WorldSnapshot.capture(world));
		}

		long keepTicks = (long) GexpressConfig.getTimeMasterRewindSeconds() * 20L + HISTORY_PADDING_TICKS;
		while (!history.isEmpty() && now - history.peekFirst().tick() > keepTicks) {
			history.removeFirst();
		}
	}

	private static void tryRewind(ServerPlayerEntity timeMaster) {
		if (timeMaster == null || !(timeMaster.getWorld() instanceof ServerWorld world)) return;
		if (VultureManager.isStashed(timeMaster)) return;
		if (!canTrack(world) || !isTimeMaster(timeMaster) || !isPlayable(timeMaster, timeMaster)) return;

		TimeMasterComponent comp = TimeMasterComponent.KEY.get(world);
		comp.ensurePlayer(timeMaster.getUuid());
		if (comp.usesRemaining(timeMaster.getUuid()) <= 0) {
			timeMaster.sendMessage(Text.literal("No rewinds left."), true);
			return;
		}
		long cooldown = comp.cooldownRemainingTicks(timeMaster.getUuid());
		if (cooldown > 0L) {
			timeMaster.sendMessage(Text.literal("Time Master ready in " + secondsCeil(cooldown) + "s."), true);
			return;
		}

		Deque<WorldSnapshot> history = HISTORY.get(world.getRegistryKey());
		WorldSnapshot snapshot = findSnapshot(history, world.getTime() - (long) GexpressConfig.getTimeMasterRewindSeconds() * 20L);
		if (snapshot == null) {
			timeMaster.sendMessage(Text.literal("The timeline is not stable enough yet."), true);
			return;
		}

		int rewindSeconds = (int) Math.max(1L, (world.getTime() - snapshot.tick() + 19L) / 20L);
		if (!comp.consume(timeMaster.getUuid())) return;

		world.playSound(null, timeMaster.getBlockPos(), SoundEvents.BLOCK_RESPAWN_ANCHOR_DEPLETE.value(),
			SoundCategory.PLAYERS, 1.0F, 1.25F);
		for (ServerPlayerEntity player : world.getPlayers()) {
			ServerPlayNetworking.send(player, new TimeMasterRewindPayload(REWIND_ANIMATION_TICKS));
			player.sendMessage(Text.literal("Time is rewinding " + rewindSeconds + "s."), true);
		}

		List<WorldSnapshot> frames = rewindFrames(history, snapshot.tick());
		ACTIVE_REWINDS.put(world.getRegistryKey(), new ActiveRewind(timeMaster.getUuid(), frames, snapshot, world.getTime()));
	}

	private static WorldSnapshot findSnapshot(Deque<WorldSnapshot> history, long targetTick) {
		if (history == null || history.isEmpty()) return null;
		WorldSnapshot best = null;
		for (WorldSnapshot snapshot : history) {
			if (snapshot.tick() <= targetTick) {
				best = snapshot;
			} else {
				break;
			}
		}
		return best != null ? best : history.peekFirst();
	}

	private static List<WorldSnapshot> rewindFrames(Deque<WorldSnapshot> history, long targetTick) {
		List<WorldSnapshot> frames = new ArrayList<>();
		if (history != null) {
			var iterator = history.descendingIterator();
			while (iterator.hasNext()) {
				WorldSnapshot snapshot = iterator.next();
				frames.add(snapshot);
				if (snapshot.tick() <= targetTick) break;
			}
		}
		return frames.isEmpty() ? List.of() : frames;
	}

	private static boolean canTrack(ServerWorld world) {
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
		return (game != null && game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE)
			|| GexpressTestState.hasRoleTesters();
	}

	private static boolean isTimeMaster(ServerPlayerEntity player) {
		if (player == null || player.getWorld() == null) return false;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(player.getWorld());
		if (game == null) return false;
		Role role = game.getRole(player);
		return role != null && MapSelectRoles.TIME_MASTER_ID.equals(role.identifier());
	}

	private static boolean isPlayable(ServerPlayerEntity player, ServerPlayerEntity timeMaster) {
		return GameFunctions.isPlayerAliveAndSurvival(player) || GexpressTestState.isRoleTester(timeMaster);
	}

	private static long secondsCeil(long ticks) {
		return Math.max(1L, (ticks + 19L) / 20L);
	}

	private static NbtCompound writeComponent(Component component, RegistryWrapper.WrapperLookup lookup) {
		NbtCompound tag = new NbtCompound();
		if (component != null) component.writeToNbt(tag, lookup);
		return tag.copy();
	}

	private static void readComponent(Component component, NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
		if (component != null && tag != null) component.readFromNbt(tag.copy(), lookup);
	}

	private record ActiveRewind(UUID timeMasterId, List<WorldSnapshot> frames, WorldSnapshot finalSnapshot, long startedAt) {
		private boolean tick(ServerWorld world) {
			int elapsed = (int) Math.max(0L, world.getTime() - startedAt);
			if (!frames.isEmpty()) {
				float progress = Math.min(1.0F, elapsed / (float) REWIND_ANIMATION_TICKS);
				int index = Math.min(frames.size() - 1, Math.round(progress * (frames.size() - 1)));
				frames.get(index).applyVisualFrame(world);
			}
			if (elapsed < REWIND_ANIMATION_TICKS) return false;

			finalSnapshot.restore(world);
			for (ServerPlayerEntity player : world.getPlayers()) {
				player.sendMessage(Text.literal("Time snapped back."), true);
			}
			Deque<WorldSnapshot> freshHistory = HISTORY.computeIfAbsent(world.getRegistryKey(), key -> new ArrayDeque<>());
			freshHistory.clear();
			freshHistory.addLast(WorldSnapshot.capture(world));
			return true;
		}
	}

	private record WorldSnapshot(long tick, Map<UUID, PlayerSnapshot> players,
			Map<UUID, ItemEntitySnapshot> items, Map<UUID, BodyEntitySnapshot> bodies,
			Map<BlockPos, BlockSnapshot> blocks,
			JuggernautManager.TimeState juggernaut, SnitchManager.TimeState snitch,
			NbtCompound gameTime, NbtCompound c4Back, NbtCompound medicShield,
			NbtCompound silentShadow, NbtCompound warlock, NbtCompound voiceMute) {

		private static WorldSnapshot capture(ServerWorld world) {
			RegistryWrapper.WrapperLookup lookup = world.getRegistryManager();
			Map<UUID, PlayerSnapshot> players = new LinkedHashMap<>();
			for (ServerPlayerEntity player : world.getPlayers()) {
				players.put(player.getUuid(), PlayerSnapshot.capture(player, lookup));
			}

			Map<UUID, ItemEntitySnapshot> items = new LinkedHashMap<>();
			for (ItemEntity item : world.getEntitiesByType(EntityType.ITEM, entity -> true)) {
				items.put(item.getUuid(), ItemEntitySnapshot.capture(item));
			}

			Map<UUID, BodyEntitySnapshot> bodies = new LinkedHashMap<>();
			for (PlayerBodyEntity body : world.getEntitiesByType(WatheEntities.PLAYER_BODY, entity -> true)) {
				bodies.put(body.getUuid(), BodyEntitySnapshot.capture(body));
			}

			Map<BlockPos, BlockSnapshot> blocks = captureBlocks(world, lookup);

			return new WorldSnapshot(
				world.getTime(),
				players,
				items,
				bodies,
				blocks,
				JuggernautManager.snapshotForTimeRewind(),
				SnitchManager.snapshotForTimeRewind(),
				writeComponent(GameTimeComponent.KEY.getNullable(world), lookup),
				writeComponent(C4BackComponent.KEY.getNullable(world), lookup),
				writeComponent(MedicShieldComponent.KEY.getNullable(world), lookup),
				writeComponent(SilentShadowComponent.KEY.getNullable(world), lookup),
				writeComponent(WarlockComponent.KEY.getNullable(world), lookup),
				writeComponent(VoiceMuteState.KEY.getNullable(world), lookup)
			);
		}

		private static Map<BlockPos, BlockSnapshot> captureBlocks(ServerWorld world,
				RegistryWrapper.WrapperLookup lookup) {
			Map<BlockPos, BlockSnapshot> out = new LinkedHashMap<>();
			final int horizontalRadius = 8;
			final int verticalRadius = 5;
			BlockPos.Mutable mutable = new BlockPos.Mutable();
			for (ServerPlayerEntity player : world.getPlayers()) {
				BlockPos center = player.getBlockPos();
				for (int x = -horizontalRadius; x <= horizontalRadius; x++) {
					for (int y = -verticalRadius; y <= verticalRadius; y++) {
						for (int z = -horizontalRadius; z <= horizontalRadius; z++) {
							mutable.set(center.getX() + x, center.getY() + y, center.getZ() + z);
							BlockState state = world.getBlockState(mutable);
							BlockEntity blockEntity = world.getBlockEntity(mutable);
							if (isRewindRelevantBlock(state, blockEntity)) {
								BlockPos pos = mutable.toImmutable();
								out.putIfAbsent(pos, BlockSnapshot.capture(world, pos, state, blockEntity, lookup));
							}
						}
					}
				}
			}
			return out;
		}

		private static boolean isRewindRelevantBlock(BlockState state, BlockEntity blockEntity) {
			if (blockEntity != null) return true;
			for (Property<?> property : state.getProperties()) {
				String name = property.getName();
				if ("open".equals(name) || "powered".equals(name) || "enabled".equals(name)
						|| "lit".equals(name) || "triggered".equals(name)) {
					return true;
				}
			}
			return false;
		}

		private void restore(ServerWorld world) {
			RegistryWrapper.WrapperLookup lookup = world.getRegistryManager();
			MinecraftServer server = world.getServer();
			PuppetmasterManager.clearForTimeRewind(server);
			VultureManager.clearForTimeRewind(world);
			TricksterManager.clearForTimeRewind(world);
			JuggernautManager.restoreForTimeRewind(juggernaut);
			SnitchManager.restoreForTimeRewind(world, snitch);
			restoreBlocks(world, lookup);

			Set<UUID> revived = new HashSet<>();
			for (PlayerSnapshot snapshot : players.values()) {
				ServerPlayerEntity player = server.getPlayerManager().getPlayer(snapshot.playerId());
				if (player == null) continue;
				boolean wasDead = !GameFunctions.isPlayerAliveAndSurvival(player);
				snapshot.restore(player, lookup);
				if (snapshot.alive()) {
					TrainVoicePlugin.resetPlayer(player.getUuid());
					if (wasDead) revived.add(player.getUuid());
				} else {
					TrainVoicePlugin.addPlayer(player.getUuid());
				}
			}

			restoreItems(world);
			restoreBodies(world, revived);
			readComponent(GameTimeComponent.KEY.getNullable(world), gameTime, lookup);
			readComponent(C4BackComponent.KEY.getNullable(world), c4Back, lookup);
			readComponent(MedicShieldComponent.KEY.getNullable(world), medicShield, lookup);
			readComponent(SilentShadowComponent.KEY.getNullable(world), silentShadow, lookup);
			readComponent(WarlockComponent.KEY.getNullable(world), warlock, lookup);
			readComponent(VoiceMuteState.KEY.getNullable(world), voiceMute, lookup);

			GameTimeComponent.KEY.sync(world);
			C4BackComponent.KEY.sync(world);
			MedicShieldComponent.KEY.sync(world);
			SilentShadowComponent.KEY.sync(world);
			WarlockComponent.KEY.sync(world);
		}

		private void restoreBlocks(ServerWorld world, RegistryWrapper.WrapperLookup lookup) {
			for (Map.Entry<BlockPos, BlockSnapshot> entry : blocks.entrySet()) {
				entry.getValue().restore(world, entry.getKey(), lookup);
			}
		}

		private void restoreItems(ServerWorld world) {
			Set<UUID> current = new HashSet<>();
			for (ItemEntity item : world.getEntitiesByType(EntityType.ITEM, entity -> true)) {
				current.add(item.getUuid());
				if (!items.containsKey(item.getUuid())) item.discard();
			}
			for (Map.Entry<UUID, ItemEntitySnapshot> entry : items.entrySet()) {
				if (!current.contains(entry.getKey())) entry.getValue().spawn(world);
			}
		}

		private void restoreBodies(ServerWorld world, Set<UUID> revived) {
			Set<UUID> current = new HashSet<>();
			for (PlayerBodyEntity body : world.getEntitiesByType(WatheEntities.PLAYER_BODY, entity -> true)) {
				current.add(body.getUuid());
				boolean remove = !bodies.containsKey(body.getUuid()) || revived.contains(body.getPlayerUuid());
				if (remove) body.discard();
			}
			for (Map.Entry<UUID, BodyEntitySnapshot> entry : bodies.entrySet()) {
				if (!current.contains(entry.getKey()) && !revived.contains(entry.getValue().playerId())) {
					entry.getValue().spawn(world);
				}
			}
		}

		private void applyVisualFrame(ServerWorld world) {
			MinecraftServer server = world.getServer();
			RegistryWrapper.WrapperLookup lookup = world.getRegistryManager();
			for (Map.Entry<BlockPos, BlockSnapshot> entry : blocks.entrySet()) {
				entry.getValue().restore(world, entry.getKey(), lookup);
			}
			for (PlayerSnapshot snapshot : players.values()) {
				ServerPlayerEntity player = server.getPlayerManager().getPlayer(snapshot.playerId());
				if (player != null && player.getWorld() == world) {
					snapshot.applyVisualFrame(player);
				}
			}
			for (ItemEntity item : world.getEntitiesByType(EntityType.ITEM, entity -> true)) {
				ItemEntitySnapshot snapshot = items.get(item.getUuid());
				if (snapshot != null) snapshot.applyVisualFrame(item);
			}
			for (PlayerBodyEntity body : world.getEntitiesByType(WatheEntities.PLAYER_BODY, entity -> true)) {
				BodyEntitySnapshot snapshot = bodies.get(body.getUuid());
				if (snapshot != null) snapshot.applyVisualFrame(body);
			}
		}
	}

	private record BlockSnapshot(BlockState state, NbtCompound blockEntityNbt) {
		private static BlockSnapshot capture(ServerWorld world, BlockPos pos, BlockState state,
				BlockEntity blockEntity, RegistryWrapper.WrapperLookup lookup) {
			NbtCompound tag = blockEntity == null ? null : blockEntity.createNbtWithId(lookup).copy();
			return new BlockSnapshot(state, tag);
		}

		private void restore(ServerWorld world, BlockPos pos, RegistryWrapper.WrapperLookup lookup) {
			BlockState current = world.getBlockState(pos);
			if (!current.equals(state)) {
				world.setBlockState(pos, state, 3);
			}
			if (blockEntityNbt == null) return;
			BlockEntity blockEntity = world.getBlockEntity(pos);
			if (blockEntity == null) return;
			blockEntity.read(blockEntityNbt.copy(), lookup);
			blockEntity.markDirty();
			world.updateListeners(pos, state, state, 3);
		}
	}

	private record PlayerSnapshot(UUID playerId, boolean alive, GameMode gameMode,
			double x, double y, double z, float yaw, float pitch, Vec3d velocity,
			float health, float absorption, int fireTicks, int frozenTicks, int air, float fallDistance,
			int selectedSlot, NbtList inventory, NbtCompound hunger, NbtCompound abilities,
			int experienceLevel, int totalExperience, float experienceProgress, int score,
			List<StatusEffectInstance> effects, int shopBalance,
			NbtCompound mood, NbtCompound poison, NbtCompound psycho, NbtCompound note,
			ItemCooldownSnapshot cooldowns) {

		private static PlayerSnapshot capture(ServerPlayerEntity player, RegistryWrapper.WrapperLookup lookup) {
			List<StatusEffectInstance> effects = new ArrayList<>();
			for (StatusEffectInstance effect : player.getStatusEffects()) {
				effects.add(new StatusEffectInstance(effect));
			}

			NbtCompound hunger = new NbtCompound();
			player.getHungerManager().writeNbt(hunger);
			NbtCompound abilities = new NbtCompound();
			player.getAbilities().writeNbt(abilities);

			return new PlayerSnapshot(
				player.getUuid(),
				GameFunctions.isPlayerAliveAndSurvival(player),
				player.interactionManager.getGameMode(),
				player.getX(),
				player.getY(),
				player.getZ(),
				player.getYaw(),
				player.getPitch(),
				player.getVelocity(),
				player.getHealth(),
				player.getAbsorptionAmount(),
				player.getFireTicks(),
				player.getFrozenTicks(),
				player.getAir(),
				player.fallDistance,
				player.getInventory().selectedSlot,
				(NbtList) player.getInventory().writeNbt(new NbtList()).copy(),
				hunger.copy(),
				abilities.copy(),
				player.experienceLevel,
				player.totalExperience,
				player.experienceProgress,
				player.getScore(),
				effects,
				PlayerShopComponent.KEY.get(player).balance,
				writeComponent(PlayerMoodComponent.KEY.getNullable(player), lookup),
				writeComponent(PlayerPoisonComponent.KEY.getNullable(player), lookup),
				writeComponent(PlayerPsychoComponent.KEY.getNullable(player), lookup),
				writeComponent(PlayerNoteComponent.KEY.getNullable(player), lookup),
				ItemCooldownSnapshot.capture(player.getItemCooldownManager())
			);
		}

		private void restore(ServerPlayerEntity player, RegistryWrapper.WrapperLookup lookup) {
			player.stopRiding();
			player.stopUsingItem();
			player.closeHandledScreen();
			player.changeGameMode(gameMode);
			player.teleport(player.getServerWorld(), x, y, z, yaw, pitch);
			player.setVelocity(velocity);
			player.velocityModified = true;

			player.getInventory().clear();
			player.getInventory().readNbt((NbtList) inventory.copy());
			player.getInventory().selectedSlot = selectedSlot;
			player.getInventory().markDirty();
			syncScreen(player.playerScreenHandler);

			player.getHungerManager().readNbt(hunger.copy());
			player.getAbilities().readNbt(abilities.copy());
			player.sendAbilitiesUpdate();
			player.clearStatusEffects();
			for (StatusEffectInstance effect : effects) {
				player.addStatusEffect(new StatusEffectInstance(effect));
			}

			player.setHealth(alive ? Math.max(1.0F, Math.min(health, player.getMaxHealth())) : Math.min(health, player.getMaxHealth()));
			player.setAbsorptionAmount(absorption);
			player.setFireTicks(fireTicks);
			player.setFrozenTicks(frozenTicks);
			player.setAir(air);
			player.fallDistance = fallDistance;
			player.experienceLevel = experienceLevel;
			player.totalExperience = totalExperience;
			player.experienceProgress = experienceProgress;
			player.setScore(score);

			PlayerShopComponent.KEY.get(player).setBalance(shopBalance);
			readComponent(PlayerMoodComponent.KEY.getNullable(player), mood, lookup);
			readComponent(PlayerPoisonComponent.KEY.getNullable(player), poison, lookup);
			readComponent(PlayerPsychoComponent.KEY.getNullable(player), psycho, lookup);
			readComponent(PlayerNoteComponent.KEY.getNullable(player), note, lookup);
			PlayerMoodComponent.KEY.sync(player);
			PlayerPoisonComponent.KEY.sync(player);
			PlayerPsychoComponent.KEY.sync(player);
			PlayerNoteComponent.KEY.sync(player);
			PlayerShopComponent.KEY.sync(player);
			cooldowns.restore(player.getItemCooldownManager());
		}

		private void applyVisualFrame(ServerPlayerEntity player) {
			player.stopRiding();
			player.teleport(player.getServerWorld(), x, y, z, yaw, pitch);
			player.setVelocity(Vec3d.ZERO);
			player.velocityModified = true;
		}

		private static void syncScreen(ScreenHandler handler) {
			if (handler != null) handler.syncState();
		}
	}

	private record ItemEntitySnapshot(UUID entityId, NbtCompound tag, ItemStack stack,
			double x, double y, double z, float yaw, float pitch, Vec3d velocity) {
		private static ItemEntitySnapshot capture(ItemEntity item) {
			return new ItemEntitySnapshot(
				item.getUuid(),
				item.writeNbt(new NbtCompound()).copy(),
				item.getStack().copy(),
				item.getX(),
				item.getY(),
				item.getZ(),
				item.getYaw(),
				item.getPitch(),
				item.getVelocity()
			);
		}

		private void spawn(ServerWorld world) {
			if (stack.isEmpty()) return;
			ItemEntity item = new ItemEntity(world, x, y, z, stack.copy());
			item.setUuid(entityId);
			item.readNbt(tag.copy());
			item.refreshPositionAndAngles(x, y, z, yaw, pitch);
			item.setVelocity(velocity);
			world.spawnEntity(item);
		}

		private void applyVisualFrame(ItemEntity item) {
			item.refreshPositionAndAngles(x, y, z, yaw, pitch);
			item.setVelocity(Vec3d.ZERO);
			item.velocityModified = true;
		}
	}

	private record BodyEntitySnapshot(UUID entityId, UUID playerId, NbtCompound tag,
			double x, double y, double z, float yaw, float pitch, Vec3d velocity) {
		private static BodyEntitySnapshot capture(PlayerBodyEntity body) {
			return new BodyEntitySnapshot(
				body.getUuid(),
				body.getPlayerUuid(),
				body.writeNbt(new NbtCompound()).copy(),
				body.getX(),
				body.getY(),
				body.getZ(),
				body.getYaw(),
				body.getPitch(),
				body.getVelocity()
			);
		}

		private void spawn(ServerWorld world) {
			PlayerBodyEntity body = WatheEntities.PLAYER_BODY.create(world);
			if (body == null) return;
			body.setUuid(entityId);
			body.readNbt(tag.copy());
			body.setPlayerUuid(playerId);
			body.refreshPositionAndAngles(x, y, z, yaw, pitch);
			body.setVelocity(velocity);
			world.spawnEntity(body);
		}

		private void applyVisualFrame(PlayerBodyEntity body) {
			body.refreshPositionAndAngles(x, y, z, yaw, pitch);
			body.setVelocity(Vec3d.ZERO);
			body.velocityModified = true;
		}
	}

	private record ItemCooldownSnapshot(Map<Item, Integer> remainingTicks) {
		private static ItemCooldownSnapshot capture(ItemCooldownManager manager) {
			Map<Item, Integer> out = new LinkedHashMap<>();
			Map<Item, Integer> current = currentCooldowns(manager);
			if (current != null) out.putAll(current);
			return new ItemCooldownSnapshot(out);
		}

		private void restore(ItemCooldownManager manager) {
			Map<Item, Integer> current = currentCooldowns(manager);
			Collection<Item> currentItems = current == null ? List.of() : current.keySet();
			for (Item item : new ArrayList<>(currentItems)) {
				manager.remove(item);
			}
			for (Map.Entry<Item, Integer> entry : remainingTicks.entrySet()) {
				int remaining = Math.max(0, entry.getValue());
				if (remaining > 0) manager.set(entry.getKey(), remaining);
			}
		}
	}

	@SuppressWarnings("unchecked")
	private static Map<Item, Integer> currentCooldowns(ItemCooldownManager manager) {
		try {
			lookupCooldownFields();
			if (cooldownEntriesField == null || cooldownTickField == null || cooldownEndTickField == null) return null;
			Map<Item, ?> entries = (Map<Item, ?>) cooldownEntriesField.get(manager);
			int tick = cooldownTickField.getInt(manager);
			Map<Item, Integer> out = new LinkedHashMap<>();
			for (Map.Entry<Item, ?> entry : entries.entrySet()) {
				int endTick = cooldownEndTickField.getInt(entry.getValue());
				int remaining = Math.max(0, endTick - tick);
				if (remaining > 0) out.put(entry.getKey(), remaining);
			}
			return out;
		} catch (Throwable t) {
			MapSelect.LOGGER.debug("Failed to snapshot item cooldowns for Time Master: {}", t.toString());
			return null;
		}
	}

	private static void lookupCooldownFields() throws ReflectiveOperationException {
		if (lookedUpCooldownFields) return;
		lookedUpCooldownFields = true;
		cooldownEntriesField = ItemCooldownManager.class.getDeclaredField("entries");
		cooldownEntriesField.setAccessible(true);
		cooldownTickField = ItemCooldownManager.class.getDeclaredField("tick");
		cooldownTickField.setAccessible(true);
		for (Class<?> inner : ItemCooldownManager.class.getDeclaredClasses()) {
			if (!inner.getSimpleName().equals("Entry")) continue;
			cooldownEndTickField = inner.getDeclaredField("endTick");
			cooldownEndTickField.setAccessible(true);
			break;
		}
	}
}
