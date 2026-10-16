package dev.mapselect.item;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.util.*;
import net.minecraft.world.World;

/** A visual prop, never an instance of the item it impersonates. */
public class CounterfeitItem extends Item {
    public CounterfeitItem() { this(new Settings().maxCount(1)); }
    protected CounterfeitItem(Settings settings) { super(settings); }
    @Override public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        return TypedActionResult.fail(user.getStackInHand(hand));
    }
    @Override public ActionResult useOnBlock(ItemUsageContext context) { return ActionResult.FAIL; }
    @Override public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) { return ActionResult.FAIL; }
}
