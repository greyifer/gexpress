package dev.mapselect.mixin.client;

import dev.mapselect.client.ClientPuppetmasterState;
import dev.mapselect.client.ClientTricksterState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(PlayerEntity.class)
public abstract class PuppetmasterClientDisplayNameMixin {
	@Inject(method = "getDisplayName", at = @At("HEAD"), cancellable = true)
	private void gexpress$puppetmasterDisplayName(CallbackInfoReturnable<Text> cir) {
		PlayerEntity self = (PlayerEntity) (Object) this;
		UUID replacementId = ClientPuppetmasterState.replacementFor(self.getUuid());
		if (replacementId == null) {
			replacementId = ClientTricksterState.replacementFor(self.getUuid());
		}
		if (replacementId == null || self.getWorld() == null) return;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.world == null) return;
		for (PlayerEntity player : client.world.getPlayers()) {
			if (replacementId.equals(player.getUuid())) {
				cir.setReturnValue(Text.literal(player.getName().getString()));
				return;
			}
		}
	}
}
