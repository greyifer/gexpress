package dev.mapselect.role.mafia;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheRoles;
import dev.doctor4t.wathe.api.event.AllowPlayerDeath;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.cca.GameRoundEndComponent;
import dev.doctor4t.wathe.cca.GameTimeComponent;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheEntities;
import dev.doctor4t.wathe.index.WatheItems;
import dev.mapselect.MapSelect;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.game.DeadPlayerStatus;
import dev.mapselect.game.GexpressGameModes;
import dev.mapselect.network.AbilityCooldownPayload;
import dev.mapselect.network.AbilityCooldownSync;
import dev.mapselect.network.MafiaActionPayload;
import dev.mapselect.network.MafiaAmmoPayload;
import dev.mapselect.network.MafiaIntroPayload;
import dev.mapselect.network.MafiaStatePayload;
import dev.mapselect.registry.MapSelectItems;
import dev.mapselect.registry.MapSelectRoles;
import dev.mapselect.role.AbilityTargeting;
import dev.mapselect.role.NeutralWinManager;
import dev.mapselect.role.PassiveMoney;
import dev.mapselect.role.spy.SpyManager;
import dev.mapselect.role.vulture.VultureManager;
import dev.mapselect.testing.GexpressTestState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.events.ModdedRoleAssigned;
import org.agmas.harpymodloader.events.ModdedRoleRemoved;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

public final class MafiaManager {
	private static final double BODY_LOOK_RADIUS_SQUARED = 2.25D;
	private static final int INTRO_TICKS = 90;
	private static final int NORMAL_MEMBER_LIMIT = 1;
	private static final int TAKEOVER_MEMBER_LIMIT = 3;
	private static final Map<UUID, Slots> slotsByGodfather = new HashMap<>();
	private static final Map<UUID, UUID> godfatherByMember = new HashMap<>();
	private static final Map<UUID, Role> previousRoleByMember = new HashMap<>();
	private static final Map<UUID, Integer> loadedBulletsByGodfather = new HashMap<>();
	private static final Map<UUID, Long> janitorCleanCooldownUntil = new HashMap<>();
	private static final Map<UUID, Integer> pendingRevolverCooldown = new HashMap<>();
	private static int syncTick;

	private MafiaManager() {}

