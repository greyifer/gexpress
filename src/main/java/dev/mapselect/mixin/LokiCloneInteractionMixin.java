package dev.mapselect.mixin;
import dev.mapselect.role.loki.LokiManager;
import net.minecraft.server.network.*;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Guard execution on the server thread, independently of packet handlers or client prediction. */
@Mixin(ServerPlayerInteractionManager.class)
public abstract class LokiCloneInteractionMixin {
    @Shadow protected ServerPlayerEntity player;
    @Inject(method="interactItem",at=@At("HEAD"),cancellable=true)
    private void gexpress$noCloneItemUse(ServerPlayerEntity user,World world,ItemStack stack,Hand hand,CallbackInfoReturnable<ActionResult> cir){if(LokiManager.isControllingClone(player))cir.setReturnValue(ActionResult.FAIL);}
    @Inject(method="interactBlock",at=@At("HEAD"),cancellable=true)
    private void gexpress$allowEmptyHandMapInteraction(ServerPlayerEntity user,World world,ItemStack stack,Hand hand,BlockHitResult hit,CallbackInfoReturnable<ActionResult> cir){if(LokiManager.isControllingClone(player)&&!stack.isEmpty())cir.setReturnValue(ActionResult.FAIL);}
}
