package dev.mapselect.mixin;

import dev.mapselect.replay.ReplayRecorder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.*;
import net.minecraft.particle.*;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.*;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(ServerWorld.class)
public abstract class ReplayEffectsMixin {
    @Inject(method="playSound",at=@At("HEAD"))
    private void gexpress$sound(PlayerEntity except,double x,double y,double z,RegistryEntry<SoundEvent> sound,SoundCategory category,float volume,float pitch,long seed,CallbackInfo ci) {
        recordSound(x,y,z,sound,category,volume,pitch,seed);
    }
    @Inject(method="playSoundFromEntity",at=@At("HEAD"))
    private void gexpress$entitySound(PlayerEntity except,Entity entity,RegistryEntry<SoundEvent> sound,SoundCategory category,float volume,float pitch,long seed,CallbackInfo ci) {
        recordSound(entity.getX(),entity.getY(),entity.getZ(),sound,category,volume,pitch,seed);
    }
    private void recordSound(double x,double y,double z,RegistryEntry<SoundEvent> sound,SoundCategory category,float volume,float pitch,long seed) {
        if(!ReplayRecorder.isRecording((ServerWorld)(Object)this)) return;
        NbtCompound data=new NbtCompound(); data.putString("kind","sound"); data.putString("sound",sound.value().getId().toString());
        data.putString("category",category.name()); data.putFloat("volume",volume); data.putFloat("pitch",pitch); data.putLong("seed",seed);
        ReplayRecorder.effect((ServerWorld)(Object)this,new Vec3d(x,y,z),data);
    }
    @Inject(method="spawnParticles(Lnet/minecraft/particle/ParticleEffect;DDDIDDDD)I",at=@At("HEAD"))
    private <T extends ParticleEffect> void gexpress$particles(T particle,double x,double y,double z,int count,double dx,double dy,double dz,double speed,CallbackInfoReturnable<Integer> cir) {
        if(!ReplayRecorder.isRecording((ServerWorld)(Object)this)) return;
        ParticleTypes.TYPE_CODEC.encodeStart(NbtOps.INSTANCE,particle).result().ifPresent(encoded->{
            NbtCompound data=new NbtCompound(); data.putString("kind","particles"); data.put("particle",encoded); data.putInt("count",count);
            data.putDouble("dx",dx); data.putDouble("dy",dy); data.putDouble("dz",dz); data.putDouble("speed",speed);
            ReplayRecorder.effect((ServerWorld)(Object)this,new Vec3d(x,y,z),data);
        });
    }
}
