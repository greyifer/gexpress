package dev.mapselect.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.platform_specific.IPConfig;

@Mixin(value = IPConfig.class, remap = false)
public abstract class ImmersivePortalsEmbeddedConfigMixin {
	@Inject(method = "onConfigChanged", at = @At("RETURN"), remap = false)
	private void gexpress$applyEmbeddedDefaults(CallbackInfo ci) {
		IPConfig config = (IPConfig) (Object) this;
		config.enableCrossPortalSound = false;
		config.checkModInfoFromInternet = false;
		config.enableUpdateNotification = false;
		config.shaderpackWarning = false;
		if (config.disabledWarnings != null) {
			config.disabledWarnings.add("many_mods");
			config.disabledWarnings.add("voicechat");
			config.disabledWarnings.add("iris");
			config.disabledWarnings.add("sodium");
			config.disabledWarnings.add("axiom");
			config.disabledWarnings.add("quilted_fabric_api");
		}

		IPGlobal.enableCrossPortalSound = false;
		IPGlobal.checkModInfoFromInternet = false;
		IPGlobal.enableUpdateNotification = false;
	}
}
