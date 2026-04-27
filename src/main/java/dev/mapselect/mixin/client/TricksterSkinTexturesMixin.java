package dev.mapselect.mixin.client;

import dev.mapselect.client.ClientTricksterState;
import dev.mapselect.client.ClientPuppetmasterState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.SkinTextures;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(AbstractClientPlayerEntity.class)
public abstract class TricksterSkinTexturesMixin {
	@Unique
	private static final ThreadLocal<Boolean> gexpress$resolvingTricksterSkin =
		ThreadLocal.withInitial(() -> false);

	@Inject(method = "getSkinTextures", at = @At("HEAD"), cancellable = true)
	private void gexpress$swapTricksterSkin(CallbackInfoReturnable<SkinTextures> cir) {
		if (Boolean.TRUE.equals(gexpress$resolvingTricksterSkin.get())) return;

		AbstractClientPlayerEntity self = (AbstractClientPlayerEntity) (Object) this;
		UUID replacementId = ClientPuppetmasterState.replacementFor(self.getUuid());
		if (replacementId == null) replacementId = ClientTricksterState.replacementFor(self.getUuid());
		if (replacementId == null || replacementId.equals(self.getUuid())) return;

		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.world == null) return;

		AbstractClientPlayerEntity replacement = null;
		for (AbstractClientPlayerEntity player : client.world.getPlayers()) {
			if (replacementId.equals(player.getUuid())) {
				replacement = player;
				break;
			}
		}
		if (replacement == null || replacement == self) return;

		try {
			gexpress$resolvingTricksterSkin.set(true);
			cir.setReturnValue(replacement.getSkinTextures());
		} finally {
			gexpress$resolvingTricksterSkin.set(false);
		}
	}
}
