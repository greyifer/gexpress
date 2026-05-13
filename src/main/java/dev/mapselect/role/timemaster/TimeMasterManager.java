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
import dev.doctor4t.wathe.index.WatheSounds;
import dev.doctor4t.wathe.util.ShootMuzzleS2CPayload;
import dev.mapselect.MapSelect;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.game.DeadPlayerStatus;
import dev.mapselect.network.TimeMasterFreezeStatePayload;
import dev.mapselect.network.TimeMasterFreezeUsePayload;
import dev.mapselect.network.TimeMasterRewindPayload;
import dev.mapselect.network.TimeMasterUsePayload;
import dev.mapselect.registry.MapSelectRoles;
import dev.mapselect.role.bombspecialist.C4BackComponent;
import dev.mapselect.role.bountyhunter.BountyHunterManager;
import dev.mapselect.role.AbilityTargeting;
import dev.mapselect.role.guardian.GuardianAngelManager;
import dev.mapselect.role.juggernaut.JuggernautManager;
import dev.mapselect.role.mafia.MafiaManager;
import dev.mapselect.role.medic.MedicShieldComponent;
import dev.mapselect.role.puppetmaster.PuppetmasterManager;
import dev.mapselect.role.silent.SilentShadowComponent;
import dev.mapselect.role.skincrawler.SkincrawlerManager;
import dev.mapselect.role.snitch.SnitchManager;
import dev.mapselect.role.spy.SpyManager;
import dev.mapselect.role.trickster.DancingCartsManager;
import dev.mapselect.role.trickster.TricksterManager;
import dev.mapselect.role.vulture.VultureManager;
import dev.mapselect.role.warlock.WarlockComponent;
import dev.mapselect.testing.GexpressTestState;
import dev.mapselect.voice.VoiceMuteState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
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
import net.minecraft.util.Hand;
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
	private static final int VISUAL_SNAPSHOT_INTERVAL_TICKS = 2;
	private static final int HISTORY_PADDING_TICKS = 40;
	private static final int REWIND_ANIMATION_TICKS = 48;
	private static final Map<RegistryKey<World>, Deque<WorldSnapshot>> HISTORY = new ConcurrentHashMap<>();
	private static final Map<RegistryKey<World>, Deque<VisualSnapshot>> VISUAL_HISTORY = new ConcurrentHashMap<>();
	private static final Map<RegistryKey<World>, Deque<WeaponEvent>> WEAPON_EVENTS = new ConcurrentHashMap<>();
	private static final Map<RegistryKey<World>, ActiveRewind> ACTIVE_REWINDS = new ConcurrentHashMap<>();
	private static final Map<UUID, ActiveFreeze> ACTIVE_FREEZES = new ConcurrentHashMap<>();

	private static Field cooldownEntriesField;
	private static Field cooldownTickField;
	private static Field cooldownEndTickField;
	private static boolean lookedUpCooldownFields;

	private TimeMasterManager() {}

	public static void register() {
		PayloadTypeRegistry.playC2S().register(TimeMasterUsePayload.ID, TimeMasterUsePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(TimeMasterFreezeUsePayload.ID, TimeMasterFreezeUsePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(TimeMasterRewindPayload.ID, TimeMasterRewindPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(TimeMasterFreezeStatePayload.ID, TimeMasterFreezeStatePayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(TimeMasterUsePayload.ID,
			(payload, context) -> context.server().execute(() -> tryRewind(context.player())));
		ServerPlayNetworking.registerGlobalReceiver(TimeMasterFreezeUsePayload.ID,
			(payload, context) -> context.server().execute(() -> tryFreeze(context.player())));
		ServerTickEvents.END_WORLD_TICK.register(TimeMasterManager::tick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
			server.execute(() -> syncActiveFreezes(handler.player)));
	}

	private static void tick(ServerWorld world) {
		if (world.getRegistryKey() != World.OVERWORLD) return;
		RegistryKey<World> worldKey = world.getRegistryKey();
		if (!canTrack(world)) {
			clearTimeline(worldKey);
			ACTIVE_REWINDS.remove(worldKey);
			clearFreezes(world, true);
			TimeMasterComponent comp = TimeMasterComponent.KEY.getNullable(world);
			if (comp != null) comp.clearAll();
			return;
		}

		tickFreezes(world);
		ActiveRewind active = ACTIVE_REWINDS.get(worldKey);
		if (active != null) {
			if (active.tick(world)) ACTIVE_REWINDS.remove(worldKey);
			return;
		}

		TimeMasterComponent comp = TimeMasterComponent.KEY.getNullable(world);
		boolean hasTimelineUser = false;
		if (comp != null) {
			for (ServerPlayerEntity player : world.getPlayers()) {
				if (!isTimeMaster(player)) continue;
				comp.ensurePlayer(player.getUuid());
				if (isPlayable(player, player)) hasTimelineUser = true;
			}
		}
		if (!hasTimelineUser) {
			clearTimeline(worldKey);
			return;
		}

		Deque<WorldSnapshot> history = HISTORY.computeIfAbsent(worldKey, key -> new ArrayDeque<>());
		Deque<VisualSnapshot> visualHistory = VISUAL_HISTORY.computeIfAbsent(worldKey, key -> new ArrayDeque<>());
		Deque<WeaponEvent> weaponEvents = WEAPON_EVENTS.computeIfAbsent(worldKey, key -> new ArrayDeque<>());

		long now = world.getTime();
		WorldSnapshot newest = history.peekLast();
		if (newest == null || now - newest.tick() >= SNAPSHOT_INTERVAL_TICKS) {
			history.addLast(WorldSnapshot.capture(world));
		}

		VisualSnapshot newestVisual = visualHistory.peekLast();
		if (newestVisual == null || now - newestVisual.tick() >= VISUAL_SNAPSHOT_INTERVAL_TICKS) {
			visualHistory.addLast(VisualSnapshot.capture(world));
		}

		long keepTicks = (long) GexpressConfig.getTimeMasterRewindSeconds() * 20L + HISTORY_PADDING_TICKS;
		while (!history.isEmpty() && now - history.peekFirst().tick() > keepTicks) {
			history.removeFirst();
		}
		while (!visualHistory.isEmpty() && now - visualHistory.peekFirst().tick() > keepTicks) {
			visualHistory.removeFirst();
		}
		while (!weaponEvents.isEmpty() && now - weaponEvents.peekFirst().tick() > keepTicks) {
			weaponEvents.removeFirst();
		}
	}

	private static void clearTimeline(RegistryKey<World> worldKey) {
		HISTORY.remove(worldKey);
		VISUAL_HISTORY.remove(worldKey);
		WEAPON_EVENTS.remove(worldKey);
	}

	private static void tryRewind(ServerPlayerEntity timeMaster) {
		if (timeMaster == null || !(timeMaster.getWorld() instanceof ServerWorld world)) return;
		if (VultureManager.isStashed(timeMaster) || isFrozen(timeMaster)) return;
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
		int configuredSeconds = GexpressConfig.getTimeMasterRewindSeconds();
		WorldSnapshot snapshot = findSnapshot(history, world.getTime() - (long) configuredSeconds * 20L);
		if (snapshot == null) {
			timeMaster.sendMessage(Text.literal("The timeline is not stable enough yet."), true);
			return;
		}

		int rewindSeconds = Math.min(configuredSeconds,
			(int) Math.max(1L, (world.getTime() - snapshot.tick() + 19L) / 20L));
		if (!comp.consume(timeMaster.getUuid())) return;

		world.playSound(null, timeMaster.getBlockPos(), SoundEvents.BLOCK_RESPAWN_ANCHOR_DEPLETE.value(),
			SoundCategory.PLAYERS, 1.0F, 1.25F);
		for (ServerPlayerEntity player : world.getPlayers()) {
			ServerPlayNetworking.send(player, new TimeMasterRewindPayload(REWIND_ANIMATION_TICKS));
			player.sendMessage(Text.literal("Time is rewinding " + rewindSeconds + "s."), true);
		}

		List<VisualSnapshot> frames = rewindVisualFrames(VISUAL_HISTORY.get(world.getRegistryKey()), snapshot.tick());
		List<WeaponEvent> events = rewindWeaponEvents(WEAPON_EVENTS.get(world.getRegistryKey()), snapshot.tick(), world.getTime());
		clearFreezes(world, true);
		ACTIVE_REWINDS.put(world.getRegistryKey(), new ActiveRewind(timeMaster.getUuid(), frames, events, snapshot, world.getTime()));
	}

	private static void tryFreeze(ServerPlayerEntity timeMaster) {
		if (timeMaster == null || !(timeMaster.getWorld() instanceof ServerWorld world)) return;
		if (VultureManager.isStashed(timeMaster) || isFrozen(timeMaster)) return;
		if (!canTrack(world) || !isTimeMaster(timeMaster) || !isPlayable(timeMaster, timeMaster)) return;

		TimeMasterComponent comp = TimeMasterComponent.KEY.get(world);
		comp.ensurePlayer(timeMaster.getUuid());
		if (comp.freezeUsesRemaining(timeMaster.getUuid()) <= 0) {
			timeMaster.sendMessage(Text.literal("No freezes left."), true);
			return;
		}
		long cooldown = comp.freezeCooldownRemainingTicks(timeMaster.getUuid());
		if (cooldown > 0L) {
			timeMaster.sendMessage(Text.literal("Freeze ready in " + secondsCeil(cooldown) + "s."), true);
			return;
		}

		ServerPlayerEntity target = findFreezeTarget(timeMaster);
		if (target == null) {
			timeMaster.sendMessage(Text.literal("No living player close enough to freeze."), true);
			return;
		}
		if (target == timeMaster) return;
		if (VultureManager.isStashed(target) || !isPlayable(target, timeMaster)) return;
		if (isFrozen(target)) {
			timeMaster.sendMessage(Text.literal(target.getName().getString() + " is already frozen."), true);
			return;
		}
		if (!comp.consumeFreeze(timeMaster.getUuid())) return;

		int durationTicks = GexpressConfig.getTimeMasterFreezeDurationSeconds() * 20;
		ActiveFreeze freeze = ActiveFreeze.capture(world, timeMaster.getUuid(), target, durationTicks);
		ACTIVE_FREEZES.put(target.getUuid(), freeze);
		freeze.apply(target);
		SpyManager.recordInteraction(timeMaster, target);
		syncFreeze(world, target.getUuid(), timeMaster.getUuid(), true, durationTicks);
		world.playSound(null, target.getBlockPos(), SoundEvents.BLOCK_GLASS_BREAK,
			SoundCategory.PLAYERS, 0.75F, 0.55F);
		timeMaster.sendMessage(Text.literal("Froze " + target.getName().getString() + "."), true);
		target.sendMessage(Text.literal("You are frozen in time."), true);
	}

	private static ServerPlayerEntity findFreezeTarget(ServerPlayerEntity timeMaster) {
		double range = GexpressConfig.getTimeMasterFreezeRange();
		return AbilityTargeting.findLookTarget(timeMaster, timeMaster.getServerWorld().getPlayers(), range, 0.0D, true,
			candidate -> !VultureManager.isStashed(candidate) && isPlayable(candidate, timeMaster));
	}

	private static void tickFreezes(ServerWorld world) {
		MinecraftServer server = world.getServer();
		long now = world.getTime();
		for (Map.Entry<UUID, ActiveFreeze> entry : List.copyOf(ACTIVE_FREEZES.entrySet())) {
			UUID targetId = entry.getKey();
			ActiveFreeze freeze = entry.getValue();
			if (!freeze.worldKey().equals(world.getRegistryKey())) continue;

			ServerPlayerEntity target = server.getPlayerManager().getPlayer(targetId);
			if (target == null || target.getWorld() != world || !isPlayable(target, target)) {
				ACTIVE_FREEZES.remove(targetId);
				syncFreeze(world, targetId, freeze.timeMasterId(), false, 0);
				continue;
			}
			if (now >= freeze.expiresAt()) {
				ACTIVE_FREEZES.remove(targetId);
				syncFreeze(world, targetId, freeze.timeMasterId(), false, 0);
				target.sendMessage(Text.literal("Time moves again."), true);
				continue;
			}
			freeze.apply(target);
		}
	}

	private static void clearFreezes(ServerWorld world, boolean sync) {
		if (world == null || ACTIVE_FREEZES.isEmpty()) return;
		for (Map.Entry<UUID, ActiveFreeze> entry : List.copyOf(ACTIVE_FREEZES.entrySet())) {
			UUID targetId = entry.getKey();
			ActiveFreeze freeze = entry.getValue();
			if (!freeze.worldKey().equals(world.getRegistryKey())) continue;
			ACTIVE_FREEZES.remove(targetId);
			if (sync) syncFreeze(world, targetId, freeze.timeMasterId(), false, 0);
		}
	}

	public static boolean isFrozen(ServerPlayerEntity player) {
		return player != null && ACTIVE_FREEZES.containsKey(player.getUuid());
	}

	private static void syncFreeze(ServerWorld world, UUID targetId, UUID timeMasterId, boolean frozen, int durationTicks) {
		if (world == null || targetId == null) return;
		TimeMasterFreezeStatePayload payload = frozen
			? new TimeMasterFreezeStatePayload(true, targetId, timeMasterId, durationTicks)
			: TimeMasterFreezeStatePayload.clear(targetId);
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (ServerPlayNetworking.canSend(player, TimeMasterFreezeStatePayload.ID)) {
				ServerPlayNetworking.send(player, payload);
			}
		}
	}

	private static void syncActiveFreezes(ServerPlayerEntity player) {
		if (player == null || !World.OVERWORLD.equals(player.getWorld().getRegistryKey())) return;
		for (Map.Entry<UUID, ActiveFreeze> entry : ACTIVE_FREEZES.entrySet()) {
			ActiveFreeze freeze = entry.getValue();
			if (!freeze.worldKey().equals(player.getWorld().getRegistryKey())) continue;
			long remaining = Math.max(0L, freeze.expiresAt() - player.getWorld().getTime());
			if (remaining <= 0L || !ServerPlayNetworking.canSend(player, TimeMasterFreezeStatePayload.ID)) continue;
			ServerPlayNetworking.send(player, new TimeMasterFreezeStatePayload(true, entry.getKey(), freeze.timeMasterId(),
				(int) Math.min(Integer.MAX_VALUE, remaining)));
		}
	}

	private static WorldSnapshot findSnapshot(Deque<WorldSnapshot> history, long targetTick) {
		if (history == null || history.isEmpty()) return null;
		WorldSnapshot best = history.peekFirst();
		long bestDistance = Math.abs(best.tick() - targetTick);
		for (WorldSnapshot snapshot : history) {
			long distance = Math.abs(snapshot.tick() - targetTick);
			if (distance <= bestDistance) {
				best = snapshot;
				bestDistance = distance;
			}
			if (snapshot.tick() > targetTick && distance > bestDistance) {
				break;
			}
		}
		return best;
	}

	private static List<VisualSnapshot> rewindVisualFrames(Deque<VisualSnapshot> history, long targetTick) {
		List<VisualSnapshot> frames = new ArrayList<>();
		if (history != null) {
			var iterator = history.descendingIterator();
			while (iterator.hasNext()) {
				VisualSnapshot snapshot = iterator.next();
				frames.add(snapshot);
				if (snapshot.tick() <= targetTick) break;
			}
		}
		return frames.isEmpty() ? List.of() : frames;
	}

	private static List<WeaponEvent> rewindWeaponEvents(Deque<WeaponEvent> events, long targetTick, long currentTick) {
		List<WeaponEvent> out = new ArrayList<>();
		if (events != null) {
			var iterator = events.descendingIterator();
			while (iterator.hasNext()) {
				WeaponEvent event = iterator.next();
				if (event.tick() > currentTick) continue;
				if (event.tick() < targetTick) break;
				out.add(event);
			}
		}
		return out.isEmpty() ? List.of() : out;
	}

	public static void recordWeaponEvent(ServerPlayerEntity player, WeaponEventType type) {
		if (player == null || type == null || !(player.getWorld() instanceof ServerWorld world)) return;
		if (world.getRegistryKey() != World.OVERWORLD || !canTrack(world)) return;
		if (!hasPlayableTimeMaster(world)) return;
		Deque<WeaponEvent> events = WEAPON_EVENTS.computeIfAbsent(world.getRegistryKey(), key -> new ArrayDeque<>());
		events.addLast(WeaponEvent.capture(world, player, type));
		long keepTicks = (long) GexpressConfig.getTimeMasterRewindSeconds() * 20L + HISTORY_PADDING_TICKS;
		long now = world.getTime();
		while (!events.isEmpty() && now - events.peekFirst().tick() > keepTicks) {
			events.removeFirst();
		}
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

	private static boolean hasPlayableTimeMaster(ServerWorld world) {
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (isTimeMaster(player) && isPlayable(player, player)) return true;
		}
		return false;
	}

	private static boolean isPlayable(ServerPlayerEntity player, ServerPlayerEntity timeMaster) {
		return DeadPlayerStatus.isLivingRoundParticipant(player) || GexpressTestState.isRoleTester(timeMaster);
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

	private static void syncScreen(ScreenHandler handler) {
		if (handler != null) handler.syncState();
	}

	public enum WeaponEventType {
		REVOLVER_SHOT,
		KNIFE_READY,
		KNIFE_STAB
	}

	private static final class ActiveRewind {
		private final UUID timeMasterId;
		private final List<VisualSnapshot> frames;
		private final List<WeaponEvent> weaponEvents;
		private final WorldSnapshot finalSnapshot;
		private final long startedAt;
		private int nextWeaponEvent;

		private ActiveRewind(UUID timeMasterId, List<VisualSnapshot> frames, List<WeaponEvent> weaponEvents,
				WorldSnapshot finalSnapshot, long startedAt) {
			this.timeMasterId = timeMasterId;
			this.frames = frames;
			this.weaponEvents = weaponEvents;
			this.finalSnapshot = finalSnapshot;
			this.startedAt = startedAt;
		}

		private boolean tick(ServerWorld world) {
			int elapsed = (int) Math.max(0L, world.getTime() - startedAt);
			if (!frames.isEmpty()) {
				float progress = Math.min(1.0F, elapsed / (float) REWIND_ANIMATION_TICKS);
				float scaledIndex = progress * (frames.size() - 1);
				int index = Math.min(frames.size() - 1, (int) Math.floor(scaledIndex));
				int nextIndex = Math.min(frames.size() - 1, index + 1);
				VisualSnapshot frame = frames.get(index);
				VisualSnapshot nextFrame = frames.get(nextIndex);
				float frameDelta = Math.max(0.0F, Math.min(1.0F, scaledIndex - index));
				frame.applyVisualFrame(world, nextFrame, frameDelta);
				long eventTick = Math.round(lerp(frame.tick(), nextFrame.tick(), frameDelta));
				replayWeaponEvents(world, eventTick);
			}
			if (elapsed < REWIND_ANIMATION_TICKS) return false;

			finalSnapshot.restore(world);
			TimeMasterComponent.KEY.get(world).spendRewindAfterRestore(timeMasterId);
			for (ServerPlayerEntity player : world.getPlayers()) {
				player.sendMessage(Text.literal("Time snapped back."), true);
			}
			Deque<WorldSnapshot> freshHistory = HISTORY.computeIfAbsent(world.getRegistryKey(), key -> new ArrayDeque<>());
			freshHistory.clear();
			freshHistory.addLast(WorldSnapshot.capture(world));
			Deque<VisualSnapshot> freshVisualHistory = VISUAL_HISTORY.computeIfAbsent(world.getRegistryKey(), key -> new ArrayDeque<>());
			freshVisualHistory.clear();
			freshVisualHistory.addLast(VisualSnapshot.capture(world));
			Deque<WeaponEvent> freshWeaponEvents = WEAPON_EVENTS.computeIfAbsent(world.getRegistryKey(), key -> new ArrayDeque<>());
			freshWeaponEvents.clear();
			return true;
		}

		private void replayWeaponEvents(ServerWorld world, long frameTick) {
			while (nextWeaponEvent < weaponEvents.size()) {
				WeaponEvent event = weaponEvents.get(nextWeaponEvent);
				if (event.tick() < frameTick) return;
				event.replay(world);
				nextWeaponEvent++;
			}
		}
	}

	private record ActiveFreeze(RegistryKey<World> worldKey, UUID timeMasterId, long expiresAt,
			double x, double y, double z, float yaw, float pitch, int selectedSlot,
			boolean usingItem, Hand activeHand, boolean sneaking, boolean sprinting) {
		private static ActiveFreeze capture(ServerWorld world, UUID timeMasterId, ServerPlayerEntity target,
				int durationTicks) {
			return new ActiveFreeze(
				world.getRegistryKey(),
				timeMasterId,
				world.getTime() + durationTicks,
				target.getX(),
				target.getY(),
				target.getZ(),
				target.getYaw(),
				target.getPitch(),
				target.getInventory().selectedSlot,
				target.isUsingItem(),
				target.isUsingItem() ? target.getActiveHand() : Hand.MAIN_HAND,
				target.isSneaking(),
				target.isSprinting()
			);
		}

		private void apply(ServerPlayerEntity target) {
			target.stopRiding();
			target.teleport(target.getServerWorld(), x, y, z, yaw, pitch);
			target.setVelocity(Vec3d.ZERO);
			target.velocityModified = true;
			target.fallDistance = 0.0F;
			target.setSneaking(sneaking);
			target.setSprinting(sprinting);
			if (target.getInventory().selectedSlot != selectedSlot) {
				target.getInventory().selectedSlot = selectedSlot;
				target.getInventory().markDirty();
				syncScreen(target.playerScreenHandler);
			}
			if (usingItem) {
				if (!target.isUsingItem() || target.getActiveHand() != activeHand) {
					target.setCurrentHand(activeHand);
				}
			} else if (target.isUsingItem()) {
				target.stopUsingItem();
			}
		}
	}

	private record WeaponEvent(long tick, UUID playerId, WeaponEventType type, double x, double eyeY, double z,
			int selectedSlot, ItemStack mainHand, ItemStack offHand, boolean usingItem, Hand activeHand) {
		private static WeaponEvent capture(ServerWorld world, ServerPlayerEntity player, WeaponEventType type) {
			boolean usingItem = player.isUsingItem();
			return new WeaponEvent(
				world.getTime(),
				player.getUuid(),
				type,
				player.getX(),
				player.getEyeY(),
				player.getZ(),
				player.getInventory().selectedSlot,
				player.getMainHandStack().copy(),
				player.getOffHandStack().copy(),
				usingItem,
				usingItem ? player.getActiveHand() : Hand.MAIN_HAND
			);
		}

		private void replay(ServerWorld world) {
			ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(playerId);
			if (player != null && player.getWorld() != world) player = null;
			if (player != null) applyHeldState(player);
			switch (type) {
				case REVOLVER_SHOT -> {
					world.playSound(null, x, eyeY, z, WatheSounds.ITEM_REVOLVER_SHOOT,
						SoundCategory.PLAYERS, 5.0F, 1.0F + (world.getRandom().nextFloat() * 0.1F) - 0.05F);
					if (player != null) {
						ShootMuzzleS2CPayload payload = new ShootMuzzleS2CPayload(playerId.toString());
						for (ServerPlayerEntity viewer : world.getPlayers()) {
							ServerPlayNetworking.send(viewer, payload);
						}
					}
				}
				case KNIFE_READY -> world.playSound(null, x, eyeY, z, WatheSounds.ITEM_KNIFE_PREPARE,
					SoundCategory.PLAYERS, 1.0F, 1.0F);
				case KNIFE_STAB -> {
					world.playSound(null, x, eyeY, z, WatheSounds.ITEM_KNIFE_STAB,
						SoundCategory.PLAYERS, 1.0F, 1.0F);
					if (player != null) player.swingHand(activeHand);
				}
			}
		}

		private void applyHeldState(ServerPlayerEntity player) {
			boolean changed = false;
			if (selectedSlot >= 0 && selectedSlot < 9 && player.getInventory().selectedSlot != selectedSlot) {
				player.getInventory().selectedSlot = selectedSlot;
				changed = true;
			}
			if (!ItemStack.areEqual(player.getMainHandStack(), mainHand)) {
				player.setStackInHand(Hand.MAIN_HAND, mainHand.copy());
				changed = true;
			}
			if (!ItemStack.areEqual(player.getOffHandStack(), offHand)) {
				player.setStackInHand(Hand.OFF_HAND, offHand.copy());
				changed = true;
			}
			if (changed) {
				player.getInventory().markDirty();
				syncScreen(player.playerScreenHandler);
			}
			if (usingItem) {
				if (!player.isUsingItem() || player.getActiveHand() != activeHand) {
					player.stopUsingItem();
					player.setCurrentHand(activeHand);
				}
			} else if (player.isUsingItem()) {
				player.stopUsingItem();
			}
		}
	}

	private record VisualSnapshot(long tick, Map<UUID, VisualPlayerSnapshot> players,
			Map<UUID, VisualEntitySnapshot> items, Map<UUID, VisualBodySnapshot> bodies) {
		private static VisualSnapshot capture(ServerWorld world) {
			Map<UUID, VisualPlayerSnapshot> players = new LinkedHashMap<>();
			for (ServerPlayerEntity player : world.getPlayers()) {
				players.put(player.getUuid(), VisualPlayerSnapshot.capture(player));
			}

			Map<UUID, VisualEntitySnapshot> items = new LinkedHashMap<>();
			for (ItemEntity item : world.getEntitiesByType(EntityType.ITEM, entity -> true)) {
				items.put(item.getUuid(), VisualEntitySnapshot.capture(item));
			}

			Map<UUID, VisualBodySnapshot> bodies = new LinkedHashMap<>();
			for (PlayerBodyEntity body : world.getEntitiesByType(WatheEntities.PLAYER_BODY, entity -> true)) {
				bodies.put(body.getUuid(), VisualBodySnapshot.capture(body));
			}

			return new VisualSnapshot(world.getTime(), players, items, bodies);
		}

		private void applyVisualFrame(ServerWorld world, VisualSnapshot nextFrame, float delta) {
			MinecraftServer server = world.getServer();
			for (VisualPlayerSnapshot snapshot : players.values()) {
				ServerPlayerEntity player = server.getPlayerManager().getPlayer(snapshot.playerId());
				if (player != null && player.getWorld() == world) {
					snapshot.applyVisualFrame(player, nextFrame.players().get(snapshot.playerId()), delta);
				}
			}
			for (ItemEntity item : world.getEntitiesByType(EntityType.ITEM, entity -> true)) {
				VisualEntitySnapshot snapshot = items.get(item.getUuid());
				if (snapshot != null) snapshot.applyVisualFrame(item, nextFrame.items().get(item.getUuid()), delta);
			}
			for (PlayerBodyEntity body : world.getEntitiesByType(WatheEntities.PLAYER_BODY, entity -> true)) {
				VisualBodySnapshot snapshot = bodies.get(body.getUuid());
				if (snapshot != null) snapshot.applyVisualFrame(body, nextFrame.bodies().get(body.getUuid()), delta);
			}
		}
	}

	private record VisualPlayerSnapshot(UUID playerId, double x, double y, double z, float yaw, float pitch,
			int selectedSlot, ItemStack mainHand, ItemStack offHand, boolean usingItem, Hand activeHand) {
		private static VisualPlayerSnapshot capture(ServerPlayerEntity player) {
			boolean usingItem = player.isUsingItem();
			Hand activeHand = usingItem ? player.getActiveHand() : Hand.MAIN_HAND;
			return new VisualPlayerSnapshot(
				player.getUuid(),
				player.getX(),
				player.getY(),
				player.getZ(),
				player.getYaw(),
				player.getPitch(),
				player.getInventory().selectedSlot,
				player.getMainHandStack().copy(),
				player.getOffHandStack().copy(),
				usingItem,
				activeHand
			);
		}

		private void applyVisualFrame(ServerPlayerEntity player, VisualPlayerSnapshot next, float delta) {
			VisualPlayerSnapshot heldState = next != null && delta >= 0.5F ? next : this;
			player.stopRiding();
			double frameX = next == null ? x : lerp(x, next.x(), delta);
			double frameY = next == null ? y : lerp(y, next.y(), delta);
			double frameZ = next == null ? z : lerp(z, next.z(), delta);
			float frameYaw = next == null ? yaw : lerpYaw(yaw, next.yaw(), delta);
			float framePitch = next == null ? pitch : (float) lerp(pitch, next.pitch(), delta);
			player.teleport(player.getServerWorld(), frameX, frameY, frameZ, frameYaw, framePitch);
			player.setVelocity(Vec3d.ZERO);
			player.velocityModified = true;
			boolean changedHeldItem = heldState.applyHeldItems(player);
			heldState.applyUsingItem(player);
			if (changedHeldItem) syncScreen(player.playerScreenHandler);
		}

		private boolean applyHeldItems(ServerPlayerEntity player) {
			boolean changed = false;
			if (selectedSlot >= 0 && selectedSlot < 9 && player.getInventory().selectedSlot != selectedSlot) {
				player.getInventory().selectedSlot = selectedSlot;
				changed = true;
			}
			if (!ItemStack.areEqual(player.getMainHandStack(), mainHand)) {
				player.setStackInHand(Hand.MAIN_HAND, mainHand.copy());
				changed = true;
			}
			if (!ItemStack.areEqual(player.getOffHandStack(), offHand)) {
				player.setStackInHand(Hand.OFF_HAND, offHand.copy());
				changed = true;
			}
			if (changed) player.getInventory().markDirty();
			return changed;
		}

		private void applyUsingItem(ServerPlayerEntity player) {
			if (!usingItem) {
				if (player.isUsingItem()) player.stopUsingItem();
				return;
			}
			if (!player.isUsingItem() || player.getActiveHand() != activeHand) {
				player.stopUsingItem();
				player.setCurrentHand(activeHand);
			}
		}
	}

	private record VisualEntitySnapshot(UUID entityId, double x, double y, double z, float yaw, float pitch) {
		private static VisualEntitySnapshot capture(ItemEntity item) {
			return new VisualEntitySnapshot(item.getUuid(), item.getX(), item.getY(), item.getZ(), item.getYaw(), item.getPitch());
		}

		private void applyVisualFrame(ItemEntity item, VisualEntitySnapshot next, float delta) {
			double frameX = next == null ? x : lerp(x, next.x(), delta);
			double frameY = next == null ? y : lerp(y, next.y(), delta);
			double frameZ = next == null ? z : lerp(z, next.z(), delta);
			float frameYaw = next == null ? yaw : lerpYaw(yaw, next.yaw(), delta);
			float framePitch = next == null ? pitch : (float) lerp(pitch, next.pitch(), delta);
			item.refreshPositionAndAngles(frameX, frameY, frameZ, frameYaw, framePitch);
			item.setVelocity(Vec3d.ZERO);
			item.velocityModified = true;
		}
	}

	private record VisualBodySnapshot(UUID entityId, double x, double y, double z, float yaw, float pitch) {
		private static VisualBodySnapshot capture(PlayerBodyEntity body) {
			return new VisualBodySnapshot(body.getUuid(), body.getX(), body.getY(), body.getZ(), body.getYaw(), body.getPitch());
		}

		private void applyVisualFrame(PlayerBodyEntity body, VisualBodySnapshot next, float delta) {
			double frameX = next == null ? x : lerp(x, next.x(), delta);
			double frameY = next == null ? y : lerp(y, next.y(), delta);
			double frameZ = next == null ? z : lerp(z, next.z(), delta);
			float frameYaw = next == null ? yaw : lerpYaw(yaw, next.yaw(), delta);
			float framePitch = next == null ? pitch : (float) lerp(pitch, next.pitch(), delta);
			body.refreshPositionAndAngles(frameX, frameY, frameZ, frameYaw, framePitch);
			body.setVelocity(Vec3d.ZERO);
			body.velocityModified = true;
		}
	}

	private static double lerp(double start, double end, float delta) {
		return start + (end - start) * delta;
	}

	private static float lerpYaw(float start, float end, float delta) {
		float diff = ((end - start + 540.0F) % 360.0F) - 180.0F;
		return start + diff * delta;
	}

	private record WorldSnapshot(long tick, Map<UUID, PlayerSnapshot> players,
			Map<UUID, ItemEntitySnapshot> items, Map<UUID, BodyEntitySnapshot> bodies,
			Map<BlockPos, BlockSnapshot> blocks,
			JuggernautManager.TimeState juggernaut, SnitchManager.TimeState snitch,
			VultureManager.TimeState vulture, PuppetmasterManager.TimeState puppetmaster,
			DancingCartsManager.TimeState dancingCarts,
			BountyHunterManager.TimeState bountyHunter,
			MafiaManager.TimeState mafia, SkincrawlerManager.TimeState skincrawler,
			SpyManager.TimeState spy,
			GuardianAngelManager.TimeState guardianAngel,
			NbtCompound gameWorld, NbtCompound gameTime, NbtCompound timeMaster,
			NbtCompound c4Back, NbtCompound medicShield,
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
				VultureManager.snapshotForTimeRewind(),
				PuppetmasterManager.snapshotForTimeRewind(),
				DancingCartsManager.snapshotForTimeRewind(world),
				BountyHunterManager.snapshotForTimeRewind(),
				MafiaManager.snapshotForTimeRewind(),
				SkincrawlerManager.snapshotForTimeRewind(),
				SpyManager.snapshotForTimeRewind(),
				GuardianAngelManager.snapshotForTimeRewind(),
				writeComponent(GameWorldComponent.KEY.getNullable(world), lookup),
				writeComponent(GameTimeComponent.KEY.getNullable(world), lookup),
				writeComponent(TimeMasterComponent.KEY.getNullable(world), lookup),
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
			PuppetmasterManager.restoreForTimeRewind(puppetmaster);
			BountyHunterManager.restoreForTimeRewind(bountyHunter);
			MafiaManager.restoreForTimeRewind(world, mafia);
			SkincrawlerManager.restoreForTimeRewind(world, skincrawler);
			SpyManager.restoreForTimeRewind(spy);
			SnitchManager.restoreForTimeRewind(world, snitch);
			restoreBlocks(world, lookup);
			DancingCartsManager.restoreForTimeRewind(world, dancingCarts);

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
			readComponent(GameWorldComponent.KEY.getNullable(world), gameWorld, lookup);
			readComponent(GameTimeComponent.KEY.getNullable(world), gameTime, lookup);
			readComponent(TimeMasterComponent.KEY.getNullable(world), timeMaster, lookup);
			readComponent(C4BackComponent.KEY.getNullable(world), c4Back, lookup);
			readComponent(MedicShieldComponent.KEY.getNullable(world), medicShield, lookup);
			readComponent(SilentShadowComponent.KEY.getNullable(world), silentShadow, lookup);
			readComponent(WarlockComponent.KEY.getNullable(world), warlock, lookup);
			readComponent(VoiceMuteState.KEY.getNullable(world), voiceMute, lookup);
			VultureManager.restoreForTimeRewind(world, vulture);
			GuardianAngelManager.restoreForTimeRewind(world, guardianAngel);

			GameWorldComponent.KEY.sync(world);
			GameTimeComponent.KEY.sync(world);
			TimeMasterComponent.KEY.sync(world);
			C4BackComponent.KEY.sync(world);
			MedicShieldComponent.KEY.sync(world);
			SilentShadowComponent.KEY.sync(world);
			WarlockComponent.KEY.sync(world);
			VoiceMuteState.KEY.sync(world);
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
