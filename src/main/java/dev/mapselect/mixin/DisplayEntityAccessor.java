package dev.mapselect.mixin;

import net.minecraft.entity.decoration.Brightness;
import net.minecraft.entity.decoration.DisplayEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(DisplayEntity.class)
public interface DisplayEntityAccessor {
	@Invoker("setBillboardMode")
	void gexpress$setBillboardMode(DisplayEntity.BillboardMode mode);

	@Invoker("setViewRange")
	void gexpress$setViewRange(float viewRange);

	@Invoker("setShadowRadius")
	void gexpress$setShadowRadius(float shadowRadius);

	@Invoker("setShadowStrength")
	void gexpress$setShadowStrength(float shadowStrength);

	@Invoker("setDisplayWidth")
	void gexpress$setDisplayWidth(float width);

	@Invoker("setDisplayHeight")
	void gexpress$setDisplayHeight(float height);

	@Invoker("setBrightness")
	void gexpress$setBrightness(Brightness brightness);

	@Invoker("setTeleportDuration")
	void gexpress$setTeleportDuration(int duration);
}
