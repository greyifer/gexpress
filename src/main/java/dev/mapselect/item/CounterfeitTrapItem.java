package dev.mapselect.item;

import dev.doctor4t.wathe.util.AdventureUsable;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.registry.Registries;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import java.util.UUID;

/** Uses the installed trap's placement, entity, radius, sounds and report item. */
public final class CounterfeitTrapItem extends CounterfeitItem implements AdventureUsable {
    public static final String TAG="gexpress_counterfeit_trap";
    private static final ThreadLocal<UUID> PLACING=new ThreadLocal<>();
    public static boolean placing(){return PLACING.get()!=null;}
    @Override public ActionResult useOnBlock(ItemUsageContext context){
        var id=Identifier.of("noellesroles","role_mine");
        if(context.getPlayer()==null || !Registries.ITEM.containsId(id))return ActionResult.FAIL;
        PLACING.set(context.getPlayer().getUuid());
        try{return Registries.ITEM.get(id).useOnBlock(context);}finally{PLACING.remove();}
    }
}
