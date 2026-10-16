package dev.mapselect.mixin;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.mapselect.role.painter.PainterManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import java.util.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.UUID;

@Pseudo
@Mixin(targets = "org.agmas.noellesroles.entities.RoleMineEntity", remap = false)
public abstract class NoellesTrapperPainterMixin {
	@Shadow(remap=false) public UUID owner;
    @Shadow(remap=false) public ArrayList<UUID> previouslyCaught;
    @Unique private Map<UUID,Role> gexpress$forgedReport;
    @Redirect(method = {"tick", "method_5773"}, at = @At(value = "INVOKE",
		target = "Ldev/doctor4t/wathe/cca/GameWorldComponent;getRole(Ljava/util/UUID;)Ldev/doctor4t/wathe/api/Role;",
		remap = false), require = 0)
	private Role gexpress$paintedTrapperReportRole(GameWorldComponent game, UUID playerId) {
		if(((net.minecraft.entity.Entity)(Object)this).getCommandTags().contains(dev.mapselect.item.CounterfeitTrapItem.TAG)){
            if(gexpress$forgedReport==null)gexpress$forgedReport=dev.mapselect.role.counterfeiter.CounterfeitReportPolicy.generate(previouslyCaught,owner,game::getRole,dev.doctor4t.wathe.api.WatheRoles.ROLES,new Random());
            return gexpress$forgedReport.get(playerId);
        }
        return PainterManager.investigationRole(game, playerId);
	}
}
