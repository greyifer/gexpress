package dev.mapselect.mixin.client;

import cat.rezelyn.watheextended.client.pronouns.PronounsCache;
import dev.mapselect.client.ClientPuppetmasterState;
import dev.mapselect.client.ClientSkincrawlerState;
import dev.mapselect.client.ClientTricksterState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.UUID;

@Mixin(value = PronounsCache.class, remap = false)
public abstract class PuppetmasterPronounsCacheMixin {
	@Shadow @Final private static Map<UUID, String> CACHE;

	@Inject(method = "get", at = @At("HEAD"), cancellable = true, remap = false)
	private static void gexpress$swapPuppetmasterPronouns(UUID playerId, CallbackInfoReturnable<String> cir) {
		UUID replacement = ClientPuppetmasterState.replacementFor(playerId);
		if (replacement == null) replacement = ClientSkincrawlerState.replacementFor(playerId);
		if (replacement == null) replacement = ClientTricksterState.replacementFor(playerId);
		if (replacement != null) {
			cir.setReturnValue(CACHE.getOrDefault(replacement, ""));
		}
	}
}
