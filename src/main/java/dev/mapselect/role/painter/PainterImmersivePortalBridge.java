package dev.mapselect.role.painter;

import dev.mapselect.MapSelect;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import qouteall.imm_ptl.core.api.PortalAPI;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.q_misc_util.my_util.DQuaternion;

import java.util.UUID;

final class PainterImmersivePortalBridge {
	static final String PAINTER_DOORWAY_TAG_PREFIX = "gexpress:painter_doorway";
	private static final double DOORWAY_WIDTH = 1.0D;
	private static final double DOORWAY_HEIGHT = 2.0D;

	private PainterImmersivePortalBridge() {}

	static DoorwayPortalIds spawnDoorwayPortals(ServerWorld world, UUID painterId, Vec3d sourceCenter,
			Direction sourceThrough, Vec3d destinationCenter, Direction destinationThrough) {
		UUID sourceId = spawnDoorwayPortal(world, painterId, sourceCenter, sourceThrough,
			destinationCenter, destinationThrough, "source");
		UUID reverseId = spawnDoorwayPortal(world, painterId, destinationCenter, destinationThrough,
			sourceCenter, sourceThrough, "reverse");
		return new DoorwayPortalIds(sourceId, reverseId);
	}

	private static UUID spawnDoorwayPortal(ServerWorld world, UUID painterId, Vec3d sourceCenter,
			Direction sourceThrough, Vec3d destinationCenter, Direction destinationThrough, String suffix) {
		if (world == null || sourceCenter == null || destinationCenter == null
				|| !isImmersivePortalsAvailable()) {
			return null;
		}
		try {
			Portal portal = Portal.ENTITY_TYPE.create(world);
			if (portal == null) return null;

			Vec3d sourceAxisW = horizontalAxisW(sourceThrough);
			Vec3d destinationAxisW = horizontalAxisW(oppositeHorizontal(destinationThrough));
			Vec3d axisH = new Vec3d(0.0D, 1.0D, 0.0D);

			portal.setOriginPos(sourceCenter);
			portal.setWidth(DOORWAY_WIDTH);
			portal.setHeight(DOORWAY_HEIGHT);
			portal.setAxisW(sourceAxisW);
			portal.setAxisH(axisH);
			portal.setDestinationDimension(world.getRegistryKey());
			portal.setDestination(destinationCenter);
			portal.setScaling(1.0D);
			portal.setOtherSideOrientation(DQuaternion.matrixToQuaternion(destinationAxisW, axisH,
				destinationAxisW.crossProduct(axisH)));
			portal.setTeleportable(true);
			portal.setInteractable(false);
			portal.setCrossPortalCollisionEnabled(false);
			portal.setFuseView(false);
			portal.portalTag = PAINTER_DOORWAY_TAG_PREFIX + ":" + suffix;
			portal.specificPlayerId = painterId;

			PortalAPI.spawnServerEntity(portal);
			return portal.getUuid();
		} catch (Throwable error) {
			MapSelect.LOGGER.warn("Failed to spawn Immersive Portals doorway renderer.", error);
			return null;
		}
	}

	static void removeDoorwayPortal(ServerWorld world, UUID portalId) {
		if (world == null || portalId == null || !isImmersivePortalsAvailable()) return;
		try {
			Entity portal = world.getEntity(portalId);
			if (portal != null) portal.discard();
		} catch (Throwable error) {
			MapSelect.LOGGER.warn("Failed to remove Immersive Portals doorway renderer {}.", portalId, error);
		}
	}

	static void updateDoorwayPortal(ServerWorld world, UUID portalId, Vec3d sourceCenter, Vec3d destinationCenter) {
		if (world == null || portalId == null || sourceCenter == null || destinationCenter == null
				|| !isImmersivePortalsAvailable()) {
			return;
		}
		try {
			Entity entity = world.getEntity(portalId);
			if (entity instanceof Portal portal) {
				portal.setOriginPos(sourceCenter);
				portal.setDestination(destinationCenter);
			}
		} catch (Throwable error) {
			MapSelect.LOGGER.warn("Failed to update Immersive Portals doorway renderer {}.", portalId, error);
		}
	}

	private static Vec3d horizontalAxisW(Direction through) {
		Direction direction = through == null || through.getAxis().isVertical() ? Direction.NORTH : through;
		return Vec3d.of(direction.rotateYClockwise().getVector());
	}

	private static Direction oppositeHorizontal(Direction direction) {
		return direction == null || direction.getAxis().isVertical()
			? Direction.SOUTH
			: direction.getOpposite();
	}

	private static boolean isImmersivePortalsAvailable() {
		if (FabricLoader.getInstance().isModLoaded("immersive_portals")
				|| FabricLoader.getInstance().isModLoaded("iportal")) {
			return true;
		}
		try {
			Class.forName("qouteall.imm_ptl.core.portal.Portal", false,
				PainterImmersivePortalBridge.class.getClassLoader());
			return true;
		} catch (ClassNotFoundException ignored) {
			return false;
		}
	}

	record DoorwayPortalIds(UUID sourceId, UUID reverseId) {
		boolean hasAny() {
			return sourceId != null || reverseId != null;
		}
	}
}
