package dev.mapselect.mixin.client;

import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.mapselect.client.ClientSnitchState;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "dev.doctor4t.wathe.client.gui.MoodRenderer$TaskRenderer", remap = false)
public abstract class SnitchMoodTaskTextMixin {
	@Shadow
	public int index;

	@Shadow
	public Text text;

	@Inject(method = "tick", at = @At("RETURN"))
	private void gexpress$appendSnitchProgress(PlayerMoodComponent.TrainTask present, float delta,
			CallbackInfoReturnable<Boolean> cir) {
		if (present == null || index != 0 || !ClientSnitchState.shouldAnnotateTaskText()) return;
		ClientSnitchState.noteWatheTaskTextVisible();
		text = text.copy().append(Text.literal(" ")).append(ClientSnitchState.taskProgressText());
	}
}
