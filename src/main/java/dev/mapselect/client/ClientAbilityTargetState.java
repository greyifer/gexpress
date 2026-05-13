package dev.mapselect.client;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.registry.MapSelectRoles;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.Identifier;

import java.util.UUID;

public final class ClientAbilityTargetState {
	private static UUID targetId;
	private static int targetColor = 0xFFFFFF;

	private ClientAbilityTargetState() {}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(ClientAbilityTargetState::tick);
	}

	public static boolean shouldGlow(AbstractClientPlayerEntity player) {
		return player != null && player.getUuid().equals(targetId);
	}

	public static int glowColor() {
		return targetColor;
	}

	private static void tick(MinecraftClient client) {
		targetId = null;
		if (client == null || client.world == null || client.player == null || client.player.isSpectator()) return;
		if (!ClientRoleRevealState.canUseRoleAbility(client)) return;
		TargetSpec spec = targetSpec(client);
		if (spec == null) return;
		if (client.crosshairTarget == null || client.crosshairTarget.getType() != HitResult.Type.ENTITY) return;
		if (!(((EntityHitResult) client.crosshairTarget).getEntity() instanceof AbstractClientPlayerEntity target)) return;
		if (target == client.player || target.isSpectator() || !target.isAlive() || target.isInvisible()
				|| target.isRemoved() || !client.player.canSee(target)) return;
		if (client.player.getEyePos().squaredDistanceTo(target.getEyePos()) > spec.range() * spec.range()) return;
		targetId = target.getUuid();
		targetColor = spec.color();
	}

	private static TargetSpec targetSpec(MinecraftClient client) {
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(client.world);
		Role role = game == null ? null : game.getRole(client.player);
		Identifier id = role == null ? null : role.identifier();
		if (id == null) return null;
		if (MapSelectRoles.WARLOCK_ID.equals(id)) return new TargetSpec(4.0D, 0xB94CFF);
		if (MapSelectRoles.VULTURE_ID.equals(id)) return new TargetSpec(3.15D, 0xA8C94A);
		if (MapSelectRoles.TIME_MASTER_ID.equals(id)) return new TargetSpec(GexpressConfig.getTimeMasterFreezeRange(), 0x66D9FF);
		if (MapSelectRoles.MEDIC_ID.equals(id)) return new TargetSpec(4.0D, 0x2CFF72);
		if (MapSelectRoles.TRACKER_ID.equals(id)) return new TargetSpec(GexpressConfig.getTrackerRange(), 0x3E9CFF);
		if (MapSelectRoles.SPY_ID.equals(id)) return new TargetSpec(GexpressConfig.getSpyBugRange(), 0x2E6F9E);
		if (MapSelectRoles.GODFATHER_ID.equals(id)) return new TargetSpec(GexpressConfig.getMafiaRecruitRange(), 0xC5C5C5);
		if (MapSelectRoles.DRACULA_ID.equals(id) || MapSelectRoles.VAMPIRE_ID.equals(id)) {
			return new TargetSpec(3.0D, 0xB81832);
		}
		return null;
	}

	private record TargetSpec(double range, int color) {}
}
