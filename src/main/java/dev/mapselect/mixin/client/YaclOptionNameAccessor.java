package dev.mapselect.mixin.client;

import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = {
	"dev.isxander.yacl3.impl.OptionImpl",
	"dev.isxander.yacl3.impl.ListOptionImpl",
	"dev.isxander.yacl3.impl.ButtonOptionImpl"
}, remap = false)
public interface YaclOptionNameAccessor {
	@Mutable
	@Accessor("name")
	void gexpress$setName(Text name);
}
