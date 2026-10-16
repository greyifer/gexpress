package dev.mapselect.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.doctor4t.wathe.util.GunShootPayload;
import dev.mapselect.entity.TwinFakePlayerEntity;
import dev.mapselect.role.twins.TwinsManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GunShootPayload.Receiver.class)
public abstract class TwinsGunShootTargetMixin {
	@WrapOperation(
		method = "receive(Ldev/doctor4t/wathe/util/GunShootPayload;Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/server/world/ServerWorld;getEntityById(I)Lnet/minecraft/entity/Entity;")
	)
	private Entity gexpress$resolveTwinBodyOwner(ServerWorld world, int entityId,
			Operation<Entity> original, GunShootPayload payload, ServerPlayNetworking.Context context) {
		Entity entity = original.call(world, entityId);
		if (entity instanceof TwinFakePlayerEntity body) {
			PlayerEntity owner = TwinsManager.ownerFor(body);
			if (owner != null) return owner;
		}
		return entity;
	}

	@WrapOperation(
		method = "receive(Ldev/doctor4t/wathe/util/GunShootPayload;Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;distanceTo(Lnet/minecraft/entity/Entity;)F")
	)
	private float gexpress$measureFromTwinBody(PlayerEntity target, Entity shooter,
			Operation<Float> original, GunShootPayload payload, ServerPlayNetworking.Context context) {
		Entity rawTarget = context.player().getServerWorld().getEntityById(payload.target());
		return rawTarget instanceof TwinFakePlayerEntity body ? body.distanceTo(shooter) : original.call(target, shooter);
	}

	@Dynamic("Wathe uses intermediary Minecraft descriptors in its published bytecode")
	@WrapOperation(
		method = "receive(Ldev/doctor4t/wathe/util/GunShootPayload;Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;)V",
		at = @At(
			value = "INVOKE",
			target = "Ldev/doctor4t/wathe/game/GameFunctions;killPlayer(Lnet/minecraft/class_1657;ZLnet/minecraft/class_1657;Lnet/minecraft/class_2960;)V",
			ordinal = 1,
			remap = false
		)
	)
	private void gexpress$killTwinBodyInsteadOfOwner(PlayerEntity victim, boolean dead,
			PlayerEntity killer, Identifier reason, Operation<Void> original,
			GunShootPayload payload, ServerPlayNetworking.Context context) {
		Entity rawTarget = context.player().getServerWorld().getEntityById(payload.target());
		if (rawTarget instanceof TwinFakePlayerEntity body && victim == TwinsManager.ownerFor(body)) {
			TwinsManager.killBody(body, killer);
			return;
		}
		original.call(victim, dead, killer, reason);
	}
}
