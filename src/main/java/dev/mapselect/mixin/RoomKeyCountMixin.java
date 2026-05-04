package dev.mapselect.mixin;

import dev.doctor4t.wathe.game.mapeffect.HarpyExpressTrainMapEffect;
import dev.mapselect.preset.map.RoomKeyRange;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import java.util.List;

@Mixin(value = HarpyExpressTrainMapEffect.class, remap = false)
public abstract class RoomKeyCountMixin {
	@ModifyConstant(method = "initializeMapEffects", constant = @Constant(intValue = 7), require = 0)
	private int gexpress$useActiveMapRoomCount(int original, ServerWorld serverWorld, List<ServerPlayerEntity> players) {
		return RoomKeyRange.forWorld(serverWorld);
	}
}
