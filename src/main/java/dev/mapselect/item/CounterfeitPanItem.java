package dev.mapselect.item;
import net.minecraft.item.*;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import net.minecraft.util.*;
import net.minecraft.registry.Registries;
/** Copies the pan's use pose without calling its stun logic or sending its ability payload. */
public final class CounterfeitPanItem extends CounterfeitItem {
    private Item original(){return Registries.ITEM.get(net.minecraft.util.Identifier.of("kinswathe","pan"));}
    @Override public TypedActionResult<ItemStack> use(World world,PlayerEntity user,Hand hand){user.setCurrentHand(hand);return TypedActionResult.consume(user.getStackInHand(hand));}
    @Override public UseAction getUseAction(ItemStack stack){return original().getUseAction(original().getDefaultStack());}
    @Override public int getMaxUseTime(ItemStack stack,LivingEntity user){return original().getMaxUseTime(original().getDefaultStack(),user);}
    @Override public void onStoppedUsing(ItemStack stack,World world,LivingEntity user,int remaining){user.swingHand(user.getActiveHand());}
}
