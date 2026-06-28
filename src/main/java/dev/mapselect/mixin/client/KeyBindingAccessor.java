package dev.mapselect.mixin.client;

import net.minecraft.client.option.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(KeyBinding.class)
public interface KeyBindingAccessor {
	@Accessor("translationKey")
	String gexpress$translationKey();

	@Accessor("pressed")
	boolean gexpress$pressed();

	@Mutable
	@Accessor("timesPressed")
	void gexpress$timesPressed(int timesPressed);
}
