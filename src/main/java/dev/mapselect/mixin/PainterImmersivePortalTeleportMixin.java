package dev.mapselect.mixin;

import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.portal.Portal;

import java.util.UUID;

@Mixin(value = Portal.class, remap = false)
public abstract class PainterImmersivePortalTeleportMixin {
	private static final String GEXPRESS_PAINTER_DOORWAY_TAG_PREFIX = "gexpress:painter_doorway";

	@Shadow(remap = false)
	public String portalTag;

	@Shadow(remap = false)
	public UUID specificPlayerId;

	@Inject(method = "canTeleportEntity", at = @At("HEAD"), cancellable = true, remap = false)
	private void gexpress$onlyPainterCanUseDoorway(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (entity == null || portalTag == null
				|| !portalTag.startsWith(GEXPRESS_PAINTER_DOORWAY_TAG_PREFIX)) {
			return;
		}
		if (portalTag.endsWith(":reverse") && specificPlayerId != null && !specificPlayerId.equals(entity.getUuid())) {
			cir.setReturnValue(false);
		}
	}
}
