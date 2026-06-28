package dev.mapselect.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.world.World;

/** A stationary, player-shaped living target used only inside tutorial sessions. */
public final class TutorialPassengerEntity extends ZombieEntity {
	public TutorialPassengerEntity(EntityType<? extends ZombieEntity> type, World world) {
		super(type, world);
		setAiDisabled(true);
		setSilent(true);
		setInvulnerable(true);
		setPersistent();
	}

	@Override
	public boolean isBaby() {
		return false;
	}
}
