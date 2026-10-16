package dev.mapselect.mixin;

import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(ItemCooldownManager.class)
public interface ItemCooldownManagerAccessor {
	@Accessor("entries")
	Map<Item, ?> gexpress$getEntries();

	@Accessor("tick")
	int gexpress$getTick();
}
