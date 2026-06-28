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
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.game.DeadPlayerStatus;
import dev.mapselect.game.GexpressGameModes;
import dev.mapselect.network.ability.AbilityCooldownPayload;
import dev.mapselect.network.ability.AbilityCooldownSync;
import dev.mapselect.network.role.mafia.MafiaActionPayload;
import dev.mapselect.network.role.mafia.MafiaAmmoPayload;
import dev.mapselect.network.role.mafia.MafiaIntroPayload;
import dev.mapselect.network.role.mafia.MafiaStatePayload;
import dev.mapselect.registry.MapSelectItems;
import dev.mapselect.registry.MapSelectRoles;
import dev.mapselect.role.AbilityTargeting;
import dev.mapselect.role.NeutralWinManager;
import dev.mapselect.role.PassiveMoney;
import dev.mapselect.role.spy.SpyManager;
import dev.mapselect.role.pelican.PelicanManager;
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
	private static final Map<UUID, PickpocketHold> activePickpockets = new HashMap<>();
	private static final List<DelayedMoneyNotice> delayedMoneyNotices = new ArrayList<>();
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
		if (player == null || !(player.getWorld() instanceof ServerWorld)) return;
		if (action == MafiaActionPayload.RECRUIT_MAFIOSO) {
			tryRecruit(player, SlotType.MAFIOSO);
		} else if (action == MafiaActionPayload.RECRUIT_JANITOR) {
			tryRecruit(player, SlotType.JANITOR);
		} else if (action == MafiaActionPayload.RECRUIT_PICKPOCKET) {
			tryRecruit(player, SlotType.PICKPOCKET);
		} else if (action == MafiaActionPayload.RECRUIT_BURGLAR) {
			tryRecruit(player, SlotType.BURGLAR);
		} else if (action == MafiaActionPayload.CLEAN_BODY) {
			tryClean(player);
		} else if (action == MafiaActionPayload.PICKPOCKET_START) {
			startPickpocket(player);
		} else if (action == MafiaActionPayload.PICKPOCKET_STOP) {
			finishPickpocket(player, false);
		}
	}

	private static void tryRecruit(ServerPlayerEntity godfather, SlotType type) {
		if (!canUseHere(godfather.getWorld(), godfather) || !isGodfather(godfather)
				|| PelicanManager.isStashed(godfather)
				|| (!GexpressTestState.isRoleTester(godfather) && !GameFunctions.isPlayerAliveAndSurvival(godfather))) {
			return;
		}
		boolean creativeBypass = GexpressTestState.hasCreativeAbilityBypass(godfather);
		Slots slots = slotsByGodfather.computeIfAbsent(godfather.getUuid(), id -> new Slots());
		if (livingFamilyCount(godfather.getServerWorld(), godfather.getUuid(), slots) >= 3) {
			godfather.sendMessage(Text.literal("The family already has 3 living members.").formatted(Formatting.GRAY), true);
			return;
		}
		int limit = memberLimit(godfather, type);
		if (occupiedMemberCount(slots, type) >= limit) {
			godfather.sendMessage(Text.literal(type.displayName() + " slots are full.").formatted(Formatting.GRAY), true);
			return;
		}
		long remaining = creativeBypass ? 0L : replacementRemaining(godfather, slots, type);
		if (!creativeBypass && remaining > 0L) {
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
		Role newRole = switch (type) {
			case MAFIOSO -> MapSelectRoles.MAFIOSO;
			case JANITOR -> MapSelectRoles.JANITOR;
			case PICKPOCKET -> MapSelectRoles.PICKPOCKET;
			case BURGLAR -> MapSelectRoles.BURGLAR;
		};
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
				|| PelicanManager.isStashed(janitor)
				|| (!GexpressTestState.isRoleTester(janitor) && !GameFunctions.isPlayerAliveAndSurvival(janitor))) {
			return;
		}
		boolean creativeBypass = GexpressTestState.hasCreativeAbilityBypass(janitor);
		long remaining = creativeBypass ? 0L : janitorCleanRemaining(janitor);
		if (!creativeBypass && remaining > 0L) {
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
		if (!creativeBypass && cleanCooldown > 0) {
			janitorCleanCooldownUntil.put(janitor.getUuid(), janitor.getWorld().getTime() + cleanCooldown);
			AbilityCooldownSync.send(janitor, AbilityCooldownPayload.JANITOR_CLEAN, cleanCooldown, cleanCooldown, false);
		} else {
			AbilityCooldownSync.clear(janitor, AbilityCooldownPayload.JANITOR_CLEAN);
		}
		janitor.playSoundToPlayer(SoundEvents.BLOCK_WOOL_BREAK, SoundCategory.PLAYERS, 0.85F, 0.65F);
		janitor.sendMessage(Text.literal("Body cleaned.").formatted(Formatting.DARK_GRAY), true);
	}

	private static void startPickpocket(ServerPlayerEntity thief) {
		if (!canUseHere(thief.getWorld(), thief) || !isPickpocket(thief)
				|| PelicanManager.isStashed(thief)
				|| (!GexpressTestState.isRoleTester(thief) && !GameFunctions.isPlayerAliveAndSurvival(thief))) {
			return;
		}
		if (activePickpockets.containsKey(thief.getUuid())) return;
		boolean testing = GexpressTestState.isRoleTester(thief);
		ServerPlayerEntity target = AbilityTargeting.findLookTarget(thief, thief.getServerWorld().getPlayers(),
			GexpressConfig.getPickpocketRange(), 0.45D, true,
			candidate -> candidate != thief
				&& !isMafiaRole(candidate)
				&& !PelicanManager.isStashed(candidate)
				&& (testing ? !candidate.isSpectator() : DeadPlayerStatus.isLivingRoundParticipant(candidate)));
		if (target == null) {
			thief.sendMessage(Text.literal("No pocket close enough.").formatted(Formatting.GRAY), true);
			return;
		}
		long now = thief.getWorld().getTime();
		int maxTicks = GexpressConfig.getPickpocketMaxHoldSeconds() * 20;
		activePickpockets.put(thief.getUuid(), new PickpocketHold(target.getUuid(), now, now));
		AbilityCooldownSync.send(thief, AbilityCooldownPayload.PICKPOCKET_STEAL, maxTicks, maxTicks, true);
		thief.sendMessage(Text.literal("Stealing from " + target.getName().getString() + "...")
			.formatted(Formatting.DARK_GRAY), true);
	}

	private static void tickPickpockets(ServerWorld world) {
		if (!delayedMoneyNotices.isEmpty()) {
			long now = world.getTime();
			delayedMoneyNotices.removeIf(notice -> {
				if (now < notice.dueTick()) return false;
				ServerPlayerEntity target = world.getServer().getPlayerManager().getPlayer(notice.playerId());
				if (target != null && target.getWorld() == world) {
					target.sendMessage(Text.literal("You notice " + notice.amount() + " coins are missing.")
						.formatted(Formatting.RED), true);
				}
				return true;
			});
		}
		if (activePickpockets.isEmpty()) return;
		List<UUID> active = new ArrayList<>(activePickpockets.keySet());
		for (UUID thiefId : active) {
			ServerPlayerEntity thief = world.getServer().getPlayerManager().getPlayer(thiefId);
			PickpocketHold hold = activePickpockets.get(thiefId);
			if (thief == null || hold == null || thief.getWorld() != world || !isPickpocket(thief)
					|| PelicanManager.isStashed(thief)) {
				finishPickpocket(world, thiefId, true);
				continue;
			}
			ServerPlayerEntity target = world.getServer().getPlayerManager().getPlayer(hold.targetId());
			long elapsed = Math.max(0L, world.getTime() - hold.startTick());
			if (target == null || target.getWorld() != world || !DeadPlayerStatus.isLivingRoundParticipant(target)
					|| thief.squaredDistanceTo(target) > GexpressConfig.getPickpocketRange() * GexpressConfig.getPickpocketRange()
					|| elapsed >= (long) GexpressConfig.getPickpocketMaxHoldSeconds() * 20L) {
				finishPickpocket(thief, true);
				continue;
			}
			if (elapsed >= 40L && world.getTime() - hold.lastWarningTick() >= 20L) {
				hold.lastWarningTick(world.getTime());
				target.sendMessage(Text.literal("Someone is going through your pockets.")
					.formatted(Formatting.YELLOW), true);
			}
		}
	}

	private static void finishPickpocket(ServerPlayerEntity thief, boolean forced) {
		if (thief == null) return;
		finishPickpocket(thief.getServerWorld(), thief.getUuid(), forced);
	}

	private static void finishPickpocket(ServerWorld world, UUID thiefId, boolean forced) {
		PickpocketHold hold = activePickpockets.remove(thiefId);
		if (hold == null || world == null) return;
		ServerPlayerEntity thief = world.getServer().getPlayerManager().getPlayer(thiefId);
		ServerPlayerEntity target = world.getServer().getPlayerManager().getPlayer(hold.targetId());
		if (thief != null) AbilityCooldownSync.clear(thief, AbilityCooldownPayload.PICKPOCKET_STEAL);
		if (target == null || target.getWorld() != world) return;
		long elapsed = Math.max(0L, world.getTime() - hold.startTick());
		if (elapsed < 10L && !forced) return;
		int seconds = Math.max(1, (int) Math.ceil(elapsed / 20.0D));
		int wanted = Math.min(seconds, GexpressConfig.getPickpocketMaxHoldSeconds())
			* GexpressConfig.getPickpocketCoinsPerSecond();
		PlayerShopComponent targetShop = PlayerShopComponent.KEY.get(target);
		int stolen = Math.min(Math.max(0, targetShop.balance), Math.max(0, wanted));
		if (stolen <= 0) {
			if (thief != null) thief.sendMessage(Text.literal("Nothing to steal.").formatted(Formatting.GRAY), true);
			return;
		}
		targetShop.setBalance(targetShop.balance - stolen);
		PlayerShopComponent.KEY.sync(target);
		List<ServerPlayerEntity> recipients = familyRecipients(world, thiefId);
		if (recipients.isEmpty()) return;
		int share = stolen / recipients.size();
		int remainder = stolen % recipients.size();
		for (int i = 0; i < recipients.size(); i++) {
			ServerPlayerEntity recipient = recipients.get(i);
			int amount = share + (i == 0 ? remainder : 0);
			PlayerShopComponent shop = PlayerShopComponent.KEY.get(recipient);
			shop.addToBalance(amount);
			PlayerShopComponent.KEY.sync(recipient);
			recipient.sendMessage(Text.literal("Pickpocket split: +" + amount + " coins.")
				.formatted(Formatting.DARK_GRAY), true);
		}
		int subtleThreshold = GexpressConfig.getPickpocketCoinsPerSecond() * 3;
		if (stolen <= subtleThreshold) {
			delayedMoneyNotices.add(new DelayedMoneyNotice(target.getUuid(), world.getTime() + 200L, stolen));
		} else {
			target.sendMessage(Text.literal("You were pickpocketed for " + stolen + " coins!")
				.formatted(Formatting.RED), true);
		}
		if (thief != null) SpyManager.recordInteraction(thief, target);
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
			if ((isMafioso(attacker) || isJanitor(attacker) || isPickpocket(attacker) || isBurglar(attacker))
					&& GameConstants.DeathReasons.GUN.equals(reason)) {
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
		if (victim instanceof ServerPlayerEntity dead
				&& (isMafioso(dead) || isJanitor(dead) || isPickpocket(dead) || isBurglar(dead))) {
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
			shooter.playSoundToPlayer(SoundEvents.UI_BUTTON_CLICK.value(),
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
		player.playSoundToPlayer(SoundEvents.ITEM_CROSSBOW_LOADING_END.value(),
			SoundCategory.PLAYERS, 0.75F, 0.75F);
		player.sendMessage(Text.literal("Loaded bullet (" + (loaded + 1) + "/" + max + ").")
			.formatted(Formatting.GRAY), true);
		player.playerScreenHandler.syncState();
		syncAmmo(player);
		return true;
	}

	public static void afterGunShot(ServerPlayerEntity shooter) {
		if (shooter == null) return;
		if (GexpressTestState.hasCreativeAbilityBypass(shooter)) {
			pendingRevolverCooldown.remove(shooter.getUuid());
			suppressMafiaRevolverCooldown(shooter);
			return;
		}
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

		tickPickpockets(world);

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
			} else if (isMafioso(player) || isPickpocket(player) || isBurglar(player)) {
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
		if (slots.remove(SlotType.PICKPOCKET, member.getUuid())) {
			slots.pickpocketReadyTick = ready;
		}
		if (slots.remove(SlotType.BURGLAR, member.getUuid())) {
			slots.burglarReadyTick = ready;
		}
		lockAllRecruitSlots(slots, ready);
		ServerPlayerEntity godfather = member.getServer().getPlayerManager().getPlayer(godfatherId);
		if (godfather != null) {
			godfather.sendMessage(Text.literal("A family slot will reopen in "
				+ GexpressConfig.getMafiaReplacementCooldownSeconds() + "s.").formatted(Formatting.GRAY), true);
			syncRecruitCooldowns(godfather);
		}
		syncFamily(member.getServerWorld(), godfatherId);
	}

	private static void lockAllRecruitSlots(Slots slots, long readyTick) {
		if (slots == null || readyTick <= 0L) return;
		slots.mafiosoReadyTick = Math.max(slots.mafiosoReadyTick, readyTick);
		slots.janitorReadyTick = Math.max(slots.janitorReadyTick, readyTick);
		slots.pickpocketReadyTick = Math.max(slots.pickpocketReadyTick, readyTick);
		slots.burglarReadyTick = Math.max(slots.burglarReadyTick, readyTick);
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
		for (UUID memberId : slots.members(SlotType.PICKPOCKET)) {
			restoreMemberAfterGodfatherDeath(godfather.getServerWorld(), memberId);
		}
		for (UUID memberId : slots.members(SlotType.BURGLAR)) {
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
			candidate -> !PelicanManager.isStashed(candidate) && DeadPlayerStatus.isLivingRoundParticipant(candidate));
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
		return isGodfather(player) || isMafioso(player) || isJanitor(player) || isPickpocket(player) || isBurglar(player);
	}

	public static boolean isMafiaRole(Role role) {
		if (role == null || role.identifier() == null) return false;
		Identifier id = role.identifier();
		return MapSelectRoles.GODFATHER_ID.equals(id)
			|| MapSelectRoles.MAFIOSO_ID.equals(id)
			|| MapSelectRoles.JANITOR_ID.equals(id)
			|| MapSelectRoles.PICKPOCKET_ID.equals(id)
			|| MapSelectRoles.BURGLAR_ID.equals(id);
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

	public static boolean isPickpocket(PlayerEntity player) {
		Role role = currentRole(player);
		return role != null && MapSelectRoles.PICKPOCKET_ID.equals(role.identifier());
	}

	public static boolean isBurglar(PlayerEntity player) {
		Role role = currentRole(player);
		return role != null && MapSelectRoles.BURGLAR_ID.equals(role.identifier());
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

	private static int livingFamilyCount(ServerWorld world, UUID godfatherId, Slots slots) {
		int count = isLivingPlayer(world, godfatherId) ? 1 : 0;
		for (SlotType type : SlotType.values()) {
			count += livingMemberCount(world, slots, type);
		}
		return count;
	}

	private static int occupiedMemberCount(Slots slots, SlotType type) {
		return slots == null ? 0 : slots.members(type).size();
	}

	private static long replacementRemaining(ServerPlayerEntity godfather, Slots slots, SlotType type) {
		long ready = switch (type) {
			case MAFIOSO -> slots.mafiosoReadyTick;
			case JANITOR -> slots.janitorReadyTick;
			case PICKPOCKET -> slots.pickpocketReadyTick;
			case BURGLAR -> slots.burglarReadyTick;
		};
		if (ready <= 0L) return 0L;
		long remaining = ready - godfather.getWorld().getTime();
		if (remaining <= 0L) {
			if (type == SlotType.MAFIOSO) slots.mafiosoReadyTick = 0L;
			else if (type == SlotType.JANITOR) slots.janitorReadyTick = 0L;
			else if (type == SlotType.PICKPOCKET) slots.pickpocketReadyTick = 0L;
			else slots.burglarReadyTick = 0L;
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
				reduceRecruitCooldown(player, slots, SlotType.PICKPOCKET, ticks);
				reduceRecruitCooldown(player, slots, SlotType.BURGLAR, ticks);
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
		else if (type == SlotType.JANITOR) slots.janitorReadyTick = next <= 0L ? 0L : godfather.getWorld().getTime() + next;
		else if (type == SlotType.PICKPOCKET) slots.pickpocketReadyTick = next <= 0L ? 0L : godfather.getWorld().getTime() + next;
		else slots.burglarReadyTick = next <= 0L ? 0L : godfather.getWorld().getTime() + next;
		syncRecruitCooldown(godfather, slots, type, next);
	}

	private static void syncRecruitCooldowns(ServerPlayerEntity godfather) {
		Slots slots = slotsByGodfather.get(godfather.getUuid());
		if (slots == null) return;
		syncRecruitCooldown(godfather, slots, SlotType.MAFIOSO, replacementRemaining(godfather, slots, SlotType.MAFIOSO));
		syncRecruitCooldown(godfather, slots, SlotType.JANITOR, replacementRemaining(godfather, slots, SlotType.JANITOR));
		syncRecruitCooldown(godfather, slots, SlotType.PICKPOCKET,
			replacementRemaining(godfather, slots, SlotType.PICKPOCKET));
		syncRecruitCooldown(godfather, slots, SlotType.BURGLAR,
			replacementRemaining(godfather, slots, SlotType.BURGLAR));
	}

	private static void syncRecruitCooldown(ServerPlayerEntity godfather, Slots slots, SlotType type, long remaining) {
		String key = switch (type) {
			case MAFIOSO -> AbilityCooldownPayload.GODFATHER_RECRUIT_MAFIOSO;
			case JANITOR -> AbilityCooldownPayload.GODFATHER_RECRUIT_JANITOR;
			case PICKPOCKET -> AbilityCooldownPayload.GODFATHER_RECRUIT_PICKPOCKET;
			case BURGLAR -> AbilityCooldownPayload.GODFATHER_RECRUIT_BURGLAR;
		};
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
		if (player == null || !(player.getWorld() instanceof ServerWorld)) return;
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
			out.addAll(slots.members(SlotType.PICKPOCKET));
			out.addAll(slots.members(SlotType.BURGLAR));
		}
		return out;
	}

	private static List<ServerPlayerEntity> familyRecipients(ServerWorld world, UUID thiefId) {
		List<ServerPlayerEntity> recipients = new ArrayList<>();
		Set<UUID> family = familyIds(familyRoot(thiefId));
		if (family.isEmpty() && thiefId != null) family = Set.of(thiefId);
		for (UUID id : family) {
			ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(id);
			if (player != null && player.getWorld() == world && DeadPlayerStatus.isLivingRoundParticipant(player)) {
				recipients.add(player);
			}
		}
		if (recipients.isEmpty() && thiefId != null) {
			ServerPlayerEntity thief = world.getServer().getPlayerManager().getPlayer(thiefId);
			if (thief != null && thief.getWorld() == world) recipients.add(thief);
		}
		return recipients;
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
		activePickpockets.clear();
		delayedMoneyNotices.clear();
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
			new HashMap<>(pendingRevolverCooldown),
			new HashMap<>(activePickpockets),
			List.copyOf(delayedMoneyNotices));
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
		activePickpockets.putAll(state.activePickpockets());
		delayedMoneyNotices.addAll(state.delayedMoneyNotices());
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
			if (slots.contains(SlotType.PICKPOCKET, member.getUuid())) assignRole(member, MapSelectRoles.PICKPOCKET);
			if (slots.contains(SlotType.BURGLAR, member.getUuid())) assignRole(member, MapSelectRoles.BURGLAR);
		}
	}

	private static long secondsCeil(long ticks) {
		return Math.max(1L, (ticks + 19L) / 20L);
	}

	private enum SlotType {
		MAFIOSO("Mafioso"),
		JANITOR("Janitor"),
		PICKPOCKET("Pickpocket"),
		BURGLAR("Burglar");

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
		private final List<UUID> pickpockets = new ArrayList<>();
		private final List<UUID> burglars = new ArrayList<>();
		private long mafiosoReadyTick;
		private long janitorReadyTick;
		private long pickpocketReadyTick;
		private long burglarReadyTick;

		private List<UUID> members(SlotType type) {
			return switch (type) {
				case MAFIOSO -> mafiosos;
				case JANITOR -> janitors;
				case PICKPOCKET -> pickpockets;
				case BURGLAR -> burglars;
			};
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
			} else if (type == SlotType.JANITOR) {
				janitors.add(playerId);
				janitorReadyTick = 0L;
			} else if (type == SlotType.PICKPOCKET) {
				pickpockets.add(playerId);
				pickpocketReadyTick = 0L;
			} else {
				burglars.add(playerId);
				burglarReadyTick = 0L;
			}
		}

		private SlotsSnapshot snapshot() {
			return new SlotsSnapshot(List.copyOf(mafiosos), List.copyOf(janitors), List.copyOf(pickpockets),
				List.copyOf(burglars), mafiosoReadyTick, janitorReadyTick, pickpocketReadyTick,
				burglarReadyTick);
		}

		private static Slots from(SlotsSnapshot snapshot) {
			Slots slots = new Slots();
			if (snapshot != null) {
				if (snapshot.mafiosos() != null) slots.mafiosos.addAll(snapshot.mafiosos());
				if (snapshot.janitors() != null) slots.janitors.addAll(snapshot.janitors());
				if (snapshot.pickpockets() != null) slots.pickpockets.addAll(snapshot.pickpockets());
				if (snapshot.burglars() != null) slots.burglars.addAll(snapshot.burglars());
				slots.mafiosoReadyTick = snapshot.mafiosoReadyTick();
				slots.janitorReadyTick = snapshot.janitorReadyTick();
				slots.pickpocketReadyTick = snapshot.pickpocketReadyTick();
				slots.burglarReadyTick = snapshot.burglarReadyTick();
			}
			return slots;
		}
	}

	public record TimeState(Map<UUID, SlotsSnapshot> slotsByGodfather, Map<UUID, UUID> godfatherByMember,
			Map<UUID, Role> previousRoleByMember, Map<UUID, Integer> loadedBulletsByGodfather,
			Map<UUID, Long> janitorCleanCooldownUntil,
			Map<UUID, Integer> pendingRevolverCooldown,
			Map<UUID, PickpocketHold> activePickpockets,
			List<DelayedMoneyNotice> delayedMoneyNotices) {}

	public record SlotsSnapshot(List<UUID> mafiosos, List<UUID> janitors, List<UUID> pickpockets,
			List<UUID> burglars, long mafiosoReadyTick, long janitorReadyTick, long pickpocketReadyTick,
			long burglarReadyTick) {}

	public static final class PickpocketHold {
		private final UUID targetId;
		private final long startTick;
		private long lastWarningTick;

		private PickpocketHold(UUID targetId, long startTick, long lastWarningTick) {
			this.targetId = targetId;
			this.startTick = startTick;
			this.lastWarningTick = lastWarningTick;
		}

		private UUID targetId() {
			return targetId;
		}

		private long startTick() {
			return startTick;
		}

		private long lastWarningTick() {
			return lastWarningTick;
		}

		private void lastWarningTick(long value) {
			lastWarningTick = value;
		}
	}

	public record DelayedMoneyNotice(UUID playerId, long dueTick, int amount) {}
}
