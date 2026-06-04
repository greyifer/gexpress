package dev.mapselect.mixin.client;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DrawContext.class)
public abstract class ExternalAbilityHudTextMixin {
	@Inject(method = "drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;III)I",
		at = @At("HEAD"), cancellable = true)
	private void gexpress$hideExternalAbilityPrompts(TextRenderer textRenderer, Text text, int x, int y, int color,
			CallbackInfoReturnable<Integer> cir) {
		DrawContext context = (DrawContext) (Object) this;
		if (x < context.getScaledWindowWidth() / 2 || y < context.getScaledWindowHeight() - 42) return;
		if (isExternalAbilityPrompt(text)) cir.setReturnValue(0);
	}

	private static boolean isExternalAbilityPrompt(Text text) {
		if (text == null || !(text.getContent() instanceof TranslatableTextContent content)) return false;
		return switch (content.getKey()) {
			case "tip.kinswathe.cooldown",
			     "tip.kinswathe.ability.can_use",
			     "tip.kinswathe.ability.not_enough_money",
			     "tip.noellesroles.cooldown",
			     "tip.phantom",
			     "tip.infected",
			     "tip.recaller.place",
			     "tip.recaller.teleport",
			     "tip.recaller.not_enough_money",
			     "tip.vulture",
			     "tip.starexpress.cooldown",
			     "tip.starexpress.starstruck",
			     "hud.stupid_express.thief.ready",
			     "hud.stupid_express.thief.cooldown",
			     "hud.stupid_express.necromancer.cooldown" -> true;
			default -> false;
		};
	}
}
