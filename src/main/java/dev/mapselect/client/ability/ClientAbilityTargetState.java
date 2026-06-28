package dev.mapselect.client.ability;
import dev.mapselect.client.game.ClientRoleRevealState;
import dev.mapselect.client.role.copycat.ClientCopycatState;
import dev.mapselect.client.role.painter.ClientPainterState;


import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.registry.MapSelectRoles;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ClientAbilityTargetState {
	private static final int TARGET_GRACE_TICKS = 12;
	private static final int SPEC_GRACE_TICKS = 12;
	private static final double MIN_TARGET_PADDING = 0.15D;
	private static UUID targetId;
	private static int targetColor = 0xFFFFFF;
	private static boolean targetingActive;
	private static UUID bodyTargetId;
	private static int bodyTargetColor = 0xFFFFFF;
	private static boolean bodyTargetingActive;
	private static UUID graceTargetId;
	private static int graceTargetColor = 0xFFFFFF;
	private static int graceTicks;
	private static TargetSpec lastSpec;
	private static int specGraceTicks;
	private static UUID seerSelectionId;
	private static UUID cupidSelectionId;
	private static final Map<UUID, Integer> selectionColors = new HashMap<>();
	private static final Map<UUID, Integer> persistentSelectionColors = new HashMap<>();

	private ClientAbilityTargetState() {}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(ClientAbilityTargetState::tick);
	}

	public static boolean shouldGlow(AbstractClientPlayerEntity player) {
		if (player == null) return false;
		UUID id = player.getUuid();
		return (targetingActive && id.equals(targetId))
			|| selectionColors.containsKey(id)
			|| persistentSelectionColors.containsKey(id);
	}

	public static boolean shouldGlow(PlayerBodyEntity body) {
		return body != null && bodyTargetingActive && body.getUuid().equals(bodyTargetId);
	}

	public static int glowColor() {
		return targetColor;
	}

	public static int glowColor(AbstractClientPlayerEntity player) {
		if (player != null) {
			Integer selectionColor = selectionColors.get(player.getUuid());
			if (selectionColor != null && !player.getUuid().equals(targetId)) return selectionColor;
			Integer persistentColor = persistentSelectionColors.get(player.getUuid());
			if (persistentColor != null && !player.getUuid().equals(targetId)) return persistentColor;
		}
		return targetColor;
	}

	public static int glowColor(PlayerBodyEntity body) {
		return shouldGlow(body) ? bodyTargetColor : 0xFFFFFF;
	}

	public static UUID currentTargetId() {
		return targetId;
	}

	public static void setSeerSelection(UUID targetId) {
		if (seerSelectionId != null) selectionColors.remove(seerSelectionId);
		seerSelectionId = targetId;
		if (seerSelectionId != null) selectionColors.put(seerSelectionId, 0xC77DFF);
	}

	public static void setCupidSelection(UUID targetId) {
		if (cupidSelectionId != null) selectionColors.remove(cupidSelectionId);
		cupidSelectionId = targetId;
		if (cupidSelectionId != null) selectionColors.put(cupidSelectionId, 0xFF77AA);
	}

	public static void setCupidLinkedTargets(Collection<UUID> targetIds) {
		persistentSelectionColors.clear();
		if (targetIds == null) return;
		for (UUID targetId : targetIds) {
			if (targetId != null) persistentSelectionColors.put(targetId, 0xFF9AC7);
		}
	}

	private static void tick(MinecraftClient client) {
		targetId = null;
		targetingActive = false;
		bodyTargetId = null;
		bodyTargetingActive = false;
		if (client == null || client.world == null || client.player == null || client.player.isSpectator()) {
			clearLookTarget();
			clearSelections();
			lastSpec = null;
			specGraceTicks = 0;
			return;
		}
		if (!ClientRoleRevealState.canUseRoleAbility(client)) {
			if (specGraceTicks <= 0) clearLookTarget();
			return;
		}
		TargetSpec bodySpec = bodyTargetSpec(client);
		if (bodySpec != null) {
			PlayerBodyEntity body = findBodyLookTarget(client, bodySpec, isPainterTargeting(client));
			if (body != null) {
				bodyTargetingActive = true;
				bodyTargetId = body.getUuid();
				bodyTargetColor = bodySpec.color();
			}
		}
		TargetSpec spec = targetSpec(client);
		if (spec == null) {
			if (lastSpec == null || specGraceTicks-- <= 0) {
				clearLookTarget();
				return;
			}
			spec = lastSpec;
		} else {
			lastSpec = spec;
			specGraceTicks = SPEC_GRACE_TICKS;
		}
		targetingActive = true;
		AbstractClientPlayerEntity target = findLookTarget(client, spec, isPainterTargeting(client));
		if (target == null) {
			target = graceTarget(client, spec);
			if (target == null) return;
		}
		targetId = target.getUuid();
		targetColor = spec.color();
		graceTargetId = targetId;
		graceTargetColor = targetColor;
		graceTicks = TARGET_GRACE_TICKS;
	}

	private static PlayerBodyEntity findBodyLookTarget(MinecraftClient client, TargetSpec spec, boolean painter) {
		Vec3d start = client.player.getEyePos();
		Vec3d look = client.player.getRotationVec(1.0F).normalize();
		Box search = client.player.getBoundingBox().expand(spec.range());
		PlayerBodyEntity best = null;
		double bestAlong = Double.MAX_VALUE;
		for (PlayerBodyEntity body : client.world.getEntitiesByClass(PlayerBodyEntity.class, search,
				entity -> entity != null && !entity.isRemoved())) {
			if (painter && ClientPainterState.isBodyPainted(body)) continue;
			Vec3d target = body.getPos().add(0.0D, 0.8D, 0.0D);
			Vec3d to = target.subtract(start);
			double along = to.dotProduct(look);
			if (along < 0.0D || along > spec.range() || along >= bestAlong) continue;
			double perpendicularSq = Math.max(0.0D, to.lengthSquared() - along * along);
			if (perpendicularSq > bodyTargetRadiusSquared(spec.padding()) || isBlocked(client, start, target)) continue;
			best = body;
			bestAlong = along;
		}
		return best;
	}

	private static AbstractClientPlayerEntity findLookTarget(MinecraftClient client, TargetSpec spec, boolean painter) {
		Vec3d start = client.player.getEyePos();
		Vec3d end = start.add(client.player.getRotationVec(1.0F).multiply(spec.range()));
		AbstractClientPlayerEntity best = null;
		double bestDistance = Double.MAX_VALUE;
		for (AbstractClientPlayerEntity candidate : client.world.getPlayers()) {
			if (candidate == client.player || candidate.isSpectator() || !candidate.isAlive()
					|| candidate.isInvisible() || candidate.isRemoved()) continue;
			if (painter && ClientPainterState.isPlayerPainted(candidate)) continue;
			Box box = candidate.getBoundingBox().expand(targetPadding(spec.padding()));
			Optional<Vec3d> hit = box.raycast(start, end);
			if (hit.isEmpty() || isBlocked(client, start, hit.get())) continue;
			double distance = start.squaredDistanceTo(hit.get());
			if (distance < bestDistance) {
				best = candidate;
				bestDistance = distance;
			}
		}
		return best;
	}

	private static AbstractClientPlayerEntity graceTarget(MinecraftClient client, TargetSpec spec) {
		if (graceTargetId == null || graceTicks <= 0) {
			clearLookTarget();
			return null;
		}
		AbstractClientPlayerEntity target = playerById(client, graceTargetId);
		if (target == null || !canKeepGraceTarget(client, target, spec)) {
			clearLookTarget();
			return null;
		}
		graceTicks--;
		targetColor = graceTargetColor;
		return target;
	}

	private static AbstractClientPlayerEntity playerById(MinecraftClient client, UUID id) {
		if (client == null || client.world == null || id == null) return null;
		for (AbstractClientPlayerEntity player : client.world.getPlayers()) {
			if (id.equals(player.getUuid())) return player;
		}
		return null;
	}

	private static boolean canKeepGraceTarget(MinecraftClient client, AbstractClientPlayerEntity candidate,
			TargetSpec spec) {
		if (candidate == client.player || candidate.isSpectator() || !candidate.isAlive()
				|| candidate.isInvisible() || candidate.isRemoved()) {
			return false;
		}
		Vec3d start = client.player.getEyePos();
		Box box = candidate.getBoundingBox().expand(targetPadding(spec.padding()));
		Vec3d closest = new Vec3d(
			clamp(start.x, box.minX, box.maxX),
			clamp(start.y, box.minY, box.maxY),
			clamp(start.z, box.minZ, box.maxZ)
		);
		if (start.squaredDistanceTo(closest) > spec.range() * spec.range()) return false;
		return !isBlocked(client, start, closest) || !isBlocked(client, start, candidate.getEyePos());
	}

	private static boolean isBlocked(MinecraftClient client, Vec3d start, Vec3d target) {
		HitResult hit = client.world.raycast(new RaycastContext(start, target,
			RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, client.player));
		return hit != null && hit.getType() == HitResult.Type.BLOCK
			&& start.squaredDistanceTo(hit.getPos()) + 1.0E-7D < start.squaredDistanceTo(target);
	}

	private static TargetSpec targetSpec(MinecraftClient client) {
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(client.world);
		Role role = game == null ? null : game.getRole(client.player);
		Identifier id = role == null ? null : role.identifier();
		id = ClientCopycatState.effectiveRoleId(client, id);
		if (id == null) return null;
		if (MapSelectRoles.COPYCAT_ID.equals(id)) {
			return new TargetSpec(GexpressConfig.getCopycatCopyRange(), 0.0D, 0xC9B5FF);
		}
		if (MapSelectRoles.SEER_ID.equals(id)) return new TargetSpec(GexpressConfig.getSeerCompareRange(), 0.25D, 0xC77DFF);
		if (MapSelectRoles.CUPID_ID.equals(id)) return new TargetSpec(GexpressConfig.getCupidRange(), 0.25D, 0xFF77AA);
		if (MapSelectRoles.WARLOCK_ID.equals(id)) return new TargetSpec(4.0D, 0.0D, 0xB94CFF);
		if (MapSelectRoles.VULTURE_ID.equals(id)) return new TargetSpec(3.15D, 0.0D, 0xA8C94A);
		if (MapSelectRoles.TIME_MASTER_ID.equals(id)) return new TargetSpec(GexpressConfig.getTimeMasterFreezeRange(), 0.0D, 0x66D9FF);
		if (MapSelectRoles.MEDIC_ID.equals(id)) return new TargetSpec(4.0D, 0.0D, 0x2CFF72);
		if (MapSelectRoles.TRACKER_ID.equals(id)) return new TargetSpec(GexpressConfig.getTrackerRange(), 0.0D, 0x3E9CFF);
		if (MapSelectRoles.SPY_ID.equals(id)) return new TargetSpec(GexpressConfig.getSpyBugRange(), 0.0D, 0x2E6F9E);
		if (MapSelectRoles.PAINTER_ID.equals(id)) return new TargetSpec(4.0D, 0.25D, 0xC65BFF);
		if (MapSelectRoles.GODFATHER_ID.equals(id)) return new TargetSpec(GexpressConfig.getMafiaRecruitRange(), 0.0D, 0xC5C5C5);
		if (MapSelectRoles.DRACULA_ID.equals(id) || MapSelectRoles.VAMPIRE_ID.equals(id)) {
			return new TargetSpec(3.0D, 0.0D, 0xB81832);
		}
		return null;
	}

	private static TargetSpec bodyTargetSpec(MinecraftClient client) {
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(client.world);
		Role role = game == null ? null : game.getRole(client.player);
		Identifier id = role == null ? null : role.identifier();
		id = ClientCopycatState.effectiveRoleId(client, id);
		if (id == null) return null;
		if (MapSelectRoles.PAINTER_ID.equals(id)) return new TargetSpec(4.0D, 0.35D, 0xC65BFF);
		if (MapSelectRoles.SKINCRAWLER_ID.equals(id)) {
			return new TargetSpec(GexpressConfig.getSkincrawlerRange(), 0.35D, 0xB05A66);
		}
		if (MapSelectRoles.ALTRUIST_ID.equals(id)) return new TargetSpec(GexpressConfig.getAltruistRange(), 0.35D, 0x55FFAA);
		if (MapSelectRoles.JANITOR_ID.equals(id)) return new TargetSpec(GexpressConfig.getJanitorCleanRange(), 0.35D, 0xD7D7D7);
		return null;
	}

	private static boolean isPainterTargeting(MinecraftClient client) {
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(client.world);
		Role role = game == null ? null : game.getRole(client.player);
		Identifier id = role == null ? null : role.identifier();
		id = ClientCopycatState.effectiveRoleId(client, id);
		return MapSelectRoles.PAINTER_ID.equals(id);
	}

	private static void clearSelections() {
		seerSelectionId = null;
		cupidSelectionId = null;
		selectionColors.clear();
		persistentSelectionColors.clear();
	}

	private static void clearLookTarget() {
		graceTargetId = null;
		graceTicks = 0;
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}

	private static double targetPadding(double padding) {
		return Math.max(MIN_TARGET_PADDING, padding);
	}

	private static double bodyTargetRadiusSquared(double padding) {
		double radius = Math.max(0.65D, padding * 2.6D);
		return radius * radius;
	}

	private record TargetSpec(double range, double padding, int color) {}
}
