package dev.mapselect.entity;

import dev.mapselect.block.RatTrapBlock;
import dev.mapselect.role.rat.RatManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

/** A short-lived floor trap with real entity rendering and vanilla outline support. */
public final class RatTrapEntity extends Entity {
	public RatTrapEntity(EntityType<? extends RatTrapEntity> type, World world) {
		super(type, world);
		setNoGravity(true);
	}

	@Override
	public void tick() {
		super.tick();
		setVelocity(0.0D, 0.0D, 0.0D);
		if (getWorld().isClient) return;
		if (age >= RatTrapBlock.LIFETIME_TICKS) {
			discard();
			return;
		}
		Box trigger = getBoundingBox().expand(0.10D, 0.16D, 0.10D);
		for (ServerPlayerEntity player : getWorld().getEntitiesByClass(ServerPlayerEntity.class,
				trigger, RatManager::isRat)) {
			discard();
			RatManager.killWithTrap(player);
			break;
		}
	}

	public float remainingOpacity(float tickDelta) {
		float elapsed = Math.max(0.0F, age + tickDelta);
		float remaining = 1.0F - elapsed / RatTrapBlock.LIFETIME_TICKS;
		return Math.max(0.0F, Math.min(1.0F, remaining));
	}

	@Override
	public int getTeamColorValue() {
		return 0xFFD15C;
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
		age = Math.max(0, nbt.getInt("Age"));
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		nbt.putInt("Age", Math.max(0, age));
	}
}
