package dev.mapselect.task;

import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.role.vulture.VultureManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;

public final class ConversationTask implements PlayerMoodComponent.TrainTask {
	public static final String NAME = "gexpress.conversation";
	public static final String NBT_KEY = "gexpressConversation";

	private int timer;

	public ConversationTask(int ticks) {
		this.timer = Math.max(1, ticks);
	}

	public static ConversationTask createConfigured() {
		return new ConversationTask(GexpressConfig.getConversationTaskDurationSeconds() * 20);
	}

	public static ConversationTask fromNbt(NbtCompound nbt) {
		int ticks = nbt == null ? 0 : nbt.getInt("timer");
		return new ConversationTask(ticks <= 0 ? GexpressConfig.getConversationTaskDurationSeconds() * 20 : ticks);
	}

	public static boolean isConversation(PlayerMoodComponent.TrainTask task) {
		return task instanceof ConversationTask || task != null && NAME.equals(task.getName());
	}

	@Override
	public void tick(@NotNull PlayerEntity player) {
		if (timer > 0 && hasConversationPartner(player)) {
			timer--;
		}
	}

	@Override
	public boolean isFulfilled(PlayerEntity player) {
		return timer <= 0;
	}

	@Override
	public String getName() {
		return NAME;
	}

	@Override
	public PlayerMoodComponent.Task getType() {
		return PlayerMoodComponent.Task.OUTSIDE;
	}

	@Override
	public NbtCompound toNbt() {
		NbtCompound nbt = new NbtCompound();
		nbt.putInt("type", PlayerMoodComponent.Task.OUTSIDE.ordinal());
		nbt.putInt("timer", timer);
		nbt.putBoolean(NBT_KEY, true);
		return nbt;
	}

	private static boolean hasConversationPartner(PlayerEntity player) {
		if (!(player instanceof ServerPlayerEntity serverPlayer)) return false;
		World world = serverPlayer.getWorld();
		double radius = GexpressConfig.getConversationTaskRadiusBlocks();
		double radiusSq = radius * radius;
		int verticalTolerance = GexpressConfig.getConversationTaskVerticalToleranceBlocks();

		for (ServerPlayerEntity other : serverPlayer.getServerWorld().getPlayers()) {
			if (other == serverPlayer || other.getWorld() != world || VultureManager.isStashed(other)) continue;
			if (!GameFunctions.isPlayerAliveAndSurvival(other)) continue;
			if (Math.abs(other.getY() - serverPlayer.getY()) > verticalTolerance + 0.5D) continue;
			double dx = other.getX() - serverPlayer.getX();
			double dz = other.getZ() - serverPlayer.getZ();
			if (dx * dx + dz * dz <= radiusSq) return true;
		}
		return false;
	}
}
