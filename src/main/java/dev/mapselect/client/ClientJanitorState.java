package dev.mapselect.client;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.registry.MapSelectRoles;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public final class ClientJanitorState {
	private static final double BODY_LOOK_RADIUS_SQUARED = 2.25D;
	private static int lookedBodyId = -1;

	private ClientJanitorState() {}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(ClientJanitorState::tick);
	}

	public static boolean hasTarget() {
		return lookedBodyId >= 0;
	}

	public static boolean shouldGlow(Entity entity) {
		return entity != null && entity.getId() == lookedBodyId;
	}

	public static int glowColor() {
		return 0xD7D7D7;
	}

	private static void tick(MinecraftClient client) {
		lookedBodyId = findLookedBodyId(client);
	}

	private static int findLookedBodyId(MinecraftClient client) {
		if (client == null || client.player == null || client.world == null
				|| !ClientRoleRevealState.canUseRoleAbility(client)
				|| !MapSelectRoles.JANITOR_ID.equals(localRoleId(client))) {
			return -1;
		}
		double range = GexpressConfig.getJanitorCleanRange();
		Vec3d eye = client.player.getEyePos();
		Vec3d look = client.player.getRotationVec(1.0F).normalize();
		PlayerBodyEntity best = null;
		double bestAlong = Double.MAX_VALUE;
		Box box = client.player.getBoundingBox().expand(range);
		for (PlayerBodyEntity body : client.world.getEntitiesByClass(PlayerBodyEntity.class, box,
				entity -> entity != null && !entity.isRemoved())) {
			Vec3d to = body.getPos().add(0.0D, 0.8D, 0.0D).subtract(eye);
			double along = to.dotProduct(look);
			if (along < 0.0D || along > range || along >= bestAlong) continue;
			double perpendicularSq = Math.max(0.0D, to.lengthSquared() - along * along);
			if (perpendicularSq > BODY_LOOK_RADIUS_SQUARED || !client.player.canSee(body)) continue;
			best = body;
			bestAlong = along;
		}
		return best == null ? -1 : best.getId();
	}

	private static Identifier localRoleId(MinecraftClient client) {
		try {
			GameWorldComponent game = GameWorldComponent.KEY.getNullable(client.world);
			Role role = game == null ? null : game.getRole(client.player);
			return role == null ? null : role.identifier();
		} catch (Throwable ignored) {
			return null;
		}
	}
}
