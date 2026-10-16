package dev.mapselect.client.role.twins;

import dev.mapselect.network.role.twins.TwinsBodyPayload;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import java.util.*;

public final class ClientTwinsBodies {
    private static final Map<UUID,UUID> OWNERS=new HashMap<>();
    private ClientTwinsBodies(){}
    public static void register(){
        dev.mapselect.role.loki.BodyDisplayIdentity.clientResolver(ClientTwinsBodies::owner);
        ClientPlayNetworking.registerGlobalReceiver(TwinsBodyPayload.ID,(p,c)->c.client().execute(()->{if(p.present())OWNERS.put(p.bodyId(),p.ownerId());else OWNERS.remove(p.bodyId());}));
        ClientPlayConnectionEvents.DISCONNECT.register((h,c)->OWNERS.clear());
    }
    public static UUID owner(Entity entity){return entity==null?null:OWNERS.get(entity.getUuid());}
    public static boolean isBody(Entity entity){return owner(entity)!=null;}
    public static Entity localBody(MinecraftClient client){
        if(client.player==null || client.world==null)return null;
        for(Entity entity:client.world.getEntities())if(client.player.getUuid().equals(owner(entity)))return entity;
        return null;
    }
}
