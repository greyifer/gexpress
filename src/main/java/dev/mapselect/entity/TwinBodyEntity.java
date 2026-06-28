package dev.mapselect.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Stationary, player-rendered spare body used by The Twins. */
public final class TwinBodyEntity extends ZombieEntity {
	private static final TrackedData<Optional<UUID>> OWNER_UUID =
		DataTracker.registerData(TwinBodyEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);
	private static final TrackedData<String> OWNER_NAME =
		DataTracker.registerData(TwinBodyEntity.class, TrackedDataHandlerRegistry.STRING);
	private static final TrackedData<String> DECOY_ROLE_ID =
		DataTracker.registerData(TwinBodyEntity.class, TrackedDataHandlerRegistry.STRING);
	private boolean anchored;
	private double anchorX;
	private double anchorY;
	private double anchorZ;
	private float anchorYaw;
	private float anchorPitch;
	private boolean anchorMove;

	public TwinBodyEntity(EntityType<? extends ZombieEntity> type, World world) {
		super(type, world);
		setAiDisabled(true);
		setNoGravity(true);
		setSilent(true);
		setInvulnerable(true);
		setPersistent();
		setCanPickUpLoot(false);
		noClip = true;
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(OWNER_UUID, Optional.empty());
		builder.add(OWNER_NAME, "");
		builder.add(DECOY_ROLE_ID, "");
	}

	public void initializeTwin(UUID ownerId, String ownerName, String decoyRoleId) {
		dataTracker.set(OWNER_UUID, Optional.ofNullable(ownerId));
		dataTracker.set(OWNER_NAME, ownerName == null ? "" : ownerName);
		dataTracker.set(DECOY_ROLE_ID, decoyRoleId == null ? "" : decoyRoleId);
		if (ownerName != null && !ownerName.isBlank()) {
			setCustomName(Text.literal(ownerName));
			setCustomNameVisible(false);
		}
	}

	public Optional<UUID> ownerId() {
		return dataTracker.get(OWNER_UUID);
	}

	public String ownerName() {
		return dataTracker.get(OWNER_NAME);
	}

	public String decoyRoleId() {
		return dataTracker.get(DECOY_ROLE_ID);
	}

