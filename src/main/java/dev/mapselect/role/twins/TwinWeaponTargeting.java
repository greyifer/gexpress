package dev.mapselect.role.twins;

import dev.doctor4t.wathe.game.GameFunctions;
import dev.mapselect.entity.TwinFakePlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.util.hit.HitResult;

public final class TwinWeaponTargeting {
	private TwinWeaponTargeting() {}

	public static HitResult findTarget(PlayerEntity user, float range) {
		return ProjectileUtil.getCollision(user, entity -> {
			java.util.UUID owner = entity instanceof TwinFakePlayerEntity body ? body.ownerId().orElse(null)
				: user.getWorld().isClient ? dev.mapselect.client.role.twins.ClientTwinsBodies.owner(entity) : null;
			if (owner != null) return entity.isAlive() && !owner.equals(user.getUuid());
			if (entity instanceof PlayerEntity player) {
				return GameFunctions.isPlayerAliveAndSurvival(player);
			}
			if (!(entity instanceof TwinFakePlayerEntity body) || !body.isAlive() || body.isRemoved()) {
				return false;
			}
			return body.ownerId().filter(ownerId -> !ownerId.equals(user.getUuid())).isPresent();
		}, range);
	}
}
