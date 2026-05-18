package dev.mapselect.mixin;

import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.harpymodloader.modifiers.Modifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mixin(value = WorldModifierComponent.class, remap = false)
public abstract class HmlModifierComponentNullGuardMixin {
	@Shadow
	public HashMap<UUID, ArrayList<Modifier>> modifiers;

	@Inject(method = "getModifiers(Ljava/util/UUID;)Ljava/util/ArrayList;", at = @At("HEAD"))
	private void gexpress$ensureModifierMap(UUID uuid, CallbackInfo ci) {
		if (modifiers == null) modifiers = new HashMap<>();
	}

	@Inject(method = "getModifiers(Ljava/util/UUID;)Ljava/util/ArrayList;", at = @At("RETURN"), cancellable = true)
	private void gexpress$neverReturnNullModifierList(UUID uuid, CallbackInfoReturnable<ArrayList<Modifier>> cir) {
		if (modifiers == null) modifiers = new HashMap<>();
		ArrayList<Modifier> current = cir.getReturnValue();
		if (current == null) {
			current = new ArrayList<>();
			modifiers.put(uuid, current);
			cir.setReturnValue(current);
		}
	}

	@Inject(method = "getModifiers()Ljava/util/HashMap;", at = @At("RETURN"), cancellable = true)
	private void gexpress$sanitizeModifierMap(CallbackInfoReturnable<HashMap<UUID, ArrayList<Modifier>>> cir) {
		if (modifiers == null) modifiers = new HashMap<>();
		for (Map.Entry<UUID, ArrayList<Modifier>> entry : modifiers.entrySet()) {
			if (entry.getValue() == null) entry.setValue(new ArrayList<>());
		}
		if (cir.getReturnValue() == null) cir.setReturnValue(modifiers);
	}
}
