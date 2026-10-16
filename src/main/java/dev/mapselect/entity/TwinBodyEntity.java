package dev.mapselect.entity;

import dev.mapselect.role.twins.TwinsManager;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Optional;
import java.util.UUID;

/**
 * The inactive physical body owned by a Twins player.
 *
 * This is a separate living entity with its own health, effects, equipment,
 * motion, collision, and inventory snapshot. The owner only exchanges state
 * with it when swapping bodies.
 */
public final class TwinBodyEntity extends PathAwareEntity {
	private static final TrackedData<Optional<UUID>> OWNER_UUID =
		DataTracker.registerData(TwinBodyEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);
	private static final TrackedData<String> OWNER_NAME =
		DataTracker.registerData(TwinBodyEntity.class, TrackedDataHandlerRegistry.STRING);
	private static final TrackedData<String> DECOY_ROLE_ID =
		DataTracker.registerData(TwinBodyEntity.class, TrackedDataHandlerRegistry.STRING);
	private static final String INVENTORY_NBT_KEY = "TwinInventory";
	private static final String SELECTED_SLOT_NBT_KEY = "TwinSelectedSlot";

	private NbtList storedInventory = new NbtList();
	private int storedSelectedSlot;
	private boolean deathReported;

	public TwinBodyEntity(EntityType<? extends PathAwareEntity> type, World world) {
		super(type, world);
		setAiDisabled(true);
		setPersistent();
		setCanPickUpLoot(false);
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			setEquipmentDropChance(slot, 0.0F);
		}
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
		setCustomName(null);
		setCustomNameVisible(false);
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

	public void storeInventoryFrom(ServerPlayerEntity owner) {
		if (owner == null) return;
		storedInventory = (NbtList) owner.getInventory().writeNbt(new NbtList()).copy();
		storedSelectedSlot = owner.getInventory().selectedSlot;
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			ItemStack stack = owner.getEquippedStack(slot);
			equipStack(slot, stack == null ? ItemStack.EMPTY : stack.copy());
		}
	}

	public NbtList copyStoredInventory() {
		return (NbtList) storedInventory.copy();
	}

	public int storedSelectedSlot() {
		return storedSelectedSlot;
	}

	public void restoreStoredInventory(ServerPlayerEntity player) {
		if (player == null) return;
		player.getInventory().clear();
		player.getInventory().readNbt(copyStoredInventory());
		player.getInventory().selectedSlot = Math.max(0, Math.min(8, storedSelectedSlot));
		player.getInventory().markDirty();
	}

	/** Moves this entity only when the player swaps which body is active. */
	public void setTwinAnchor(double x, double y, double z, float yaw, float pitch) {
		refreshPositionAndAngles(x, y, z, yaw, pitch);
		setHeadYaw(yaw);
		setBodyYaw(yaw);
		setVelocity(Vec3d.ZERO);
		velocityModified = true;
		fallDistance = 0.0F;
	}

	@Override
	protected ActionResult interactMob(PlayerEntity player, Hand hand) {
		if (getWorld().isClient) return ActionResult.SUCCESS;
		if (player == null || hand == null) return ActionResult.PASS;
		ItemStack stack = player.getStackInHand(hand);
		return stack.isEmpty() ? ActionResult.PASS : stack.useOnEntity(player, this, hand);
	}

	@Override
	public boolean isPushable() {
		return true;
	}

	@Override
	public void takeKnockback(double strength, double x, double z) {
		super.takeKnockback(strength, x, z);
		velocityModified = true;
	}

	@Override
	public void onDeath(DamageSource source) {
		super.onDeath(source);
		if (!getWorld().isClient && !deathReported) {
			deathReported = true;
			discard(); // Legacy saved mob bodies are retired; new twins use native player actors.
		}
	}

	@Override
	protected void dropEquipment(ServerWorld world, DamageSource source, boolean causedByPlayer) {
		// The equipped stacks are visual mirrors of this body's stored inventory.
		// Do not let MobEntity drop a second copy of them.
	}

	@Override
	protected void initGoals() {
	}

	@Override
	public boolean canImmediatelyDespawn(double distanceSquared) {
		return false;
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		ownerId().ifPresent(uuid -> nbt.putUuid("Owner", uuid));
		nbt.putString("OwnerName", ownerName());
		nbt.putString("DecoyRole", decoyRoleId());
		nbt.put(INVENTORY_NBT_KEY, storedInventory.copy());
		nbt.putInt(SELECTED_SLOT_NBT_KEY, storedSelectedSlot);
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		UUID owner = nbt.containsUuid("Owner") ? nbt.getUuid("Owner") : null;
		initializeTwin(owner, nbt.getString("OwnerName"), nbt.getString("DecoyRole"));
		storedInventory = nbt.contains(INVENTORY_NBT_KEY, NbtElement.LIST_TYPE)
			? (NbtList) nbt.getList(INVENTORY_NBT_KEY, NbtElement.COMPOUND_TYPE).copy()
			: new NbtList();
		storedSelectedSlot = nbt.getInt(SELECTED_SLOT_NBT_KEY);
	}
}
