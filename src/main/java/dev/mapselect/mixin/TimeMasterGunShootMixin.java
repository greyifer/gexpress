package dev.mapselect.mixin;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.index.WatheItems;
import dev.doctor4t.wathe.util.GunDropPayload;
import dev.doctor4t.wathe.util.GunShootPayload;
import dev.doctor4t.wathe.util.Scheduler;
import dev.mapselect.game.GexpressGameModes;
import dev.mapselect.entity.TwinFakePlayerEntity;
import dev.mapselect.role.mafia.MafiaManager;
import dev.mapselect.role.timemaster.TimeMasterManager;
import dev.mapselect.role.juggernaut.JuggernautManager;
import dev.mapselect.role.silent.SilentShadowComponent;
import dev.mapselect.role.twins.TwinsManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GunShootPayload.Receiver.class)
public abstract class TimeMasterGunShootMixin {
	@Inject(
		method = "receive(Ldev/doctor4t/wathe/util/GunShootPayload;Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;)V",
		at = @At("HEAD"),
		cancellable = true
	)
	private void gexpress$blockFrozenGunShot(GunShootPayload payload,
			ServerPlayNetworking.Context context, CallbackInfo ci) {
		if (SilentShadowComponent.isShadowed(context.player())
				|| TimeMasterManager.isFrozen(context.player()) || TimeMasterManager.isRewinding(context.player())) {
			ci.cancel();
			return;
		}
		if (!MafiaManager.beforeGunShot(context.player())) ci.cancel();
	}

	@Inject(
		method = "receive(Ldev/doctor4t/wathe/util/GunShootPayload;Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;)V",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/World;playSound(Lnet/minecraft/entity/player/PlayerEntity;DDDLnet/minecraft/sound/SoundEvent;Lnet/minecraft/sound/SoundCategory;FF)V",
			ordinal = 1,
			shift = At.Shift.AFTER
		)
	)
	private void gexpress$recordTimeMasterRevolverShot(GunShootPayload payload,
			ServerPlayNetworking.Context context, CallbackInfo ci) {
		TimeMasterManager.recordWeaponEvent(context.player(), TimeMasterManager.WeaponEventType.REVOLVER_SHOT);
		gexpress$enforceDraftKillerGunDrop(payload, context.player());
		if (context.player().getServer() != null) {
			context.player().getServer().execute(() -> JuggernautManager.applyCurrentWeaponCooldown(
				context.player(), JuggernautManager.WeaponKind.REVOLVER));
		}
	}

	@Inject(
		method = "receive(Ldev/doctor4t/wathe/util/GunShootPayload;Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;)V",
		at = @At("TAIL")
	)
	private void gexpress$applyMafiaGunRules(GunShootPayload payload,
			ServerPlayNetworking.Context context, CallbackInfo ci) {
		MafiaManager.afterGunShot(context.player());
	}

	private static void gexpress$enforceDraftKillerGunDrop(GunShootPayload payload,
			ServerPlayerEntity shooter) {
		if (payload == null || shooter == null || !shooter.getMainHandStack().isOf(WatheItems.REVOLVER)) return;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(shooter.getWorld());
		if (!GexpressGameModes.isDraft(game) || game.getRole(shooter) == null
				|| !game.getRole(shooter).canUseKiller()) return;
		Entity hitEntity = shooter.getServerWorld().getEntityById(payload.target());
		ServerPlayerEntity target = hitEntity instanceof ServerPlayerEntity player
			? player
			: hitEntity instanceof TwinFakePlayerEntity body ? TwinsManager.ownerFor(body) : null;
		Entity distanceTarget = hitEntity instanceof TwinFakePlayerEntity ? hitEntity : target;
		if (target == null || distanceTarget == null
				|| shooter.distanceTo(distanceTarget) >= 65.0F || !game.isInnocent(target)) {
			return;
		}

		int revolversBeforeVanillaDrop = shooter.getInventory().count(WatheItems.REVOLVER);
		Scheduler.schedule(() -> {
			GameWorldComponent currentGame = GameWorldComponent.KEY.getNullable(shooter.getWorld());
			if (!GexpressGameModes.isDraft(currentGame)
					|| shooter.getInventory().count(WatheItems.REVOLVER) < revolversBeforeVanillaDrop) {
				return;
			}
			shooter.getInventory().remove(stack -> stack.isOf(WatheItems.REVOLVER), 1, shooter.getInventory());
			ItemEntity dropped = shooter.dropItem(WatheItems.REVOLVER.getDefaultStack(), false, false);
			if (dropped != null) {
				dropped.setPickupDelay(10);
				dropped.setThrower(shooter);
			}
			ServerPlayNetworking.send(shooter, new GunDropPayload());
			PlayerMoodComponent.KEY.get(shooter).setMood(0.0F);
		}, 5);
	}
}
