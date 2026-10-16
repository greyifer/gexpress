package dev.mapselect.role;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;

/** Cursor-held items still belong to the player while moving between inventory slots. */
public final class RoleItemInventory {
    private RoleItemInventory() {}
    public static int count(PlayerEntity player, Item item) {
        return count(player.getInventory(),item,player.currentScreenHandler.getCursorStack(),player.playerScreenHandler.getCursorStack());
    }
    static int count(net.minecraft.inventory.Inventory inventory,Item item,net.minecraft.item.ItemStack... cursors) {
        return countWithCursors(inventory.count(item),cursors,cursor->cursor.isOf(item)?cursor.getCount():0);
    }
    static <T> int countWithCursors(int inventoryCount,T[] cursors,java.util.function.ToIntFunction<T> countForItem) {
        int count=inventoryCount;
        var seen=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<T,Boolean>());
        for(var cursor:cursors) if(cursor!=null && seen.add(cursor)) count+=countForItem.applyAsInt(cursor);
        return count;
    }
}