	public static void register() {
		PayloadTypeRegistry.playC2S().register(MafiaActionPayload.ID, MafiaActionPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(MafiaStatePayload.ID, MafiaStatePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(MafiaIntroPayload.ID, MafiaIntroPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(MafiaAmmoPayload.ID, MafiaAmmoPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(MafiaActionPayload.ID,
			(payload, context) -> context.server().execute(() -> handleAction(context.player(), payload.action())));
		ServerTickEvents.END_WORLD_TICK.register(MafiaManager::tick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
			server.execute(() -> {
				sync(handler.player);
				syncAmmo(handler.player);
			}));
		AllowPlayerDeath.EVENT.register(MafiaManager::allowDeath);
		GameEvents.ON_FINISH_INITIALIZE.register(MafiaManager::onFinishInitialize);
		GameEvents.ON_FINISH_FINALIZE.register((world, game) -> clearAll(world));
	}

	private static void onFinishInitialize(World world, GameWorldComponent game) {
		clearAll(world);
		if (!(world instanceof ServerWorld serverWorld) || game == null) return;
		for (ServerPlayerEntity player : serverWorld.getPlayers()) {
			if (!isGodfather(player)) continue;
			slotsByGodfather.putIfAbsent(player.getUuid(), new Slots());
			grantGodfatherLoadout(player);
			loadedBulletsByGodfather.put(player.getUuid(), startingLoadedBullets());
			setMafiaStartingBalance(player);
			sendIntro(player);
			sync(player);
			syncAmmo(player);
		}
	}

	private static void handleAction(ServerPlayerEntity player, int action) {
		if (player == null || !(player.getWorld() instanceof ServerWorld world)) return;
		if (action == MafiaActionPayload.RECRUIT_MAFIOSO) {
			tryRecruit(player, SlotType.MAFIOSO);
		} else if (action == MafiaActionPayload.RECRUIT_JANITOR) {
			tryRecruit(player, SlotType.JANITOR);
		} else if (action == MafiaActionPayload.CLEAN_BODY) {
			tryClean(player);
		}
	}

	private static void tryRecruit(ServerPlayerEntity godfather, SlotType type) {
		if (!canUseHere(godfather.getWorld(), godfather) || !isGodfather(godfather)
				|| VultureManager.isStashed(godfather) || !GameFunctions.isPlayerAliveAndSurvival(godfather)) {
			return;
		}
		Slots slots = slotsByGodfather.computeIfAbsent(godfather.getUuid(), id -> new Slots());
		int limit = memberLimit(godfather, type);
		if (livingMemberCount(godfather.getServerWorld(), slots, type) >= limit) {
			godfather.sendMessage(Text.literal(type.displayName() + " slots are full.").formatted(Formatting.GRAY), true);
			return;
		}
		long remaining = replacementRemaining(godfather, slots, type);
		if (remaining > 0L) {
			syncRecruitCooldown(godfather, slots, type, remaining);
			godfather.sendMessage(Text.literal(type.displayName() + " replacement ready in "
				+ secondsCeil(remaining) + "s.").formatted(Formatting.GRAY), true);
			return;
		}
		ServerPlayerEntity target = findTarget(godfather, GexpressConfig.getMafiaRecruitRange());
		if (target == null) {
			godfather.sendMessage(Text.literal("No living player close enough to recruit.").formatted(Formatting.GRAY), true);
			return;
		}
		if (isMafiaRole(target)) {
			godfather.sendMessage(Text.literal("That player is already part of the family.").formatted(Formatting.GRAY), true);
			return;
		}

		Role oldRole = currentRole(target);
		Role newRole = type == SlotType.MAFIOSO ? MapSelectRoles.MAFIOSO : MapSelectRoles.JANITOR;
		if (newRole == null) return;
		assignRole(target, newRole);
		previousRoleByMember.put(target.getUuid(), oldRole == null ? WatheRoles.CIVILIAN : oldRole);
		godfatherByMember.put(target.getUuid(), godfather.getUuid());
		slots.add(type, target.getUuid());
		setMafiaStartingBalance(target);
		sendIntro(target);
		target.sendMessage(Text.literal("You have been recruited as the " + type.displayName() + ".")
			.formatted(Formatting.DARK_GRAY), true);
		godfather.sendMessage(Text.literal(target.getName().getString() + " is now your " + type.displayName() + ".")
			.formatted(Formatting.GRAY), true);
		SpyManager.recordInteraction(godfather, target);
		syncFamily(godfather.getServerWorld(), godfather.getUuid());
	}

	private static void tryClean(ServerPlayerEntity janitor) {
		if (!canUseHere(janitor.getWorld(), janitor) || !isJanitor(janitor)
				|| VultureManager.isStashed(janitor) || !GameFunctions.isPlayerAliveAndSurvival(janitor)) {
			return;
		}
		long remaining = janitorCleanRemaining(janitor);
		if (remaining > 0L) {
			AbilityCooldownSync.send(janitor, AbilityCooldownPayload.JANITOR_CLEAN, remaining,
				(long) GexpressConfig.getJanitorCleanCooldownSeconds() * 20L, false);
			janitor.sendMessage(Text.literal("Clean ready in " + secondsCeil(remaining) + "s.")
				.formatted(Formatting.GRAY), true);
			return;
		}
		PlayerBodyEntity body = findBody(janitor);
		if (body == null) {
			janitor.sendMessage(Text.literal("No body close enough to clean.").formatted(Formatting.GRAY), true);
			return;
		}
		body.discard();
		int cleanCooldown = GexpressConfig.getJanitorCleanCooldownSeconds() * 20;
		if (cleanCooldown > 0) {
			janitorCleanCooldownUntil.put(janitor.getUuid(), janitor.getWorld().getTime() + cleanCooldown);
			AbilityCooldownSync.send(janitor, AbilityCooldownPayload.JANITOR_CLEAN, cleanCooldown, cleanCooldown, false);
		}
		janitor.getWorld().playSound(null, janitor.getBlockPos(), SoundEvents.BLOCK_WOOL_BREAK,
			SoundCategory.PLAYERS, 0.85F, 0.65F);
		janitor.sendMessage(Text.literal("Body cleaned.").formatted(Formatting.DARK_GRAY), true);
	}

	private static boolean allowDeath(PlayerEntity victim, PlayerEntity killer, Identifier reason) {
		if (victim == null || victim.getWorld().isClient) return true;
		if (killer instanceof ServerPlayerEntity attacker && victim instanceof ServerPlayerEntity target) {
			if (isMafiaRole(attacker) && isMafiaRole(target) && !attacker.getUuid().equals(target.getUuid())) {
				if (!canMafiaKillMafia(attacker, target)) {
					attacker.sendMessage(Text.literal("You cannot kill your own family.").formatted(Formatting.GRAY), true);
					return false;
				}
			}
			if ((isMafioso(attacker) || isJanitor(attacker)) && GameConstants.DeathReasons.GUN.equals(reason)) {
				pendingRevolverCooldown.put(attacker.getUuid(),
					GexpressConfig.getMafiaRevolverKillCooldownSeconds() * 20);
			}
			if (attacker != target) {
				SpyManager.recordInteraction(attacker, target);
			}
			if (isJanitor(attacker) && attacker != target) {
				int ticks = GexpressConfig.getJanitorCleanCooldownAfterKillSeconds() * 20;
				if (ticks > 0) {
					janitorCleanCooldownUntil.put(attacker.getUuid(), attacker.getWorld().getTime() + ticks);
					AbilityCooldownSync.send(attacker, AbilityCooldownPayload.JANITOR_CLEAN, ticks, ticks, false);
				}
			}
		}
		if (victim instanceof ServerPlayerEntity dead && (isMafioso(dead) || isJanitor(dead))) {
			onMemberDeath(dead);
		}
		if (victim instanceof ServerPlayerEntity dead && isGodfather(dead)) {
			onGodfatherDeath(dead);
		}
		return true;
	}

	public static boolean beforeGunShot(ServerPlayerEntity shooter) {
		if (isMafiaRole(shooter)) {
			suppressMafiaRevolverCooldown(shooter);
		}
		if (!isGodfather(shooter)) return true;
		int loaded = loadedBulletsByGodfather.getOrDefault(shooter.getUuid(), 0);
		if (loaded <= 0) {
			shooter.sendMessage(Text.literal("Out of bullets.").formatted(Formatting.GRAY), true);
			shooter.getWorld().playSound(null, shooter.getBlockPos(), SoundEvents.UI_BUTTON_CLICK.value(),
				SoundCategory.PLAYERS, 0.35F, 0.65F);
			syncAmmo(shooter);
			return false;
		}
		loadedBulletsByGodfather.put(shooter.getUuid(), loaded - 1);
		syncAmmo(shooter);
		suppressMafiaRevolverCooldown(shooter);
		return true;
	}

	public static boolean tryLoadBullet(ServerPlayerEntity player, ItemStack stack) {
		if (player == null || stack == null || stack.isEmpty() || !stack.isOf(MapSelectItems.BULLET)) return false;
		if (!isGodfather(player)) {
			player.sendMessage(Text.literal("Only the Godfather can load these bullets.").formatted(Formatting.GRAY), true);
			return false;
		}
		int max = GexpressConfig.getGodfatherMaxLoadedBullets();
		int loaded = Math.max(0, loadedBulletsByGodfather.getOrDefault(player.getUuid(), 0));
		if (loaded >= max) {
			player.sendMessage(Text.literal("Revolver is already loaded (" + loaded + "/" + max + ").")
				.formatted(Formatting.GRAY), true);
			syncAmmo(player);
			return false;
		}
		if (!player.getAbilities().creativeMode) stack.decrement(1);
		loadedBulletsByGodfather.put(player.getUuid(), loaded + 1);
		player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.ITEM_CROSSBOW_LOADING_END.value(),
			SoundCategory.PLAYERS, 0.75F, 0.75F);
		player.sendMessage(Text.literal("Loaded bullet (" + (loaded + 1) + "/" + max + ").")
			.formatted(Formatting.GRAY), true);
		player.playerScreenHandler.syncState();
		syncAmmo(player);
		return true;
	}

	public static void afterGunShot(ServerPlayerEntity shooter) {
		if (shooter == null) return;
		if (isGodfather(shooter)) {
			suppressMafiaRevolverCooldown(shooter);
			return;
		}
		Integer cooldown = pendingRevolverCooldown.remove(shooter.getUuid());
		if (cooldown != null && cooldown > 0) {
			shooter.getItemCooldownManager().set(WatheItems.REVOLVER, cooldown);
		}
	}

	public static boolean shouldBlockWeaponDrop(PlayerEntity player, ItemStack stack) {
		if (player == null || stack == null || stack.isEmpty()) return false;
		return isMafiaRole(player) && stack.isOf(WatheItems.REVOLVER);
	}

	public static boolean shouldBlockWeaponRemoval(PlayerEntity player, Predicate<ItemStack> predicate, int maxCount) {
		if (player == null || predicate == null || maxCount <= 0 || !isMafiaRole(player)) return false;
		try {
			return predicate.test(WatheItems.REVOLVER.getDefaultStack());
		} catch (Throwable ignored) {
			return false;
		}
	}

	private static void suppressMafiaRevolverCooldown(ServerPlayerEntity shooter) {
		if (shooter != null) shooter.getItemCooldownManager().remove(WatheItems.REVOLVER);
	}

	public static boolean handleMurderTick(ServerWorld world, GameWorldComponent game) {
		if (world == null || game == null || game.getGameStatus() != GameWorldComponent.GameStatus.ACTIVE) return false;
		List<ServerPlayerEntity> alive = world.getPlayers(GameFunctions::isPlayerAliveAndSurvival);
		List<ServerPlayerEntity> mafia = alive.stream().filter(MafiaManager::isMafiaRole).toList();
		if (mafia.isEmpty()) return false;
		if (!GexpressGameModes.isTakeover(game) && mafia.stream().noneMatch(MafiaManager::isGodfather)) return false;

		PassiveMoney.grant(world, game);

		if (GexpressGameModes.isTakeover(game)) {
			return handleTakeoverTick(world, game, alive);
		}

		GameFunctions.WinStatus winStatus = GameFunctions.WinStatus.NONE;
		if (!GameTimeComponent.KEY.get(world).hasTime()) {
			winStatus = GameFunctions.WinStatus.TIME;
		} else if (alive.size() == mafia.size()) {
			ServerPlayerEntity godfather = mafia.stream().filter(MafiaManager::isGodfather).findFirst().orElse(mafia.getFirst());
			game.setLooseEndWinner(godfather.getUuid());
			NeutralWinManager.announce(world, godfather, "announcement.win.gexpress.godfather",
				MapSelectRoles.GODFATHER == null ? 0x6B6B6B : MapSelectRoles.GODFATHER.color());
			winStatus = GameFunctions.WinStatus.LOOSE_END;
		}

		if (winStatus != GameFunctions.WinStatus.NONE) {
			GameRoundEndComponent.KEY.get(world).setRoundEndData(world.getPlayers(), winStatus);
			GameFunctions.stopGame(world);
		}
		return true;
	}

	private static boolean handleTakeoverTick(ServerWorld world, GameWorldComponent game,
			List<ServerPlayerEntity> alive) {
		GameFunctions.WinStatus winStatus = GameFunctions.WinStatus.NONE;
		if (!GameTimeComponent.KEY.get(world).hasTime()) {
			winStatus = GameFunctions.WinStatus.TIME;
		} else {
			UUID winningGodfather = takeoverControllingGodfather(world, alive);
			if (winningGodfather != null) {
				ServerPlayerEntity announcer = world.getServer().getPlayerManager().getPlayer(winningGodfather);
				if (announcer == null || !GameFunctions.isPlayerAliveAndSurvival(announcer)) {
					announcer = alive.stream()
						.filter(player -> winningGodfather.equals(familyRoot(player.getUuid())))
						.findFirst()
						.orElse(null);
				}
				TakeoverSide side = TakeoverManager.sideForGodfather(winningGodfather);
				if (announcer != null && side != null) {
					game.setLooseEndWinner(winningGodfather);
					NeutralWinManager.announce(world, announcer, side.winTranslationKey(), side.color());
					winStatus = GameFunctions.WinStatus.LOOSE_END;
				}
			}
		}

		if (winStatus != GameFunctions.WinStatus.NONE) {
			GameRoundEndComponent.KEY.get(world).setRoundEndData(world.getPlayers(), winStatus);
			GameFunctions.stopGame(world);
		}
		return true;
	}

	private static UUID takeoverControllingGodfather(ServerWorld world, List<ServerPlayerEntity> alive) {
		UUID winner = null;
		for (ServerPlayerEntity player : alive) {
			UUID root = familyRoot(player.getUuid());
			if (root == null || !TakeoverManager.isTrackedGodfather(root)) return null;
			if (winner == null) {
				winner = root;
			} else if (!winner.equals(root)) {
				return null;
			}
		}
		if (winner == null) return null;
		return winner;
	}

	private static void tick(ServerWorld world) {
		if (world.getRegistryKey() != World.OVERWORLD) return;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
		boolean active = game != null && game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE;
		if (!active && !GexpressTestState.hasRoleTesters()) {
			clearAll(world);
			return;
		}

		if (++syncTick < 10) return;
		syncTick = 0;
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (!GameFunctions.isPlayerAliveAndSurvival(player) && !GexpressTestState.isRoleTester(player)) continue;
			if (isGodfather(player)) {
				slotsByGodfather.putIfAbsent(player.getUuid(), new Slots());
				loadedBulletsByGodfather.putIfAbsent(player.getUuid(), startingLoadedBullets());
				ensureGodfatherRevolver(player);
				syncFamily(world, player.getUuid());
				syncRecruitCooldowns(player);
				syncAmmo(player);
			} else if (isJanitor(player)) {
				syncJanitorCleanCooldown(player);
			} else if (isMafioso(player)) {
				sync(player);
			}
		}
	}

	private static void onMemberDeath(ServerPlayerEntity member) {
		UUID godfatherId = godfatherByMember.remove(member.getUuid());
		Role previousRole = previousRoleByMember.remove(member.getUuid());
		if (previousRole != null) {
			assignRole(member, previousRole);
		}
		if (godfatherId == null) return;
		Slots slots = slotsByGodfather.computeIfAbsent(godfatherId, id -> new Slots());
		long ready = member.getWorld().getTime() + (long) GexpressConfig.getMafiaReplacementCooldownSeconds() * 20L;
		if (slots.remove(SlotType.MAFIOSO, member.getUuid())) {
			slots.mafiosoReadyTick = ready;
		}
		if (slots.remove(SlotType.JANITOR, member.getUuid())) {
			slots.janitorReadyTick = ready;
		}
		ServerPlayerEntity godfather = member.getServer().getPlayerManager().getPlayer(godfatherId);
		if (godfather != null) {
			godfather.sendMessage(Text.literal("A family slot will reopen in "
				+ GexpressConfig.getMafiaReplacementCooldownSeconds() + "s.").formatted(Formatting.GRAY), true);
			syncRecruitCooldowns(godfather);
		}
		syncFamily(member.getServerWorld(), godfatherId);
	}

	private static void onGodfatherDeath(ServerPlayerEntity godfather) {
		UUID godfatherId = godfather.getUuid();
		loadedBulletsByGodfather.remove(godfatherId);
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(godfather.getWorld());
		Slots slots = slotsByGodfather.get(godfatherId);
		if (GexpressGameModes.isTakeover(game)) {
			if (ServerPlayNetworking.canSend(godfather, MafiaStatePayload.ID)) {
				ServerPlayNetworking.send(godfather, new MafiaStatePayload(List.of()));
			}
			if (slots != null) syncFamily(godfather.getServerWorld(), godfatherId);
			return;
		}
		slots = slotsByGodfather.remove(godfatherId);
		if (slots == null) return;
		for (UUID memberId : slots.members(SlotType.MAFIOSO)) {
			restoreMemberAfterGodfatherDeath(godfather.getServerWorld(), memberId);
		}
		for (UUID memberId : slots.members(SlotType.JANITOR)) {
			restoreMemberAfterGodfatherDeath(godfather.getServerWorld(), memberId);
		}
		if (ServerPlayNetworking.canSend(godfather, MafiaStatePayload.ID)) {
			ServerPlayNetworking.send(godfather, new MafiaStatePayload(List.of()));
		}
	}

	private static void restoreMemberAfterGodfatherDeath(ServerWorld world, UUID memberId) {
		if (memberId == null) return;
		godfatherByMember.remove(memberId);
		Role previousRole = previousRoleByMember.remove(memberId);
		ServerPlayerEntity member = world.getServer().getPlayerManager().getPlayer(memberId);
		if (member == null || member.getWorld() != world || !GameFunctions.isPlayerAliveAndSurvival(member)) return;
		assignRole(member, previousRole == null ? WatheRoles.CIVILIAN : previousRole);
		if (ServerPlayNetworking.canSend(member, MafiaStatePayload.ID)) {
			ServerPlayNetworking.send(member, new MafiaStatePayload(List.of()));
		}
		member.sendMessage(Text.literal("The Godfather died. Your old role has returned.")
			.formatted(Formatting.GRAY), true);
	}

	private static ServerPlayerEntity findTarget(ServerPlayerEntity user, double range) {
		return AbilityTargeting.findLookTarget(user, user.getServerWorld().getPlayers(), range, 0.0D, true,
			candidate -> !VultureManager.isStashed(candidate) && DeadPlayerStatus.isLivingRoundParticipant(candidate));
	}

	private static PlayerBodyEntity findBody(ServerPlayerEntity janitor) {
		double range = GexpressConfig.getJanitorCleanRange();
		Vec3d eye = janitor.getEyePos();
		Vec3d look = janitor.getRotationVec(1.0F).normalize();
		PlayerBodyEntity best = null;
		double bestAlong = Double.MAX_VALUE;
		for (PlayerBodyEntity body : janitor.getServerWorld().getEntitiesByType(WatheEntities.PLAYER_BODY, entity -> true)) {
			Vec3d to = body.getPos().add(0.0D, 0.8D, 0.0D).subtract(eye);
			double along = to.dotProduct(look);
			if (along < 0.0D || along > range || along >= bestAlong) continue;
			double perpendicularSq = Math.max(0.0D, to.lengthSquared() - along * along);
			if (perpendicularSq > BODY_LOOK_RADIUS_SQUARED) continue;
			best = body;
			bestAlong = along;
		}
		return best;
	}

	private static void grantGodfatherLoadout(ServerPlayerEntity player) {
		ensureGodfatherRevolver(player);
		player.playerScreenHandler.syncState();
	}

	private static void ensureGodfatherRevolver(ServerPlayerEntity player) {
		if (player.getInventory().count(WatheItems.REVOLVER) <= 0) {
			player.getInventory().insertStack(WatheItems.REVOLVER.getDefaultStack());
		}
	}

	private static void setMafiaStartingBalance(ServerPlayerEntity player) {
		PlayerShopComponent.KEY.get(player).setBalance(startingGold(player));
		PlayerShopComponent.KEY.sync(player);
	}

	private static int startingGold(ServerPlayerEntity player) {
		if (isGodfather(player)) return GexpressConfig.getGodfatherStartingGold();
		if (isMafioso(player)) return GexpressConfig.getMafiosoStartingGold();
		if (isJanitor(player)) return GexpressConfig.getJanitorStartingGold();
		return GexpressConfig.getMafiaStartingGold();
	}

	private static void assignRole(ServerPlayerEntity player, Role role) {
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(player.getWorld());
		if (game == null || role == null) return;
		Role previous = game.getRole(player);
		if (previous != null && !Harpymodloader.VANNILA_ROLES.contains(previous)) {
			ModdedRoleRemoved.EVENT.invoker().removeModdedRole(player, previous);
		}
		game.addRole(player, role);
		if (!Harpymodloader.VANNILA_ROLES.contains(role)) {
			ModdedRoleAssigned.EVENT.invoker().assignModdedRole(player, role);
		}
		game.sync();
	}

	private static Role currentRole(PlayerEntity player) {
		GameWorldComponent game = player == null ? null : GameWorldComponent.KEY.getNullable(player.getWorld());
		return game == null ? null : game.getRole(player);
	}

	public static boolean isMafiaRole(PlayerEntity player) {
		return isGodfather(player) || isMafioso(player) || isJanitor(player);
	}

	public static boolean isMafiaRole(Role role) {
		if (role == null || role.identifier() == null) return false;
		Identifier id = role.identifier();
		return MapSelectRoles.GODFATHER_ID.equals(id)
			|| MapSelectRoles.MAFIOSO_ID.equals(id)
			|| MapSelectRoles.JANITOR_ID.equals(id);
	}

	public static boolean isGodfather(PlayerEntity player) {
		Role role = currentRole(player);
		return role != null && MapSelectRoles.GODFATHER_ID.equals(role.identifier());
	}

	public static boolean isMafioso(PlayerEntity player) {
		Role role = currentRole(player);
		return role != null && MapSelectRoles.MAFIOSO_ID.equals(role.identifier());
	}

	public static boolean isJanitor(PlayerEntity player) {
		Role role = currentRole(player);
		return role != null && MapSelectRoles.JANITOR_ID.equals(role.identifier());
	}

	public static UUID familyRoot(UUID playerId) {
		if (playerId == null) return null;
		if (slotsByGodfather.containsKey(playerId)) return playerId;
		return godfatherByMember.get(playerId);
	}

	public static boolean isSameFamily(UUID firstPlayerId, UUID secondPlayerId) {
		UUID firstRoot = familyRoot(firstPlayerId);
		UUID secondRoot = familyRoot(secondPlayerId);
		return firstRoot != null && firstRoot.equals(secondRoot);
	}

	private static boolean canMafiaKillMafia(ServerPlayerEntity attacker, ServerPlayerEntity target) {
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(attacker.getWorld());
		if (!GexpressGameModes.isTakeover(game)) return false;
		UUID attackerRoot = familyRoot(attacker.getUuid());
		UUID targetRoot = familyRoot(target.getUuid());
		return attackerRoot != null
			&& targetRoot != null
			&& !attackerRoot.equals(targetRoot)
			&& TakeoverManager.isTrackedGodfather(attackerRoot)
			&& TakeoverManager.isTrackedGodfather(targetRoot);
	}

	private static boolean canUseHere(World world, PlayerEntity player) {
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
		return (game != null && game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE)
			|| GexpressTestState.isRoleTester(player);
	}

	private static boolean isLivingPlayer(ServerWorld world, UUID id) {
		if (id == null) return false;
		ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(id);
		return player != null && player.getWorld() == world && DeadPlayerStatus.isLivingRoundParticipant(player);
	}

	private static int memberLimit(ServerPlayerEntity godfather, SlotType type) {
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(godfather.getWorld());
		return GexpressGameModes.isTakeover(game) ? TAKEOVER_MEMBER_LIMIT : NORMAL_MEMBER_LIMIT;
	}

	private static int livingMemberCount(ServerWorld world, Slots slots, SlotType type) {
		if (slots == null) return 0;
		int count = 0;
		for (UUID memberId : slots.members(type)) {
			if (isLivingPlayer(world, memberId)) count++;
		}
		return count;
	}

	private static long replacementRemaining(ServerPlayerEntity godfather, Slots slots, SlotType type) {
		long ready = type == SlotType.MAFIOSO ? slots.mafiosoReadyTick : slots.janitorReadyTick;
		if (ready <= 0L) return 0L;
		long remaining = ready - godfather.getWorld().getTime();
		if (remaining <= 0L) {
			if (type == SlotType.MAFIOSO) slots.mafiosoReadyTick = 0L;
			else slots.janitorReadyTick = 0L;
			return 0L;
		}
		return remaining;
	}

	private static long janitorCleanRemaining(ServerPlayerEntity janitor) {
		Long until = janitorCleanCooldownUntil.get(janitor.getUuid());
		if (until == null) return 0L;
		long remaining = until - janitor.getWorld().getTime();
		if (remaining <= 0L) {
			janitorCleanCooldownUntil.remove(janitor.getUuid());
			return 0L;
		}
		return remaining;
	}

	public static void reduceCooldowns(ServerPlayerEntity player, long ticks) {
		if (player == null || ticks <= 0L) return;
		if (isGodfather(player)) {
			Slots slots = slotsByGodfather.get(player.getUuid());
			if (slots != null) {
				reduceRecruitCooldown(player, slots, SlotType.MAFIOSO, ticks);
				reduceRecruitCooldown(player, slots, SlotType.JANITOR, ticks);
			}
		}
		if (isJanitor(player)) {
			long remaining = janitorCleanRemaining(player);
			if (remaining > 0L) {
				long next = Math.max(0L, remaining - ticks);
				if (next <= 0L) janitorCleanCooldownUntil.remove(player.getUuid());
				else janitorCleanCooldownUntil.put(player.getUuid(), player.getWorld().getTime() + next);
				syncJanitorCleanCooldown(player);
			}
		}
	}

	private static void reduceRecruitCooldown(ServerPlayerEntity godfather, Slots slots, SlotType type, long ticks) {
		long remaining = replacementRemaining(godfather, slots, type);
		if (remaining <= 0L) return;
		long next = Math.max(0L, remaining - ticks);
		if (type == SlotType.MAFIOSO) slots.mafiosoReadyTick = next <= 0L ? 0L : godfather.getWorld().getTime() + next;
		else slots.janitorReadyTick = next <= 0L ? 0L : godfather.getWorld().getTime() + next;
		syncRecruitCooldown(godfather, slots, type, next);
	}

	private static void syncRecruitCooldowns(ServerPlayerEntity godfather) {
		Slots slots = slotsByGodfather.get(godfather.getUuid());
		if (slots == null) return;
		syncRecruitCooldown(godfather, slots, SlotType.MAFIOSO, replacementRemaining(godfather, slots, SlotType.MAFIOSO));
		syncRecruitCooldown(godfather, slots, SlotType.JANITOR, replacementRemaining(godfather, slots, SlotType.JANITOR));
	}

	private static void syncRecruitCooldown(ServerPlayerEntity godfather, Slots slots, SlotType type, long remaining) {
		String key = type == SlotType.MAFIOSO
			? AbilityCooldownPayload.GODFATHER_RECRUIT_MAFIOSO
			: AbilityCooldownPayload.GODFATHER_RECRUIT_JANITOR;
		if (remaining <= 0L) AbilityCooldownSync.clear(godfather, key);
		else AbilityCooldownSync.send(godfather, key, remaining,
			(long) GexpressConfig.getMafiaReplacementCooldownSeconds() * 20L, false);
	}

	private static void syncJanitorCleanCooldown(ServerPlayerEntity janitor) {
		long remaining = janitorCleanRemaining(janitor);
		if (remaining <= 0L) AbilityCooldownSync.clear(janitor, AbilityCooldownPayload.JANITOR_CLEAN);
		else AbilityCooldownSync.send(janitor, AbilityCooldownPayload.JANITOR_CLEAN, remaining,
			(long) GexpressConfig.getJanitorCleanCooldownSeconds() * 20L, false);
	}

	private static void syncFamily(ServerWorld world, UUID godfatherId) {
		Set<UUID> members = familyIds(godfatherId);
		for (UUID id : members) {
			ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(id);
			if (player != null) sync(player, members);
		}
	}

	private static void sync(ServerPlayerEntity player) {
		if (player == null || !(player.getWorld() instanceof ServerWorld world)) return;
		UUID godfatherId = isGodfather(player) ? player.getUuid() : godfatherByMember.get(player.getUuid());
		sync(player, familyIds(godfatherId));
	}

	private static void sync(ServerPlayerEntity player, Set<UUID> members) {
		if (!ServerPlayNetworking.canSend(player, MafiaStatePayload.ID)) return;
		ServerPlayNetworking.send(player, new MafiaStatePayload(new ArrayList<>(members), familyColor(player)));
	}

	private static int familyColor(ServerPlayerEntity player) {
		UUID root = player == null ? null : familyRoot(player.getUuid());
		TakeoverSide side = TakeoverManager.sideForGodfather(root);
		return side == null ? 0x8C8C8C : side.color();
	}

	private static Set<UUID> familyIds(UUID godfatherId) {
		LinkedHashSet<UUID> out = new LinkedHashSet<>();
		if (godfatherId == null) return out;
		out.add(godfatherId);
		Slots slots = slotsByGodfather.get(godfatherId);
		if (slots != null) {
			out.addAll(slots.members(SlotType.MAFIOSO));
			out.addAll(slots.members(SlotType.JANITOR));
		}
		return out;
	}

	private static void sendIntro(ServerPlayerEntity player) {
		if (ServerPlayNetworking.canSend(player, MafiaIntroPayload.ID)) {
			ServerPlayNetworking.send(player, new MafiaIntroPayload(INTRO_TICKS));
		}
	}

	private static void clearAll(World world) {
		slotsByGodfather.clear();
		godfatherByMember.clear();
		previousRoleByMember.clear();
		loadedBulletsByGodfather.clear();
		janitorCleanCooldownUntil.clear();
		pendingRevolverCooldown.clear();
		syncTick = 0;
		if (world instanceof ServerWorld serverWorld) {
			for (ServerPlayerEntity player : serverWorld.getPlayers()) {
				if (ServerPlayNetworking.canSend(player, MafiaStatePayload.ID)) {
					ServerPlayNetworking.send(player, new MafiaStatePayload(List.of()));
				}
				if (ServerPlayNetworking.canSend(player, MafiaAmmoPayload.ID)) {
					ServerPlayNetworking.send(player, new MafiaAmmoPayload(0, GexpressConfig.getGodfatherMaxLoadedBullets()));
				}
			}
		}
	}

	public static TimeState snapshotForTimeRewind() {
		Map<UUID, SlotsSnapshot> slots = new HashMap<>();
		for (Map.Entry<UUID, Slots> entry : slotsByGodfather.entrySet()) {
			slots.put(entry.getKey(), entry.getValue().snapshot());
		}
		return new TimeState(slots, new HashMap<>(godfatherByMember),
			new HashMap<>(previousRoleByMember), new HashMap<>(loadedBulletsByGodfather),
			new HashMap<>(janitorCleanCooldownUntil),
			new HashMap<>(pendingRevolverCooldown));
	}

	public static void restoreForTimeRewind(ServerWorld world, TimeState state) {
		Set<UUID> currentMembers = new LinkedHashSet<>(godfatherByMember.keySet());
		clearAll(world);
		if (state == null) {
			restoreRemovedMembers(world, currentMembers, Map.of());
			return;
		}
		for (Map.Entry<UUID, SlotsSnapshot> entry : state.slotsByGodfather().entrySet()) {
			slotsByGodfather.put(entry.getKey(), Slots.from(entry.getValue()));
		}
		godfatherByMember.putAll(state.godfatherByMember());
		previousRoleByMember.putAll(state.previousRoleByMember());
		loadedBulletsByGodfather.putAll(state.loadedBulletsByGodfather());
		janitorCleanCooldownUntil.putAll(state.janitorCleanCooldownUntil());
		pendingRevolverCooldown.putAll(state.pendingRevolverCooldown());
		restoreRemovedMembers(world, currentMembers, previousRoleByMember);
		restoreActiveMemberRoles(world);
		for (UUID godfatherId : slotsByGodfather.keySet()) syncFamily(world, godfatherId);
		for (ServerPlayerEntity player : world.getPlayers(MafiaManager::isGodfather)) syncAmmo(player);
	}

	private static int startingLoadedBullets() {
		return Math.min(GexpressConfig.getGodfatherStartingBullets(), GexpressConfig.getGodfatherMaxLoadedBullets());
	}

	private static void syncAmmo(ServerPlayerEntity player) {
		if (player == null || !ServerPlayNetworking.canSend(player, MafiaAmmoPayload.ID)) return;
		int max = GexpressConfig.getGodfatherMaxLoadedBullets();
		int loaded = isGodfather(player) ? Math.min(max, Math.max(0,
			loadedBulletsByGodfather.getOrDefault(player.getUuid(), 0))) : 0;
		ServerPlayNetworking.send(player, new MafiaAmmoPayload(loaded, max));
	}

	private static void restoreRemovedMembers(ServerWorld world, Set<UUID> currentMembers, Map<UUID, Role> previousRoles) {
		for (UUID memberId : currentMembers) {
			if (godfatherByMember.containsKey(memberId)) continue;
			ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(memberId);
			if (player == null || player.getWorld() != world || !isMafiaRole(player)) continue;
			assignRole(player, previousRoles.getOrDefault(memberId, WatheRoles.CIVILIAN));
		}
	}

	private static void restoreActiveMemberRoles(ServerWorld world) {
		for (Map.Entry<UUID, UUID> entry : godfatherByMember.entrySet()) {
			ServerPlayerEntity member = world.getServer().getPlayerManager().getPlayer(entry.getKey());
			if (member == null || member.getWorld() != world) continue;
			Slots slots = slotsByGodfather.get(entry.getValue());
			if (slots == null) continue;
			if (slots.contains(SlotType.MAFIOSO, member.getUuid())) assignRole(member, MapSelectRoles.MAFIOSO);
			if (slots.contains(SlotType.JANITOR, member.getUuid())) assignRole(member, MapSelectRoles.JANITOR);
		}
	}

	private static long secondsCeil(long ticks) {
		return Math.max(1L, (ticks + 19L) / 20L);
	}

	private enum SlotType {
		MAFIOSO("Mafioso"),
		JANITOR("Janitor");

		private final String displayName;

		SlotType(String displayName) {
			this.displayName = displayName;
		}

		private String displayName() {
			return displayName;
		}
	}

	private static final class Slots {
		private final List<UUID> mafiosos = new ArrayList<>();
		private final List<UUID> janitors = new ArrayList<>();
		private long mafiosoReadyTick;
		private long janitorReadyTick;

		private List<UUID> members(SlotType type) {
			return type == SlotType.MAFIOSO ? mafiosos : janitors;
		}

		private boolean contains(SlotType type, UUID playerId) {
			return playerId != null && members(type).contains(playerId);
		}

		private boolean remove(SlotType type, UUID playerId) {
			return playerId != null && members(type).remove(playerId);
		}

		private void add(SlotType type, UUID playerId) {
			if (playerId == null || members(type).contains(playerId)) return;
			if (type == SlotType.MAFIOSO) {
				mafiosos.add(playerId);
				mafiosoReadyTick = 0L;
			} else {
				janitors.add(playerId);
				janitorReadyTick = 0L;
			}
		}

		private SlotsSnapshot snapshot() {
			return new SlotsSnapshot(List.copyOf(mafiosos), List.copyOf(janitors), mafiosoReadyTick, janitorReadyTick);
		}

		private static Slots from(SlotsSnapshot snapshot) {
			Slots slots = new Slots();
			if (snapshot != null) {
				if (snapshot.mafiosos() != null) slots.mafiosos.addAll(snapshot.mafiosos());
				if (snapshot.janitors() != null) slots.janitors.addAll(snapshot.janitors());
				slots.mafiosoReadyTick = snapshot.mafiosoReadyTick();
				slots.janitorReadyTick = snapshot.janitorReadyTick();
			}
			return slots;
		}
	}

	public record TimeState(Map<UUID, SlotsSnapshot> slotsByGodfather, Map<UUID, UUID> godfatherByMember,
			Map<UUID, Role> previousRoleByMember, Map<UUID, Integer> loadedBulletsByGodfather,
			Map<UUID, Long> janitorCleanCooldownUntil,
			Map<UUID, Integer> pendingRevolverCooldown) {}

	public record SlotsSnapshot(List<UUID> mafiosos, List<UUID> janitors,
			long mafiosoReadyTick, long janitorReadyTick) {}
}
