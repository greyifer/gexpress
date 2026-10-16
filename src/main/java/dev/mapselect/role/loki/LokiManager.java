package dev.mapselect.role.loki;

import dev.doctor4t.wathe.api.event.*;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.*;
import dev.mapselect.entity.TwinFakePlayerEntity;
import dev.mapselect.game.*;
import dev.mapselect.network.PayloadSync;
import dev.mapselect.network.role.loki.*;
import dev.mapselect.registry.MapSelectRoles;
import dev.mapselect.role.AbilityTargeting;
import dev.mapselect.role.pelican.PelicanManager;
import dev.mapselect.role.twins.TwinsManager;
import dev.mapselect.testing.GexpressTestState;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class LokiManager {
    private static final class Round {
        final Map<UUID,long[]> cooldowns=new HashMap<>();
        final Map<UUID,UUID> selections=new HashMap<>();
        final Map<UUID,Long> hex=new HashMap<>(), gravity=new HashMap<>();
        final Map<UUID,Long> pendingWarp=new HashMap<>();
        final Map<UUID,LokiFuse> fuses=new HashMap<>();
        final LokiVoicePairs voices=new LokiVoicePairs();
    }
    private record Clone(ServerWorld world,TwinFakePlayerEntity body,long until) {}
    private static final Map<ServerWorld,Round> ROUNDS=new ConcurrentHashMap<>();
    private static final Map<UUID,Clone> CLONES=new HashMap<>();
    private LokiManager() {}
    public static void register(){
        PayloadTypeRegistry.playC2S().register(LokiUsePayload.ID,LokiUsePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(LokiFusePayload.ID,LokiFusePayload.CODEC);
        net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register((user,world,hand,entity,hit)->{
            if(!(entity instanceof ServerPlayerEntity target) || !(user instanceof ServerPlayerEntity player))return net.minecraft.util.ActionResult.PASS;
            return passFuse(player,target);
        });
        PayloadTypeRegistry.playS2C().register(LokiStatePayload.ID,LokiStatePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(LokiEffectsPayload.ID,LokiEffectsPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(LokiUsePayload.ID,(p,c)->c.server().execute(()->use(c.player(),p)));
        ServerTickEvents.END_WORLD_TICK.register(LokiManager::tick);
        RoundLifecycleHooks.clearWorld(w->{if(w instanceof ServerWorld world)clear(world);});
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->{
            endClone(handler.player,true);removeTarget(handler.player); });
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->server.execute(()->syncWorld(handler.player.getServerWorld())));
        ServerLifecycleEvents.SERVER_STOPPING.register(server->{for(var clone:List.copyOf(CLONES.entrySet())){var player=server.getPlayerManager().getPlayer(clone.getKey());if(player!=null)endClone(player,true);}});
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{ROUNDS.keySet().removeIf(w->w.getServer()==server);CLONES.entrySet().removeIf(e->e.getValue().world.getServer()==server);});
        AllowPlayerDeath.EVENT.register((victim,killer,reason)->{
            if(victim instanceof ServerPlayerEntity player && isControllingClone(player)){killClone(player);return false;}return true;
        });
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity,source,amount)->{
            if(entity instanceof ServerPlayerEntity player && isControllingClone(player)){killClone(player);return false;}return true;
        });
    }
    private static boolean playable(ServerPlayerEntity player){return player!=null && (DeadPlayerStatus.isLivingRoundParticipant(player)||GexpressTestState.isRoleTester(player)) && !PelicanManager.isStashed(player);}
    private static boolean loki(ServerPlayerEntity player){var game=GameWorldComponent.KEY.getNullable(player.getWorld());var role=game==null?null:game.getRole(player);return role!=null&&MapSelectRoles.LOKI_ID.equals(role.identifier());}
    private static void use(ServerPlayerEntity player,LokiUsePayload payload){
        LokiAbility ability=LokiAbility.fromIndex(payload.ability());if(ability==null)return;
        if(ability==LokiAbility.CLONE && isControllingClone(player)){endClone(player,true);return;}
        if(!loki(player)||!playable(player)||isControllingClone(player)||!GexpressAbilityGuards.checkItemAbility(player).allowed())return;
        ServerWorld world=player.getServerWorld();long now=world.getTime();Round round=ROUNDS.computeIfAbsent(world,w->new Round());
        long[] timers=round.cooldowns.computeIfAbsent(player.getUuid(),id->new long[4]);
        if(timers[ability.ordinal()]>now){player.sendMessage(Text.literal("Loki cooldown: "+(timers[ability.ordinal()]-now+19)/20+"s."),true);return;}
        if(ability==LokiAbility.CLONE){if(startClone(player,now+ability.durationTicks))timers[ability.ordinal()]=now+ability.cooldownTicks;sync(player,round);return;}
        ServerPlayerEntity target=payload.target()==null?null:world.getServer().getPlayerManager().getPlayer(payload.target());
        if(target==null||target.getWorld()!=world||!AbilityTargeting.canTarget(player,target,4.0,0.25,true,LokiManager::playable)){player.sendMessage(Text.literal("Get close and look at a living player."),true);return;}
        if(ability==LokiAbility.VOICES){
            UUID firstId=round.selections.get(player.getUuid());
            if(firstId==null){round.selections.put(player.getUuid(),target.getUuid());player.sendMessage(Text.literal("First voice selected: "+target.getName().getString()+". Select another player."),true);sync(player,round);return;}
            if(firstId.equals(target.getUuid())){player.sendMessage(Text.literal("Select a different second player."),true);return;}
            ServerPlayerEntity first=world.getServer().getPlayerManager().getPlayer(firstId);
            if(!playable(first)||first.getWorld()!=world){round.selections.remove(player.getUuid());sync(player,round);return;}
            round.voices.swap(firstId,target.getUuid(),now+ability.durationTicks);round.selections.remove(player.getUuid());
            player.sendMessage(Text.literal("Their voices have been swapped for two minutes."),true);
        }else if(ability==LokiAbility.HEX){
            round.pendingWarp.put(target.getUuid(),ability.activationTick(now));
        }else{
            if(loki(target)||round.fuses.containsKey(target.getUuid())){player.sendMessage(Text.literal("Choose a non-Loki player without a fuse."),true);return;}
            round.fuses.put(target.getUuid(),new LokiFuse(player.getUuid(),target.getUuid(),player.getUuid(),ability.expiryTick(now)));
            syncFuse(target,round);
        }
        timers[ability.ordinal()]=now+ability.cooldownTicks;sync(player,round);syncWorld(world);
    }
    private static net.minecraft.util.ActionResult passFuse(ServerPlayerEntity player,ServerPlayerEntity target){
        Round round=ROUNDS.get(player.getServerWorld());LokiFuse fuse=round==null?null:round.fuses.get(player.getUuid());
        if(fuse==null)return net.minecraft.util.ActionResult.PASS;
        if(player.getWorld().getTime()<fuse.expires()-600 || fuse.expires()<=player.getWorld().getTime() || !playable(player) || !playable(target) || loki(target) || target.getWorld()!=player.getWorld()
            || round.fuses.containsKey(target.getUuid()) || !fuse.canPassTo(target.getUuid()) || !GexpressAbilityGuards.checkItemAbility(player).allowed()
            || !AbilityTargeting.canTarget(player,target,4.0,0.25,true,LokiManager::playable)){
            player.sendMessage(Text.literal("Cannot pass the fuse to this player. You cannot give it straight back."),true);
            return net.minecraft.util.ActionResult.FAIL;
        }
        round.fuses.remove(player.getUuid());round.fuses.put(target.getUuid(),fuse.passTo(target.getUuid()));
        syncFuse(player,round);syncFuse(target,round);return net.minecraft.util.ActionResult.SUCCESS;
    }
    private static void syncFuse(ServerPlayerEntity player,Round round){
        LokiFuse fuse=round==null?null:round.fuses.get(player.getUuid());
        PayloadSync.sendIfChanged(player,LokiFusePayload.ID,new LokiFusePayload(fuse==null?0:remaining(fuse.expires(),player.getWorld().getTime())));
    }
    public static ServerPlayerEntity voiceSource(ServerPlayerEntity speaker){
        Round round=ROUNDS.get(speaker.getServerWorld());if(round==null)return speaker;
        UUID id=round.voices.partner(speaker.getUuid(),speaker.getWorld().getTime());
        ServerPlayerEntity target=id==null?null:speaker.getServer().getPlayerManager().getPlayer(id);
        return target!=null && !target.isDisconnected() && target.getWorld()==speaker.getWorld() ? target:speaker;
    }
    public static boolean gravityFlipped(Entity entity){Round round=ROUNDS.get(entity.getWorld());return round!=null&&round.gravity.getOrDefault(entity.getUuid(),0L)>entity.getWorld().getTime();}
    public static boolean isControllingClone(ServerPlayerEntity player){return CLONES.containsKey(player.getUuid());}
    public static boolean isLokiBody(TwinFakePlayerEntity body){return MapSelectRoles.LOKI_ID.toString().equals(body.decoyRoleId());}
    private static boolean startClone(ServerPlayerEntity player,long until){
        ServerWorld world=player.getServerWorld();Vec3d spawn=null;
        for(int i=0;i<4;i++){
            double angle=Math.toRadians(player.getYaw()+i*90);Vec3d offset=new Vec3d(-Math.sin(angle)*1.7,0,Math.cos(angle)*1.7);
            if(world.isSpaceEmpty(player,player.getBoundingBox().stretch(offset))){spawn=player.getPos().add(offset);break;}
        }
        if(spawn==null){player.sendMessage(Text.literal("Make room beside you before summoning a clone."),true);return false;}
        var body=new TwinFakePlayerEntity(world,player);body.initializeTwin(player.getUuid(),player.getGameProfile().getName(),MapSelectRoles.LOKI_ID.toString());
        body.setTwinAnchor(player.getX(),player.getY(),player.getZ(),player.getYaw(),player.getPitch());body.setHealth(player.getHealth());body.storeInventoryFrom(player);
        for(var viewer:world.getServer().getPlayerManager().getPlayerList())TwinsManager.sendBody(viewer,body,true);
        if(!world.spawnEntity(body)){body.discard();return false;}
        CLONES.put(player.getUuid(),new Clone(world,body,until));
        player.stopUsingItem();player.closeHandledScreen();player.getInventory().clear();player.getInventory().markDirty();player.playerScreenHandler.sendContentUpdates();
        player.teleport(world,spawn.x,spawn.y,spawn.z,player.getYaw(),player.getPitch());player.setHealth(player.getMaxHealth());
        player.sendMessage(Text.literal("Controlling your clone. Fourth Ability returns you to your body."),true);return true;
    }
    private static void killClone(ServerPlayerEntity player){
        if(!isControllingClone(player))return;
        // Make the corpse before teleporting back so it remains at the clone's death site.
        var corpse=dev.doctor4t.wathe.index.WatheEntities.PLAYER_BODY.create(player.getServerWorld());
        if(corpse!=null){
            corpse.setPlayerUuid(player.getUuid());
            corpse.refreshPositionAndAngles(player.getX(),player.getY(),player.getZ(),player.getYaw(),0);
            corpse.setHeadYaw(player.getHeadYaw());corpse.setBodyYaw(player.getBodyYaw());
            corpse.setVelocity(Vec3d.ZERO);player.getServerWorld().spawnEntity(corpse);
        }
        endClone(player,true);
    }
    private static void endClone(ServerPlayerEntity player,boolean restore){
        Clone session=CLONES.remove(player.getUuid());if(session==null)return;
        var body=session.body;
        if(restore){
            player.teleport(session.world,body.getX(),body.getY(),body.getZ(),body.getYaw(),body.getPitch());
            body.restoreStoredInventory(player);player.setHealth(Math.max(1,body.getHealth()));player.setVelocity(Vec3d.ZERO);player.fallDistance=0;
            player.currentScreenHandler.sendContentUpdates();
        }
        body.getInventory().clear();body.discard();
        Round round=ROUNDS.get(player.getServerWorld());if(round!=null)sync(player,round);
    }
    public static boolean bodyDeath(TwinFakePlayerEntity body,DamageSource source){
        if(!isLokiBody(body))return false;
        var owner=body.ownerId().map(id->body.getServer().getPlayerManager().getPlayer(id)).orElse(null);
        if(owner!=null && isControllingClone(owner)){
            endClone(owner,true);GameFunctions.killPlayer(owner,true,source.getAttacker() instanceof PlayerEntity p?p:null,GameConstants.DeathReasons.GENERIC);
        }else body.discard();return true;
    }
    private static void removeTarget(ServerPlayerEntity player){
        Round round=ROUNDS.get(player.getWorld());if(round==null)return;
        round.pendingWarp.remove(player.getUuid());round.fuses.remove(player.getUuid());syncFuse(player,round);
        round.voices.remove(player.getUuid());round.hex.remove(player.getUuid());round.gravity.remove(player.getUuid());
        round.selections.remove(player.getUuid());round.selections.values().removeIf(player.getUuid()::equals);
    }
    private static void tick(ServerWorld world){
        for(var entry:List.copyOf(CLONES.entrySet())){
            Clone clone=entry.getValue();if(clone.world!=world)continue;
            var owner=world.getServer().getPlayerManager().getPlayer(entry.getKey());
            if(owner!=null && (owner.getWorld()!=world || world.getTime()>=clone.until || !loki(owner) || !playable(owner)))endClone(owner,true);
        }
        Round round=ROUNDS.get(world);if(round==null)return;long now=world.getTime();
        for(var pending:List.copyOf(round.pendingWarp.entrySet())){
            if(now<pending.getValue())continue;round.pendingWarp.remove(pending.getKey());
            var target=world.getServer().getPlayerManager().getPlayer(pending.getKey());
            if(playable(target)&&target.getWorld()==world){target.stopRiding();target.stopFallFlying();target.fallDistance=0;long until=pending.getValue()+600;round.hex.put(target.getUuid(),until);round.gravity.put(target.getUuid(),until);}
        }
        for(var fuse:List.copyOf(round.fuses.values())){
            var holder=world.getServer().getPlayerManager().getPlayer(fuse.holder());
            if(holder==null||holder.getWorld()!=world||!playable(holder)||loki(holder)){round.fuses.remove(fuse.holder());if(holder!=null)syncFuse(holder,round);continue;}
            if(now>=fuse.expires()){
                round.fuses.remove(fuse.holder());syncFuse(holder,round);
                var caster=world.getServer().getPlayerManager().getPlayer(fuse.caster());LokiFuseExplosion.explode(world,holder.getPos(),caster);
            }else if(now%10==0){syncFuse(holder,round);}
        }
        for(var player:world.getPlayers()){
            Clone clone=CLONES.get(player.getUuid());
            if(clone!=null && !player.getInventory().isEmpty()){
                player.stopUsingItem();player.getInventory().clear();player.getInventory().markDirty();player.currentScreenHandler.sendContentUpdates();
            }
            if(clone!=null&&(now>=clone.until||!loki(player)||!playable(player)||clone.world!=world))endClone(player,true);
            if(!playable(player)){removeTarget(player);continue;}
            round.voices.partner(player.getUuid(),now);
            if(round.gravity.getOrDefault(player.getUuid(),Long.MAX_VALUE)<=now){round.gravity.remove(player.getUuid());player.fallDistance=0;}
            if(round.hex.getOrDefault(player.getUuid(),Long.MAX_VALUE)<=now)round.hex.remove(player.getUuid());
            if(now%10==0 && loki(player))sync(player,round);
        }
        if(now%10==0)syncWorld(world);
    }
    private static int remaining(long until,long now){return (int)Math.max(0,until-now);}
    private static void sync(ServerPlayerEntity player,Round round){
        long now=player.getWorld().getTime();long[] cd=round.cooldowns.getOrDefault(player.getUuid(),new long[4]);UUID first=round.selections.get(player.getUuid());
        var target=first==null?null:player.getServer().getPlayerManager().getPlayer(first);Clone clone=CLONES.get(player.getUuid());
        PayloadSync.sendIfChanged(player,LokiStatePayload.ID,new LokiStatePayload(first,target==null?"":target.getName().getString(),remaining(cd[0],now),remaining(cd[1],now),remaining(cd[2],now),remaining(cd[3],now),clone==null?0:remaining(clone.until,now)));
    }
    private static void syncWorld(ServerWorld world){
        Round round=ROUNDS.get(world);List<LokiEffectsPayload.Effect> effects=new ArrayList<>();
        if(round!=null)for(var player:world.getPlayers()){
            int hex=remaining(round.hex.getOrDefault(player.getUuid(),0L),world.getTime()),gravity=remaining(round.gravity.getOrDefault(player.getUuid(),0L),world.getTime());
            if(hex>0||gravity>0)effects.add(new LokiEffectsPayload.Effect(player.getUuid(),hex,gravity));
        }
        var payload=new LokiEffectsPayload(effects);for(var player:world.getPlayers())PayloadSync.sendIfChanged(player,LokiEffectsPayload.ID,payload);
    }
    private static void clear(ServerWorld world){
        for(var player:world.getPlayers())if(isControllingClone(player))endClone(player,false);
        ROUNDS.remove(world);syncWorld(world);
        for(var player:world.getPlayers())syncFuse(player,null);
        for(var player:world.getPlayers()){player.fallDistance=0;PayloadSync.sendIfChanged(player,LokiStatePayload.ID,LokiStatePayload.empty());}
    }
}
