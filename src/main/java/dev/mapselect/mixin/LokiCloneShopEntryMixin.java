package dev.mapselect.mixin;
import dev.doctor4t.wathe.util.ShopEntry;
import dev.mapselect.role.loki.LokiManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Also protects optional-mod shops that insert directly rather than using Wathe's purchase handler. */
@Mixin(value=ShopEntry.class,remap=false,priority=2100)
public abstract class LokiCloneShopEntryMixin {
    @Inject(method="onBuy",at=@At("HEAD"),cancellable=true)
    private void gexpress$cloneCannotBuy(PlayerEntity player,CallbackInfoReturnable<Boolean> cir){if(player instanceof ServerPlayerEntity server && LokiManager.isControllingClone(server))cir.setReturnValue(false);}
    @Inject(method="insertStackInFreeSlot",at=@At("HEAD"),cancellable=true)
    private static void gexpress$cloneCannotReceiveItems(PlayerEntity player,ItemStack stack,CallbackInfoReturnable<Boolean> cir){if(player instanceof ServerPlayerEntity server && LokiManager.isControllingClone(server))cir.setReturnValue(false);}
}
