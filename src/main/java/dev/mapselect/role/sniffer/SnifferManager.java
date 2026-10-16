package dev.mapselect.role.sniffer;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.CanSeePoison;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.mapselect.registry.MapSelectRoles;
import dev.mapselect.game.DeadPlayerStatus;
import dev.mapselect.game.RoundLifecycleHooks;
import dev.mapselect.network.ability.AbilityCooldownPayload;
import dev.mapselect.network.ability.AbilityCooldownSync;
import dev.mapselect.network.role.sniffer.SnifferUsePayload;
import dev.mapselect.role.AbilityCooldowns;
import dev.mapselect.role.AbilitySounds;
import dev.mapselect.role.AbilityTargeting;
import dev.mapselect.role.pelican.PelicanManager;
import dev.mapselect.role.veteran.VeteranManager;
import dev.mapselect.testing.GexpressTestState;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SnifferManager {
	public static final int SNIFF_COOLDOWN_TICKS = 45 * 20;
	private static final Map<UUID, Long> COOLDOWN_UNTIL = new ConcurrentHashMap<>();

	private SnifferManager() {}

	public static void register() {
		CanSeePoison.EVENT.register(SnifferManager::canSeePoison);
		PayloadTypeRegistry.playC2S().register(SnifferUsePayload.ID, SnifferUsePayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(SnifferUsePayload.ID,
			(payload, context) -> context.server().execute(() -> trySniff(context.player())));
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
			server.execute(() -> syncCooldown(handler.player)));
		RoundLifecycleHooks.clearWorld(SnifferManager::clear);
	}

	private static void trySniff(ServerPlayerEntity sniffer) {
		if (sniffer == null || !isSniffer(sniffer) || PelicanManager.isStashed(sniffer)) return;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(sniffer.getWorld());
		boolean active = game != null && game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE;
		if (!active && !GexpressTestState.isRoleTester(sniffer)) return;
		if (!GexpressTestState.isRoleTester(sniffer) && !DeadPlayerStatus.isLivingRoundParticipant(sniffer)) return;
		boolean creativeBypass = GexpressTestState.hasCreativeAbilityBypass(sniffer);
		long remaining = creativeBypass ? 0L : AbilityCooldowns.remaining(COOLDOWN_UNTIL, sniffer);
		if (!creativeBypass && remaining > 0L) {
			AbilityCooldownSync.send(sniffer, AbilityCooldownPayload.SNIFFER_SNIFF,
				remaining, SNIFF_COOLDOWN_TICKS, false);
			sniffer.sendMessage(Text.literal("Sniff ready in "
				+ Math.max(1L, AbilityCooldowns.secondsCeil(remaining)) + "s.")
				.formatted(Formatting.GRAY), true);
			return;
		}

		boolean testing = GexpressTestState.isRoleTester(sniffer);
		ServerPlayerEntity target = AbilityTargeting.findLookTarget(sniffer,
			sniffer.getServerWorld().getPlayers(), 3.5D, 0.15D, true,
			candidate -> candidate != sniffer && !PelicanManager.isStashed(candidate)
				&& (testing ? !candidate.isSpectator() : DeadPlayerStatus.isLivingRoundParticipant(candidate)));
		if (target == null) {
			sniffer.sendMessage(Text.literal(AbilityTargeting.lookTargetFailure(sniffer,
				sniffer.getServerWorld().getPlayers(), 3.5D, 0.15D, true,
				candidate -> candidate != sniffer && !PelicanManager.isStashed(candidate)
					&& (testing ? !candidate.isSpectator() : DeadPlayerStatus.isLivingRoundParticipant(candidate)),
				"sniff")).formatted(Formatting.GRAY), true);
			return;
		}
		if (VeteranManager.interceptAbilityInteraction(sniffer, target)) return;

		Set<String> found = new LinkedHashSet<>();
		for (int slot = 0; slot < target.getInventory().size(); slot++) {
			ItemStack stack = target.getInventory().getStack(slot);
			if (stack == null || stack.isEmpty()
					|| !SnifferContrabandPolicy.isContraband(Registries.ITEM.getId(stack.getItem()))) continue;
			found.add(stack.getName().getString());
		}
		AbilitySounds.playTo(sniffer, SoundEvents.BLOCK_SCULK_SENSOR_CLICKING,
			SoundCategory.PLAYERS, 0.6F, found.isEmpty() ? 1.4F : 0.75F);
		if (found.isEmpty()) {
			sniffer.sendMessage(Text.literal("No contraband detected on " + target.getName().getString() + ".")
				.formatted(Formatting.GREEN), true);
		} else {
			sniffer.sendMessage(Text.literal("Contraband on " + target.getName().getString() + ": "
				+ String.join(", ", found) + ".").formatted(Formatting.RED), true);
		}
		if (creativeBypass) {
			AbilityCooldownSync.clear(sniffer, AbilityCooldownPayload.SNIFFER_SNIFF);
		} else {
			AbilityCooldowns.start(COOLDOWN_UNTIL, sniffer, SNIFF_COOLDOWN_TICKS);
			AbilityCooldownSync.send(sniffer, AbilityCooldownPayload.SNIFFER_SNIFF,
				SNIFF_COOLDOWN_TICKS, SNIFF_COOLDOWN_TICKS, false);
		}
	}

	public static long reduceCooldown(ServerPlayerEntity player, long ticks) {
		long next = AbilityCooldowns.reduce(COOLDOWN_UNTIL, player, ticks);
		if (next <= 0L) {
			if (player != null) AbilityCooldownSync.clear(player, AbilityCooldownPayload.SNIFFER_SNIFF);
		} else {
			AbilityCooldownSync.send(player, AbilityCooldownPayload.SNIFFER_SNIFF,
				next, SNIFF_COOLDOWN_TICKS, false);
		}
		return next;
	}

	private static void syncCooldown(ServerPlayerEntity player) {
		if (player == null || !isSniffer(player)) return;
		long remaining = AbilityCooldowns.remaining(COOLDOWN_UNTIL, player);
		if (remaining <= 0L) {
			AbilityCooldownSync.clear(player, AbilityCooldownPayload.SNIFFER_SNIFF);
		} else {
			AbilityCooldownSync.send(player, AbilityCooldownPayload.SNIFFER_SNIFF,
				remaining, SNIFF_COOLDOWN_TICKS, false);
		}
	}

	private static void clear(World world) {
		COOLDOWN_UNTIL.clear();
		if (world instanceof net.minecraft.server.world.ServerWorld serverWorld) {
			for (ServerPlayerEntity player : serverWorld.getPlayers()) {
				AbilityCooldownSync.clear(player, AbilityCooldownPayload.SNIFFER_SNIFF);
			}
		}
	}

	private static boolean canSeePoison(PlayerEntity player) {
		if (player == null || player.getWorld() == null) return false;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(player.getWorld());
		if (game == null) return false;
		return isSniffer(player);
	}

	private static boolean isSniffer(PlayerEntity player) {
		if (player == null || player.getWorld() == null) return false;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(player.getWorld());
		Role role = game == null ? null : game.getRole(player);
		return role != null && (MapSelectRoles.SNIFFER_ID.equals(role.identifier())
			|| dev.mapselect.role.copycat.CopycatManager.isCopyingRole(player, MapSelectRoles.SNIFFER_ID));
	}
}
