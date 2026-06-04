package dev.mapselect.mixin.client;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.client.WatheClient;
import dev.mapselect.client.ClientAmnesiaState;
import dev.mapselect.client.ClientInstinctEnergy;
import dev.mapselect.client.ClientSpectatorRoleRevealDelay;
import dev.mapselect.client.ClientVultureState;
import dev.mapselect.role.copycat.CopycatManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

@Mixin(value = WatheClient.class, remap = false, priority = 3000)
public abstract class WatheClientAmnesiaInstinctMixin {
	private static final Set<Identifier> NO_INSTINCT_NEUTRALS = Set.of(
		Identifier.of("noellesroles", "jester"),
		Identifier.of("wathe", "loose_end")
	);

	@Inject(method = "isInstinctEnabled", at = @At("HEAD"), cancellable = true)
	private static void gexpress$disableInstinctInPelicanBelly(CallbackInfoReturnable<Boolean> cir) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (ClientVultureState.isLocalStashed(client)) {
			cir.setReturnValue(false);
			return;
		}
		if (client != null && CopycatManager.isBorrowingAbility(client.player)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "isInstinctEnabled", at = @At("RETURN"), cancellable = true)
	private static void gexpress$disableInstinctForLicensedVillain(CallbackInfoReturnable<Boolean> cir) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.player == null || client.world == null) {
			cir.setReturnValue(ClientInstinctEnergy.filter(cir.getReturnValue()));
			return;
		}
		if (CopycatManager.isBorrowingAbility(client.player)) {
			cir.setReturnValue(false);
			return;
		}
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(client.world);
		boolean enabled = cir.getReturnValue();
		if (game != null) {
			Role role = game.getRole(client.player);
			if (role != null && NO_INSTINCT_NEUTRALS.contains(role.identifier())) {
				enabled = false;
			}
		}
		cir.setReturnValue(ClientInstinctEnergy.filter(enabled));
	}

	@Inject(method = "getInstinctHighlight", at = @At("HEAD"), cancellable = true)
	private static void gexpress$renderOtherKillersAsCivilians(Entity target, CallbackInfoReturnable<Integer> cir) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (ClientVultureState.isLocalStashed(client)) {
			cir.setReturnValue(-1);
			return;
		}
		if (client != null && CopycatManager.isBorrowingAbility(client.player)) {
			cir.setReturnValue(-1);
			return;
		}
		if (target instanceof PlayerEntity player) {
			if (ClientSpectatorRoleRevealDelay.shouldMaskRoleColors(client)) {
				cir.setReturnValue(ClientSpectatorRoleRevealDelay.shouldUseInstinctReveal(client)
					? ClientSpectatorRoleRevealDelay.glowColor()
					: -1);
				return;
			}
			if (ClientAmnesiaState.shouldHideKillerIdentity(player)) {
				cir.setReturnValue(ClientAmnesiaState.civilianInstinctColor(player));
			}
		}
	}
}
