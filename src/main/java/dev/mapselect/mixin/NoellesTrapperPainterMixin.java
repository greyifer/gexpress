package dev.mapselect.mixin;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.mapselect.role.painter.PainterManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.UUID;

@Pseudo
@Mixin(targets = "org.agmas.noellesroles.entities.RoleMineEntity", remap = false)
public abstract class NoellesTrapperPainterMixin {
	@Redirect(method = "tick", at = @At(value = "INVOKE",
		target = "Ldev/doctor4t/wathe/cca/GameWorldComponent;getRole(Ljava/util/UUID;)Ldev/doctor4t/wathe/api/Role;",
		remap = false), require = 0)
	private Role gexpress$paintedTrapperReportRole(GameWorldComponent game, UUID playerId) {
		return PainterManager.investigationRole(game, playerId);
	}
}
