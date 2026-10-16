package dev.mapselect.mixin.client;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(HeldItemRenderer.class)
public interface ReplayHeldItemInvoker {
    @Invoker("renderFirstPersonItem")
    void gexpress$replayHand(AbstractClientPlayerEntity player,float tickDelta,float pitch,Hand hand,float swing,ItemStack item,float equip,MatrixStack matrices,VertexConsumerProvider consumers,int light);
}
