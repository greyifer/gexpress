package dev.mapselect.mixin.client;

import dev.doctor4t.wathe.client.WatheClient;
import dev.mapselect.client.role.rat.ClientRatState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.util.Identifier;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;

@Mixin(value = KeyBinding.class, priority = 5000)
public abstract class RatJumpKeyBindingMixin {
	@Unique private static final Identifier GEXPRESS_KINS_EFFECT =
		Identifier.of("kinswathe", "effect");
	@Unique private static Field gexpress$stunTicksField;
	@Unique private static boolean gexpress$lookedUpStunField;

	@Inject(method = "wasPressed", at = @At("RETURN"), cancellable = true)
	private void gexpress$unlockRatJumpWasPressed(CallbackInfoReturnable<Boolean> cir) {
		gexpress$unlockRatJump(cir);
	}

	@Inject(method = "isPressed", at = @At("RETURN"), cancellable = true)
	private void gexpress$unlockRatJumpIsPressed(CallbackInfoReturnable<Boolean> cir) {
		gexpress$unlockRatJump(cir);
	}

	@Unique
	private void gexpress$unlockRatJump(CallbackInfoReturnable<Boolean> cir) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.player == null || client.options == null
				|| (Object) this != client.options.jumpKey
				|| !ClientRatState.isLocalRat(client)
				|| ClientRatState.isPlayingDead()
				|| !WatheClient.isPlayerAliveAndInSurvival()
				|| gexpress$isKinsStunned(client)) {
			return;
		}
		cir.setReturnValue(ClientRatState.allowLocalJump(client,
			((KeyBindingAccessor) this).gexpress$pressed()));
	}

	@Unique
	private static boolean gexpress$isKinsStunned(MinecraftClient client) {
		try {
			ComponentKey<?> key = ComponentRegistry.get(GEXPRESS_KINS_EFFECT);
			if (key == null || client.player == null) return false;
			Object component = key.get(client.player);
			if (component == null) return false;
			if (!gexpress$lookedUpStunField) {
				gexpress$stunTicksField = component.getClass().getField("stunTicks");
				gexpress$stunTicksField.trySetAccessible();
				gexpress$lookedUpStunField = true;
			}
			return gexpress$stunTicksField != null && gexpress$stunTicksField.getInt(component) > 0;
		} catch (Throwable ignored) {
			gexpress$lookedUpStunField = true;
			gexpress$stunTicksField = null;
			return false;
		}
	}
}
