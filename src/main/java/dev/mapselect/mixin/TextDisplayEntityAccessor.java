package dev.mapselect.mixin;

import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(DisplayEntity.TextDisplayEntity.class)
public interface TextDisplayEntityAccessor {
	@Invoker("setText")
	void gexpress$setText(Text text);

	@Invoker("setLineWidth")
	void gexpress$setLineWidth(int lineWidth);

	@Invoker("setTextOpacity")
	void gexpress$setTextOpacity(byte opacity);

	@Invoker("setBackground")
	void gexpress$setBackground(int background);

	@Invoker("setDisplayFlags")
	void gexpress$setDisplayFlags(byte flags);
}
