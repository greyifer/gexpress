package dev.mapselect.mixin;

import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.mapselect.role.spy.SpyManager;
import dev.mapselect.task.FreshAirAreaManager;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.EnumSet;
import java.util.Map;

@Mixin(value = PlayerMoodComponent.class, remap = false)
public abstract class PlayerMoodTaskCompletionMixin {
	@Final
	@Shadow
	private PlayerEntity player;

	@Final
	@Shadow
	public Map<PlayerMoodComponent.Task, PlayerMoodComponent.TrainTask> tasks;

	@Shadow
	public abstract float getMood();

	@Shadow
	public abstract void setMood(float mood);

	@Unique
	private EnumSet<PlayerMoodComponent.Task> gexpress$tasksBeforeTick = EnumSet.noneOf(PlayerMoodComponent.Task.class);

	@Inject(method = "serverTick", at = @At("HEAD"))
	private void gexpress$captureTasksBeforeTick(CallbackInfo ci) {
		gexpress$tasksBeforeTick = tasks.isEmpty()
			? EnumSet.noneOf(PlayerMoodComponent.Task.class)
			: EnumSet.copyOf(tasks.keySet());
	}

	@Inject(method = "serverTick", at = @At("RETURN"))
	private void gexpress$noticeCompletedTasks(CallbackInfo ci) {
		if (gexpress$tasksBeforeTick.isEmpty()) return;
		for (PlayerMoodComponent.Task task : gexpress$tasksBeforeTick) {
			if (tasks.containsKey(task)) continue;
			if (task == PlayerMoodComponent.Task.OUTSIDE) {
				int percent = FreshAirAreaManager.sanityPercent(player);
				if (percent < 100) {
					float adjustment = GameConstants.MOOD_GAIN * (1.0F - percent / 100.0F);
					setMood(getMood() - adjustment);
				}
			}
			SpyManager.recordTask(player, task);
		}
	}
}
