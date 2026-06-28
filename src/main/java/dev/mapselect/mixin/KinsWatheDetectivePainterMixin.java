package dev.mapselect.mixin;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.mapselect.role.painter.PainterManager;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "org.BsXinQin.kinswathe.roles.detective.DetectiveAbility", remap = false)
public abstract class KinsWatheDetectivePainterMixin {
	@Redirect(method = "register", at = @At(value = "INVOKE",
		target = "Ldev/doctor4t/wathe/cca/GameWorldComponent;getRole(Lnet/minecraft/class_1657;)Ldev/doctor4t/wathe/api/Role;",
		remap = false), require = 0)
	private static Role gexpress$paintedDetectiveRole(GameWorldComponent game, PlayerEntity player) {
		return PainterManager.investigationRole(game, player);
	}
}
