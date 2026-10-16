package dev.mapselect.entity;

import com.mojang.authlib.GameProfile;
import dev.mapselect.role.twins.TwinsManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.*;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.server.network.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import java.util.*;

/** Native player physics, inventory and tracking; the actor is never a logged-in user. */
public final class TwinFakePlayerEntity extends ServerPlayerEntity {
    private UUID ownerId;
    private String ownerName="", decoyRole="";
    private boolean reported;
    public TwinFakePlayerEntity(ServerWorld world,ServerPlayerEntity owner) {
        super(world.getServer(),world,profile(owner),SyncedClientOptions.createDefault());
        networkHandler=new ServerPlayNetworkHandler(world.getServer(),new BodyConnection(),this,ConnectedClientData.createDefault(getGameProfile(),false));
        changeGameMode(GameMode.ADVENTURE);
        getDataTracker().set(PLAYER_MODEL_PARTS,(byte)0x7F);
        initializeTwin(owner.getUuid(),owner.getGameProfile().getName(),"");
        getHungerManager().setFoodLevel(20);
    }
    private static GameProfile profile(ServerPlayerEntity owner) {
        GameProfile profile=new GameProfile(UUID.randomUUID(),owner.getGameProfile().getName());
        profile.getProperties().putAll(owner.getGameProfile().getProperties()); return profile;
    }
    public void initializeTwin(UUID owner,String name,String role) { ownerId=owner; ownerName=name; decoyRole=role; }
    public Optional<UUID> ownerId() { return Optional.ofNullable(ownerId); }
    public String ownerName() { return ownerName; }
    public String decoyRoleId() { return decoyRole; }
    public void storeInventoryFrom(ServerPlayerEntity owner) {
        getInventory().clear(); getInventory().readNbt(owner.getInventory().writeNbt(new NbtList()));
        getInventory().selectedSlot=owner.getInventory().selectedSlot;
    }
    public NbtList copyStoredInventory() { return getInventory().writeNbt(new NbtList()); }
    public int storedSelectedSlot() { return getInventory().selectedSlot; }
    public void restoreStoredInventory(ServerPlayerEntity player) {
        player.getInventory().clear(); player.getInventory().readNbt(copyStoredInventory()); player.getInventory().selectedSlot=storedSelectedSlot(); player.getInventory().markDirty();
    }
    public void setTwinAnchor(double x,double y,double z,float yaw,float pitch) {
        refreshPositionAndAngles(x,y,z,yaw,pitch); setHeadYaw(yaw); setBodyYaw(yaw); setVelocity(Vec3d.ZERO); velocityModified=true; fallDistance=0;
        networkHandler.syncWithPlayerPosition();
    }
    @Override public void tick() {
        var owner=getServer().getPlayerManager().getPlayer(ownerId);
        if(owner==null || owner.getWorld()!=getWorld() || !dev.doctor4t.wathe.game.GameFunctions.isPlayerAliveAndSurvival(owner)) { discard(); return; }
        super.tick(); super.playerTick();
        networkHandler.syncWithPlayerPosition();
    }
    @Override public void onDeath(DamageSource source) {
        if(reported) return; reported=true; setHealth(0); TwinsManager.onBodyDeath(this,source);
    }
    @Override public boolean shouldSave() { return false; }
    @Override public void remove(Entity.RemovalReason reason) {
        if(!isRemoved()) for(ServerPlayerEntity viewer:getServer().getPlayerManager().getPlayerList()) {
            viewer.networkHandler.sendPacket(new PlayerRemoveS2CPacket(List.of(getUuid())));
            if(net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(viewer,dev.mapselect.network.role.twins.TwinsBodyPayload.ID)) net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(viewer,new dev.mapselect.network.role.twins.TwinsBodyPayload(ownerId,getUuid(),false));
        }
        super.remove(reason);
    }
    /** No socket, keepalive, chat, or login events are attached to this actor. */
    private static final class BodyConnection extends ClientConnection {
        BodyConnection() { super(NetworkSide.SERVERBOUND); }
        @Override public boolean isOpen() { return true; }
        @Override public boolean isLocal() { return true; }
        @Override public void send(Packet<?> packet) {}
        @Override public void send(Packet<?> packet,PacketCallbacks callbacks) {}
        @Override public void send(Packet<?> packet,PacketCallbacks callbacks,boolean flush) {}
    }
}