	public void copyEquipmentFrom(LivingEntity owner) {
		if (owner == null) return;
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			ItemStack stack = owner.getEquippedStack(slot);
			equipStack(slot, stack == null ? ItemStack.EMPTY : stack.copy());
		}
	}

	public void setTwinAnchor(double x, double y, double z, float yaw, float pitch) {
		anchored = true;
		anchorX = x;
		anchorY = y;
		anchorZ = z;
		anchorYaw = yaw;
		anchorPitch = pitch;
		snapToAnchor();
	}

	@Override
	public void tick() {
		super.tick();
		if (anchored) maintainAnchor();
	}

	@Override
	public void setPosition(double x, double y, double z) {
		if (blocksExternalMove(x, y, z)) return;
		super.setPosition(x, y, z);
	}

	@Override
	public void refreshPositionAfterTeleport(double x, double y, double z) {
		if (blocksExternalMove(x, y, z)) return;
		super.refreshPositionAfterTeleport(x, y, z);
	}

	@Override
	public void refreshPositionAfterTeleport(Vec3d pos) {
		if (pos != null && blocksExternalMove(pos.x, pos.y, pos.z)) return;
		super.refreshPositionAfterTeleport(pos);
	}

	@Override
	public void refreshPositionAndAngles(double x, double y, double z, float yaw, float pitch) {
		if (blocksExternalMove(x, y, z)) return;
		super.refreshPositionAndAngles(x, y, z, yaw, pitch);
	}

	@Override
	public void refreshPositionAndAngles(Vec3d pos, float yaw, float pitch) {
		if (pos != null && blocksExternalMove(pos.x, pos.y, pos.z)) return;
		super.refreshPositionAndAngles(pos, yaw, pitch);
	}

	@Override
	public void refreshPositionAndAngles(BlockPos pos, float yaw, float pitch) {
		if (pos != null && blocksExternalMove(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D)) return;
		super.refreshPositionAndAngles(pos, yaw, pitch);
	}

	@Override
	public void tickMovement() {
		stopMovement();
		setVelocity(Vec3d.ZERO);
		velocityModified = false;
		fallDistance = 0.0F;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean isPushedByFluids() {
		return false;
	}

	@Override
	public void pushAwayFrom(Entity entity) {
	}

	@Override
	public boolean isBaby() {
		return false;
	}

	@Override
	public boolean canImmediatelyDespawn(double distanceSquared) {
		return false;
	}

	@Override
	public boolean teleport(ServerWorld world, double destX, double destY, double destZ, Set<PositionFlag> flags,
			float yaw, float pitch) {
		if (blocksExternalMove(destX, destY, destZ)) return false;
		return super.teleport(world, destX, destY, destZ, flags, yaw, pitch);
	}

	@Override
	public boolean teleport(double x, double y, double z, boolean particleEffects) {
		if (blocksExternalMove(x, y, z)) return false;
		return super.teleport(x, y, z, particleEffects);
	}

	@Override
	public void requestTeleport(double destX, double destY, double destZ) {
		if (blocksExternalMove(destX, destY, destZ)) return;
		super.requestTeleport(destX, destY, destZ);
	}

	@Override
	public void requestTeleportAndDismount(double destX, double destY, double destZ) {
		if (blocksExternalMove(destX, destY, destZ)) return;
		super.requestTeleportAndDismount(destX, destY, destZ);
	}

	@Override
	public void requestTeleportOffset(double offsetX, double offsetY, double offsetZ) {
		if (anchored && !anchorMove) return;
		super.requestTeleportOffset(offsetX, offsetY, offsetZ);
	}

	@Override
	public Entity teleportTo(TeleportTarget teleportTarget) {
		if (teleportTarget != null && blocksExternalMove(teleportTarget.pos().x, teleportTarget.pos().y,
				teleportTarget.pos().z)) {
			return this;
		}
		return super.teleportTo(teleportTarget);
	}

	@Override
	protected void initGoals() {
	}

	@Override
	protected boolean burnsInDaylight() {
		return false;
	}

	@Override
	protected boolean canConvertInWater() {
		return false;
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		ownerId().ifPresent(uuid -> nbt.putUuid("Owner", uuid));
		nbt.putString("OwnerName", ownerName());
		nbt.putString("DecoyRole", decoyRoleId());
		nbt.putBoolean("TwinAnchored", anchored);
		nbt.putDouble("TwinAnchorX", anchorX);
		nbt.putDouble("TwinAnchorY", anchorY);
		nbt.putDouble("TwinAnchorZ", anchorZ);
		nbt.putFloat("TwinAnchorYaw", anchorYaw);
		nbt.putFloat("TwinAnchorPitch", anchorPitch);
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		UUID owner = nbt.containsUuid("Owner") ? nbt.getUuid("Owner") : null;
		initializeTwin(owner, nbt.getString("OwnerName"), nbt.getString("DecoyRole"));
		anchored = nbt.getBoolean("TwinAnchored");
		anchorX = nbt.getDouble("TwinAnchorX");
		anchorY = nbt.getDouble("TwinAnchorY");
		anchorZ = nbt.getDouble("TwinAnchorZ");
		anchorYaw = nbt.getFloat("TwinAnchorYaw");
		anchorPitch = nbt.getFloat("TwinAnchorPitch");
		if (anchored) snapToAnchor();
	}

	private void snapToAnchor() {
		noClip = true;
		setNoGravity(true);
		anchorMove = true;
		try {
			refreshPositionAndAngles(anchorX, anchorY, anchorZ, anchorYaw, anchorPitch);
			setHeadYaw(anchorYaw);
			setBodyYaw(anchorYaw);
			setVelocity(Vec3d.ZERO);
			velocityModified = false;
			fallDistance = 0.0F;
		} finally {
			anchorMove = false;
		}
	}

	private void maintainAnchor() {
		noClip = true;
		setNoGravity(true);
		setVelocity(Vec3d.ZERO);
		velocityModified = false;
		fallDistance = 0.0F;
		double dx = getX() - anchorX;
		double dy = getY() - anchorY;
		double dz = getZ() - anchorZ;
		if (dx * dx + dy * dy + dz * dz > 1.0E-4D
				|| Math.abs(getYaw() - anchorYaw) > 0.01F
				|| Math.abs(getPitch() - anchorPitch) > 0.01F) {
			snapToAnchor();
		}
	}

	private boolean blocksExternalMove(double x, double y, double z) {
		if (!anchored || anchorMove) return false;
		double dx = x - anchorX;
		double dy = y - anchorY;
		double dz = z - anchorZ;
		return dx * dx + dy * dy + dz * dz > 1.0E-4D;
	}
}
