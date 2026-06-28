package dev.mapselect.role.medic;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.AllowPlayerDeath;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.mapselect.game.DeadPlayerStatus;
import dev.mapselect.network.role.medic.MedicShieldFlashPayload;
import dev.mapselect.network.role.medic.MedicShieldUsePayload;
import dev.mapselect.registry.MapSelectRoles;
import dev.mapselect.role.AbilitySounds;
import dev.mapselect.role.AbilityTargeting;
import dev.mapselect.role.spy.SpyManager;
import dev.mapselect.role.pelican.PelicanManager;
import dev.mapselect.testing.GexpressTestState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class MedicShieldManager {
	private static final double SHIELD_RANGE = 4.0D;

	private MedicShieldManager() {}

	public static void register() {
		PayloadTypeRegistry.playC2S().register(MedicShieldUsePayload.ID, MedicShieldUsePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(MedicShieldFlashPayload.ID, MedicShieldFlashPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(MedicShieldUsePayload.ID,
			(payload, context) -> context.server().execute(() -> tryShield(context.player())));
		AllowPlayerDeath.EVENT.register(MedicShieldManager::allowDeath);
		GameEvents.ON_FINISH_INITIALIZE.register((world, game) -> clearWorld(world));
		GameEvents.ON_FINISH_FINALIZE.register((world, game) -> clearWorld(world));
		ServerTickEvents.END_WORLD_TICK.register(MedicShieldManager::tick);
	}

	private static void tryShield(ServerPlayerEntity medic) {
		if (medic == null || medic.getWorld().isClient) return;
		if (PelicanManager.isStashed(medic)) return;
		ServerPlayerEntity target = findLookTarget(medic);
		if (target == null) {
			medic.sendMessage(Text.literal("No living player close enough to shield."), true);
			return;
		}
		if (!canUseMedicHere(medic.getWorld(), medic) || !isMedic(medic)) return;
		if (!GexpressTestState.isRoleTester(medic) && !GameFunctions.isPlayerAliveAndSurvival(medic)) {
			return;
		}
		if (target == medic) return;
		if (!isPlayableForMedic(medic, medic) || !isPlayableForMedic(target, medic)) {
			return;
		}

		MedicShieldComponent comp = MedicShieldComponent.KEY.getNullable(medic.getWorld());
		if (comp == null) return;

		boolean creativeBypass = GexpressTestState.hasCreativeAbilityBypass(medic);
		long remaining = creativeBypass ? 0L : comp.cooldownRemainingTicks(medic.getUuid());
		if (!creativeBypass && remaining > 0L) {
			medic.sendMessage(Text.literal("Shield ready in " + secondsCeil(remaining) + "s."), true);
			return;
		}

		UUID currentMedic = comp.getMedicForTarget(target.getUuid());
		if (currentMedic != null && !currentMedic.equals(medic.getUuid())) {
			medic.sendMessage(Text.literal(target.getName().getString() + " is already shielded."), true);
			return;
		}

		if (!comp.assignShield(medic.getUuid(), target.getUuid())) {
			medic.sendMessage(Text.literal("Could not apply shield."), true);
			return;
		}
		if (creativeBypass) comp.reduceCooldown(medic.getUuid(), Long.MAX_VALUE);

		AbilitySounds.playTo(List.of(medic, target), SoundEvents.BLOCK_BEACON_POWER_SELECT,
			SoundCategory.PLAYERS, 0.75F, 1.7F);
		medic.sendMessage(Text.literal("Shielded " + target.getName().getString() + "."), true);
		target.sendMessage(Text.literal("A Medic shield is protecting you."), true);
		SpyManager.recordInteraction(medic, target);
	}

	private static ServerPlayerEntity findLookTarget(ServerPlayerEntity medic) {
		return AbilityTargeting.findLookTarget(medic, medic.getServerWorld().getPlayers(), SHIELD_RANGE, 0.0D, true,
			candidate -> isPlayableForMedic(candidate, medic));
	}

	private static boolean allowDeath(PlayerEntity victim, PlayerEntity killer, Identifier reason) {
		if (victim == null || victim.getWorld().isClient) return true;
		if (!isShieldBreakingHit(reason)) return true;

		MedicShieldComponent comp = MedicShieldComponent.KEY.getNullable(victim.getWorld());
		if (comp == null || !comp.hasShield(victim.getUuid())) return true;

		UUID medicId = comp.getMedicForTarget(victim.getUuid());
		comp.removeShield(victim.getUuid());

		if (victim instanceof ServerPlayerEntity target) {
			ServerPlayerEntity medic = medicId == null || target.getServer() == null
				? null : target.getServer().getPlayerManager().getPlayer(medicId);
			List<ServerPlayerEntity> recipients = medic == null ? List.of(target) : List.of(target, medic);
			AbilitySounds.playTo(recipients, SoundEvents.ITEM_SHIELD_BREAK, SoundCategory.PLAYERS, 1.0F, 0.8F);
			target.sendMessage(Text.literal("Your Medic shield broke."), true);
		}
		flashMedic(victim.getWorld().getServer(), medicId, victim.getUuid(), true);
		return false;
	}

	private static boolean isShieldBreakingHit(Identifier reason) {
		if (GameConstants.DeathReasons.KNIFE.equals(reason) || GameConstants.DeathReasons.GUN.equals(reason)) {
			return true;
		}
		if (reason == null) return false;
		String path = reason.getPath().toLowerCase(Locale.ROOT);
		return path.contains("knife") || path.contains("stab") || path.contains("gun")
			|| path.contains("bullet") || path.contains("revolver");
	}

	private static void clearWorld(World world) {
		MedicShieldComponent comp = MedicShieldComponent.KEY.getNullable(world);
		if (comp != null) comp.clearAll();
	}

	private static void tick(ServerWorld world) {
		if (world.getRegistryKey() != World.OVERWORLD) return;

		MedicShieldComponent comp = MedicShieldComponent.KEY.getNullable(world);
		if (comp == null) return;

		if (!isActiveGame(world) && !GexpressTestState.hasRoleTesters()) {
			comp.clearAll();
			return;
		}

		MinecraftServer server = world.getServer();
		for (UUID targetId : comp.getShieldedTargets()) {
			UUID medicId = comp.getMedicForTarget(targetId);
			ServerPlayerEntity target = server.getPlayerManager().getPlayer(targetId);
			ServerPlayerEntity medic = medicId == null ? null : server.getPlayerManager().getPlayer(medicId);
			if (target == null || medic == null
					|| !canUseMedicHere(world, medic)
					|| !isPlayableForMedic(target, medic)
					|| !isPlayableForMedic(medic, medic)
					|| !isMedic(medic)) {
				comp.removeShield(targetId);
			}
		}
	}

	private static void flashMedic(MinecraftServer server, UUID medicId, UUID targetId, boolean broken) {
		if (server == null || medicId == null || targetId == null) return;
		ServerPlayerEntity medic = server.getPlayerManager().getPlayer(medicId);
		if (medic == null) return;
		ServerPlayNetworking.send(medic, new MedicShieldFlashPayload(targetId, broken));
		medic.sendMessage(Text.literal(broken ? "Your shield was broken." : "Your shield blocked a hit."), true);
	}

	private static boolean isMedic(PlayerEntity player) {
		if (player == null || player.getWorld() == null) return false;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(player.getWorld());
		if (game == null) return false;
		Role role = game.getRole(player);
		return role != null && (MapSelectRoles.MEDIC_ID.equals(role.identifier())
			|| dev.mapselect.role.copycat.CopycatManager.isCopyingRole(player, MapSelectRoles.MEDIC_ID));
	}

	private static boolean isActiveGame(World world) {
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
		return game != null && game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE;
	}

	private static boolean canUseMedicHere(World world, PlayerEntity medic) {
		return isActiveGame(world) || GexpressTestState.isRoleTester(medic);
	}

	private static boolean isPlayableForMedic(PlayerEntity player, PlayerEntity medic) {
		if (GexpressTestState.isRoleTester(medic)) return true;
		if (player instanceof ServerPlayerEntity serverPlayer) {
			return DeadPlayerStatus.isLivingRoundParticipant(serverPlayer);
		}
		return GameFunctions.isPlayerAliveAndSurvival(player);
	}

	private static long secondsCeil(long ticks) {
		return Math.max(1L, (ticks + 19L) / 20L);
	}
}
