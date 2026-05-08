package dev.mapselect.mixin;

import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.role.spy.SpyManager;
import dev.mapselect.task.ConversationTask;
import dev.mapselect.task.FreshAirAreaManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.RegistryWrapper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.EnumMap;
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
	private Map<PlayerMoodComponent.Task, PlayerMoodComponent.TrainTask> gexpress$tasksBeforeTick = Map.of();

	@Unique
	private float gexpress$moodBeforeTick = 1.0F;

	@Inject(method = "serverTick", at = @At("HEAD"))
	private void gexpress$captureTasksBeforeTick(CallbackInfo ci) {
		gexpress$moodBeforeTick = getMood();
		if (tasks.isEmpty()) {
			gexpress$tasksBeforeTick = Map.of();
			return;
		}
		EnumMap<PlayerMoodComponent.Task, PlayerMoodComponent.TrainTask> copy =
			new EnumMap<>(PlayerMoodComponent.Task.class);
		copy.putAll(tasks);
		gexpress$tasksBeforeTick = copy;
	}

	@Inject(method = "serverTick", at = @At("RETURN"))
	private void gexpress$noticeCompletedTasks(CallbackInfo ci) {
		if (gexpress$tasksBeforeTick.isEmpty()) return;
		for (Map.Entry<PlayerMoodComponent.Task, PlayerMoodComponent.TrainTask> entry : gexpress$tasksBeforeTick.entrySet()) {
			PlayerMoodComponent.Task type = entry.getKey();
			PlayerMoodComponent.TrainTask completedTask = entry.getValue();
			if (tasks.containsKey(type)) continue;
			if (completedTask instanceof PlayerMoodComponent.OutsideTask && FreshAirAreaManager.countsAsFreshAir(player)) {
				float gain = Math.max(0.0F, Math.min(1.0F, FreshAirAreaManager.sanityPercent(player) / 100.0F));
				setMood(Math.min(1.0F, gexpress$moodBeforeTick + gain));
			}
			SpyManager.recordTask(player, completedTask);
		}
	}

	@Inject(method = "generateTask", at = @At("RETURN"), cancellable = true)
	private void gexpress$maybeGenerateConversationTask(
			CallbackInfoReturnable<PlayerMoodComponent.TrainTask> cir) {
		if (cir.getReturnValue() == null || !GexpressConfig.isConversationTaskEnabled()) return;
		int chance = GexpressConfig.getConversationTaskChancePercent();
		if (chance <= 0 || player.getRandom().nextInt(100) >= chance) return;
		cir.setReturnValue(ConversationTask.createConfigured());
	}

	@Inject(method = "readFromNbt", at = @At("RETURN"))
	private void gexpress$restoreConversationTasks(NbtCompound tag, RegistryWrapper.WrapperLookup lookup,
			CallbackInfo ci) {
		if (tag == null || !tag.contains("tasks", NbtElement.LIST_TYPE)) return;
		for (NbtElement element : tag.getList("tasks", NbtElement.COMPOUND_TYPE)) {
			if (!(element instanceof NbtCompound taskTag) || !taskTag.getBoolean(ConversationTask.NBT_KEY)) continue;
			tasks.put(PlayerMoodComponent.Task.OUTSIDE, ConversationTask.fromNbt(taskTag));
		}
	}
}
