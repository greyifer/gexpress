package dev.mapselect.mixin.client;
import net.minecraft.entity.LimbAnimator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(LimbAnimator.class)
public interface ReplayLimbAnimatorAccessor {
    @Accessor("pos") void gexpress$phase(float value);
    @Accessor("speed") void gexpress$speed(float value);
    @Accessor("prevSpeed") void gexpress$previousSpeed(float value);
}
