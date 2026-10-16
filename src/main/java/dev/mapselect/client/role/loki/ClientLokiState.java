package dev.mapselect.client.role.loki;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.mapselect.client.ability.ClientAbilityTargetState;
import dev.mapselect.client.game.ClientRoleRevealState;
import dev.mapselect.client.input.ClientAbilityKeys;
import dev.mapselect.network.role.loki.*;
import dev.mapselect.registry.MapSelectRoles;
import dev.mapselect.role.loki.LokiGravity;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import java.util.*;

public final class ClientLokiState {
    private static LokiStatePayload state=LokiStatePayload.empty();
    private static final Map<UUID,Long> HEX=new HashMap<>();
    private static final boolean[] DOWN=new boolean[4];
    private static Object world;
    private static long receivedAt;
    private static long fuseUntil;
    private ClientLokiState() {}
    public static void register(){
        ClientPlayNetworking.registerGlobalReceiver(LokiStatePayload.ID,(p,c)->c.client().execute(()->{state=p;receivedAt=c.client().world==null?0:c.client().world.getTime();world=c.client().world;ClientAbilityTargetState.setLokiSelection(p.firstTarget());}));
        ClientPlayNetworking.registerGlobalReceiver(LokiEffectsPayload.ID,(p,c)->c.client().execute(()->{
            if(c.client().world==null)return;world=c.client().world;long now=c.client().world.getTime();HEX.clear();LokiGravity.clearClient();
            for(var effect:p.effects()){if(effect.hex()>0)HEX.put(effect.player(),now+effect.hex());if(effect.gravity()>0)LokiGravity.client(effect.player(),now+effect.gravity());}
        }));
        ClientPlayNetworking.registerGlobalReceiver(LokiFusePayload.ID,(p,c)->c.client().execute(()->{if(c.client().world!=null){world=c.client().world;fuseUntil=c.client().world.getTime()+p.remaining();}}));
        net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register((user,w,hand,entity,hit)->w.isClient && user==MinecraftClient.getInstance().player && fuseRemaining()>0 && fuseRemaining()<=600 && entity instanceof net.minecraft.entity.player.PlayerEntity ? net.minecraft.util.ActionResult.SUCCESS : net.minecraft.util.ActionResult.PASS);
        net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register((user,w,hand)->w.isClient && user==MinecraftClient.getInstance().player && cloneActive()?net.minecraft.util.TypedActionResult.fail(user.getStackInHand(hand)):net.minecraft.util.TypedActionResult.pass(user.getStackInHand(hand)));
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((user,w,hand,hit)->w.isClient && user==MinecraftClient.getInstance().player && cloneActive()&&!user.getStackInHand(hand).isEmpty()?net.minecraft.util.ActionResult.FAIL:net.minecraft.util.ActionResult.PASS);
        ClientPlayConnectionEvents.DISCONNECT.register((h,c)->clear());
        ClientTickEvents.END_CLIENT_TICK.register(ClientLokiState::tick);
        HudRenderCallback.EVENT.register(ClientLokiState::hud);
    }
    private static void clear(){state=LokiStatePayload.empty();fuseUntil=0;HEX.clear();LokiGravity.clearClient();Arrays.fill(DOWN,false);world=null;ClientAbilityTargetState.setLokiSelection(null);}
    public static boolean hexed(){var client=MinecraftClient.getInstance();return client.player!=null && client.world!=null && world==client.world && HEX.getOrDefault(client.player.getUuid(),0L)>client.world.getTime();}
    public static boolean cloneActive(){return remaining(state.cloneRemaining())>0;}
    public static int fuseRemaining(){var client=MinecraftClient.getInstance();return client.world==null||world!=client.world?0:(int)Math.max(0,fuseUntil-client.world.getTime());}
    public static int cooldown(int index){return remaining(switch(index){case 0->state.voices();case 1->state.hex();case 2->state.fuse();default->state.cloneCooldown();});}
    public static int cloneRemaining(){return remaining(state.cloneRemaining());}
    private static int remaining(int ticks){var client=MinecraftClient.getInstance();return client.world==null || world!=client.world?0:(int)Math.max(0,ticks-(client.world.getTime()-receivedAt));}
    private static boolean localLoki(MinecraftClient client){var game=client.world==null?null:GameWorldComponent.KEY.getNullable(client.world);var role=game==null||client.player==null?null:game.getRole(client.player);return role!=null&&MapSelectRoles.LOKI_ID.equals(role.identifier());}
    private static void tick(MinecraftClient client){
        if(world!=null && world!=client.world)clear();
        if(client.player==null||client.currentScreen!=null||!localLoki(client)||!ClientRoleRevealState.canUseRoleAbility(client)){Arrays.fill(DOWN,false);return;}
        var bindings=new net.minecraft.client.option.KeyBinding[]{ClientAbilityKeys.primaryBinding(),ClientAbilityKeys.secondaryBinding(),ClientAbilityKeys.tertiaryBinding(),ClientAbilityKeys.quaternaryBinding()};
        for(int i=0;i<4;i++){
            boolean down=ClientAbilityKeys.isDown(client,bindings[i]);
            if(down&&!DOWN[i]&&(!cloneActive()||i==3)&&ClientPlayNetworking.canSend(LokiUsePayload.ID))ClientPlayNetworking.send(new LokiUsePayload(i,i==3?null:ClientAbilityTargetState.currentTargetId()));
            DOWN[i]=down;
        }
    }
    private static void hud(DrawContext context,RenderTickCounter counter){
        var client=MinecraftClient.getInstance();
        if(client.player!=null&&!client.options.hudHidden&&fuseRemaining()>0)context.drawCenteredTextWithShadow(client.textRenderer,fuseRemaining()>600?"Trickster's Fuse activates in "+(fuseRemaining()-600+19)/20+"s":"Trickster's Fuse: "+(fuseRemaining()+19)/20+"s - Right-click a player to pass",context.getScaledWindowWidth()/2,context.getScaledWindowHeight()/2+42,0xFFFF9955);
        if(client.player==null||client.options.hudHidden||!localLoki(client)||!ClientRoleRevealState.canShowRoleHud(client))return;
        if(state.firstTarget()!=null)context.drawCenteredTextWithShadow(client.textRenderer,"First voice: "+state.firstName()+" - Select the second player",context.getScaledWindowWidth()/2,context.getScaledWindowHeight()/2+25,0xFF7BDA8F);
    }
}
