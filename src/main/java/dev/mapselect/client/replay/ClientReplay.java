package dev.mapselect.client.replay;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import dev.mapselect.MapSelect;
import dev.mapselect.replay.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.*;
import net.minecraft.nbt.*;
import net.minecraft.util.math.*;
import org.lwjgl.glfw.GLFW;
import java.io.*;
import java.util.*;

public final class ClientReplay {
    private static final Map<UUID, Entity> GHOSTS=new LinkedHashMap<>();
    private static final Map<UUID, Motion> MOTIONS=new HashMap<>();
    private static final Map<UUID, NbtCompound> PERSISTENT = new HashMap<>();
    private static final Map<UUID, byte[]> TRACKED = new HashMap<>();
    private static final java.util.concurrent.ExecutorService DECODER = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "Gexpress replay decoder"); thread.setDaemon(true); return thread;
    });
    private static volatile int connectionGeneration;
    private static int decoderGeneration = -1;
    private static ClientWorld ghostWorld;
    private static int nextEntityId=-1000000;
    private static int fragmentSerial=-1, fragmentCount, fragmentNext;
    private static ByteArrayOutputStream fragments;
    public static NbtCompound metadata=new NbtCompound();
    public static NbtList markers=new NbtList(), catalog=new NbtList();
    public static boolean active, paused, loading, buffering;
    public static int tick, duration, epoch=-1;
    public static double speed=1;
    public static long worldTime;
    public static boolean rain, thunder, hasTrain;
    private static final ReplaySceneryClock sceneryClock = new ReplaySceneryClock();
    private static boolean applyingTrainSnapshot;
    private static dev.doctor4t.wathe.api.MapEffect recordedMapEffect;
    public static boolean isReplayWorld(net.minecraft.world.World world) { return active && world != null && world == ghostWorld; }
    public static boolean applyingTrainSnapshot() { return applyingTrainSnapshot; }
    public static void restoreMapEffect(dev.doctor4t.wathe.cca.GameWorldComponent component) {
        if (recordedMapEffect != null && component.getMapEffect() != recordedMapEffect) component.setMapEffect(recordedMapEffect);
    }
    public static float sceneryTime() {
        return sceneryClock.time();
    }
    private static BlockPos origin=BlockPos.ORIGIN;
    private static Vec3d offset=Vec3d.ZERO;
    private static BlockPos stageTranslation = BlockPos.ORIGIN;
    public static BlockPos stageTranslation() { return stageTranslation; }
    private static UUID pov;
    private static final Set<Entity> GHOST_IDENTITIES = Collections.newSetFromMap(new IdentityHashMap<>());
    private static long lastFrameArrival;
    private static double frameIntervalNanos = 50_000_000;
    private static boolean awaitingTerrain;
    private static int terrainReadyTicks, terrainWaitTicks;
    private static Perspective previousPerspective;
    private static KeyBinding library, pause, back, forward, faster, slower, camera, free, controls;
    private ClientReplay() {}
    public static void register() {
        library=key("library",GLFW.GLFW_KEY_K); pause=key("pause",GLFW.GLFW_KEY_P);
        back=key("back",GLFW.GLFW_KEY_LEFT); forward=key("forward",GLFW.GLFW_KEY_RIGHT);
        faster=key("faster",GLFW.GLFW_KEY_EQUAL); slower=key("slower",GLFW.GLFW_KEY_MINUS);
        camera=key("camera",GLFW.GLFW_KEY_TAB); free=key("free",GLFW.GLFW_KEY_F); controls=key("controls",GLFW.GLFW_KEY_R);
        ClientPlayNetworking.registerGlobalReceiver(ReplayDataPayload.ID,(payload,context) -> {
            int generation = connectionGeneration;
            DECODER.execute(() -> {
                if (generation != connectionGeneration) return;
                if (decoderGeneration != generation) { fragments = null; decoderGeneration = generation; }
                receive(payload, generation);
            });
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client) -> {
            int generation = ++connectionGeneration;
            DECODER.execute(() -> { if (generation == connectionGeneration) { fragments = null; decoderGeneration = generation; } });
            clear();
        });
        ClientTickEvents.END_CLIENT_TICK.register(ClientReplay::keys);
        WorldRenderEvents.AFTER_ENTITIES.register(ClientReplay::renderGhosts);
        HudRenderCallback.EVENT.register((context,counter) -> { if(active && !(MinecraftClient.getInstance().currentScreen instanceof ReplayControlsScreen)) ReplayControlsScreen.timeline(context,-1,-1); });
    }
    private static KeyBinding key(String name,int code) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding("key.gexpress.replay."+name,code,"category.gexpress.replay"));
    }
    public static boolean canAccess() {
        var player = MinecraftClient.getInstance().player;
        return player != null && player.hasPermissionLevel(2);
    }
    public static Vec3d voicePosition(UUID speaker) {
        Entity entity = GHOSTS.get(speaker);
        return entity == null ? null : entity.getEyePos();
    }
    public static void control(String action,String value) {
        if (!canAccess() && !action.equals("stop")) return;
        if(ClientPlayNetworking.canSend(ReplayControlPayload.ID)) ClientPlayNetworking.send(new ReplayControlPayload(action,value));
    }
    private static void keys(MinecraftClient client) {
        if (active && awaitingTerrain && epoch >= 0 && client.world == ghostWorld && client.player != null) {
            terrainWaitTicks++;
            BlockPos camera = client.getCameraEntity() == null ? client.player.getBlockPos() : client.getCameraEntity().getBlockPos();
            boolean loaded = client.world.getChunkManager().isChunkLoaded(camera.getX() >> 4, camera.getZ() >> 4);
            terrainReadyTicks = loaded && client.worldRenderer.isTerrainRenderComplete() ? terrainReadyTicks + 1 : 0;
            if (terrainReadyTicks >= 5 || loaded && terrainWaitTicks >= 300) {
                awaitingTerrain = false; loading = false; control("ready", ""); client.setScreen(new ReplayControlsScreen());
            }
        }
        if(client.player==null || client.currentScreen!=null) { drainKeys(); return; }
        if(library.wasPressed()) control("list","");
        if(!active) { drainKeys(); return; }
        while(pause.wasPressed()) control("pause","");
        while(back.wasPressed()) control("seek",Integer.toString(Math.max(0,tick-100)));
        while(forward.wasPressed()) control("seek",Integer.toString(Math.min(duration,tick+100)));
        while(faster.wasPressed()) changeSpeed(1);
        while(slower.wasPressed()) changeSpeed(-1);
        while(camera.wasPressed()) cyclePov();
        while(free.wasPressed()) freeCamera();
        if(controls.wasPressed()) { client.setScreen(new ReplayControlsScreen()); client.mouse.unlockCursor(); }
        if(ghostWorld!=client.world) { control("stop",""); clear(); }
    }
    private static void drainKeys() {
        for(KeyBinding key:new KeyBinding[]{library,pause,back,forward,faster,slower,camera,free,controls}) while(key.wasPressed()) {}
    }
    public static void changeSpeed(int direction) {
        int index=2; for(int i=0;i<ReplayClock.SPEEDS.length;i++) if(speed==ReplayClock.SPEEDS[i]) index=i;
        control("speed",Double.toString(ReplayClock.SPEEDS[Math.clamp(index+direction,0,ReplayClock.SPEEDS.length-1)]));
    }
    private static void receive(ReplayDataPayload p, int generation) {
        try {
            if(p.count()<1 || p.count()>ReplayDataPayload.MAX_FRAGMENTS || p.part()<0 || p.part()>=p.count()) throw new IOException("Invalid replay fragment");
            if(p.part()==0) { fragmentSerial=p.serial(); fragmentCount=p.count(); fragmentNext=0; fragments=new ByteArrayOutputStream(); }
            if(fragments==null || p.serial()!=fragmentSerial || p.count()!=fragmentCount || p.part()!=fragmentNext) throw new IOException("Out of order replay fragment");
            if (fragments.size() > ReplayArchive.MAX_COMPRESSED_BYTES - p.bytes().length) throw new IOException("Replay message exceeds 64 MB");
            fragments.write(p.bytes()); fragmentNext++;
            if(fragmentNext!=fragmentCount) return;
            byte[] bytes=fragments.toByteArray(); fragments=null;
            NbtCompound decoded = NbtIo.readCompressed(new ByteArrayInputStream(bytes),NbtSizeTracker.of(ReplayArchive.MAX_NBT_BYTES));
            MinecraftClient.getInstance().execute(() -> {
                if (generation != connectionGeneration) return;
                try { message(decoded); }
                catch (Exception e) { MapSelect.LOGGER.error("Could not apply replay data", e); control("stop", ""); clear(); }
            });
        } catch(Exception e) {
            fragments=null; MapSelect.LOGGER.error("Could not read replay data",e);
            MinecraftClient.getInstance().execute(() -> { if (generation == connectionGeneration) { control("stop",""); clear(); } });
        }
    }
    private static void message(NbtCompound data) {
        MinecraftClient client=MinecraftClient.getInstance();
        switch(data.getString("type")) {
            case "catalog" -> { catalog=data.getList("items",NbtElement.COMPOUND_TYPE); client.setScreen(new ReplayLibraryScreen()); }
            case "buffering" -> buffering = data.getBoolean("buffering");
            case "loading" -> {
                if (loading && client.currentScreen instanceof ReplayLoadingScreen screen) screen.update(data.getString("phase"), data.getInt("progress"));
            }
            case "start" -> {
                clear(); metadata=data; markers=data.getList("markers",NbtElement.COMPOUND_TYPE); duration=data.getInt("duration");
                origin=BlockPos.fromLong(data.getLong("origin")); offset=new Vec3d(data.getDouble("offsetX"),data.getDouble("offsetY"),data.getDouble("offsetZ"));
                stageTranslation = BlockPos.ofFloored(offset).subtract(origin);
                active=true; ghostWorld=client.world; previousPerspective=client.options.getPerspective();
                awaitingTerrain = data.getBoolean("warmup"); loading = awaitingTerrain; terrainReadyTicks = terrainWaitTicks = 0;
                client.options.setPerspective(Perspective.FIRST_PERSON);
                if (awaitingTerrain) { var screen = new ReplayLoadingScreen(); screen.update("Preparing scenery", 98); client.setScreen(screen); }
                else client.setScreen(new ReplayControlsScreen());
            }
            case "frame" -> { if(active && client.world==ghostWorld) apply(data); }
            case "stop" -> {
                boolean stoppedWarmup = awaitingTerrain && client.currentScreen instanceof ReplayLoadingScreen;
                boolean openingAnother = loading && !awaitingTerrain && client.currentScreen instanceof ReplayLoadingScreen;
                clear(); loading = openingAnother;
                if (stoppedWarmup) client.setScreen(new ReplayLibraryScreen());
            }
            case "error" -> {
                if (client.currentScreen instanceof ReplayLoadingScreen) {
                    loading = false; var libraryScreen = new ReplayLibraryScreen(); libraryScreen.error = data.getString("message"); client.setScreen(libraryScreen);
                } else if(client.currentScreen instanceof ReplayLibraryScreen screen) screen.error=data.getString("message");
            }
            default -> {}
        }
    }
    private static Vec3d translated(NbtCompound data) {
        NbtList pos=data.getList("Pos",NbtElement.DOUBLE_TYPE);
        return new Vec3d(pos.getDouble(0)-origin.getX()+offset.x,pos.getDouble(1)-origin.getY()+offset.y,pos.getDouble(2)-origin.getZ()+offset.z);
    }
    private static void apply(NbtCompound frame) {
        long applyStarted = System.nanoTime();
        MinecraftClient client=MinecraftClient.getInstance();
        int nextEpoch=frame.getInt("epoch"); boolean discontinuity=epoch!=nextEpoch;
        if(discontinuity) resetVoice(); epoch=nextEpoch;
        long arrival = System.nanoTime();
        int previousTick = tick;
        tick=frame.getInt("tick"); speed=frame.getDouble("speed"); paused=frame.getBoolean("paused");
        frameIntervalNanos=dev.mapselect.replay.ReplayMotionTiming.interval(discontinuity?1:tick-previousTick,speed);
        lastFrameArrival=arrival;
        worldTime=frame.getLong("time"); rain=frame.getBoolean("rain"); thunder=frame.getBoolean("thunder");
        if (frame.contains("train")) {
            NbtCompound train = frame.getCompound("train").copy();
            hasTrain = true;
            sceneryClock.update(train.getInt("Time"), System.nanoTime(), (long)frameIntervalNanos,
                discontinuity || paused || tick < previousTick);
            train.putBoolean("Hud", false);
            applyingTrainSnapshot = true;
            try { dev.doctor4t.wathe.cca.TrainWorldComponent.KEY.get(client.world).readFromNbt(train, client.world.getRegistryManager()); }
            finally { applyingTrainSnapshot = false; }
            var effect = dev.doctor4t.wathe.api.WatheMapEffects.MAP_EFFECTS.get(net.minecraft.util.Identifier.tryParse(frame.getString("mapEffect")));
            if (effect != null) {
                recordedMapEffect = effect; restoreMapEffect(dev.doctor4t.wathe.cca.GameWorldComponent.KEY.get(client.world));
            }
        }
        // Keep lightmaps and other render hooks on the recorded environment too.
        client.world.setTimeOfDay(worldTime);
        client.world.setRainGradient(rain ? 1 : 0);
        client.world.setThunderGradient(thunder ? 1 : 0);
        Set<UUID> present=new HashSet<>();
        for(NbtElement element:frame.getList("entities",NbtElement.COMPOUND_TYPE)) {
            NbtCompound saved=(NbtCompound)element; UUID uuid=saved.getUuid("UUID"); present.add(uuid);
            Entity entity=GHOSTS.get(uuid);
            boolean fresh = entity == null;
            Vec3d pos=translated(saved);
            NbtCompound persistent = ReplayEntityState.persistentState(saved);
            boolean readNbt = fresh || discontinuity || !persistent.equals(PERSISTENT.get(uuid));
            NbtCompound nbt = null;
            if (readNbt) {
                nbt = saved.copy(); nbt.putUuid("UUID", ReplayEntityState.ghostUuid(uuid));
                NbtList coords=new NbtList(); coords.add(NbtDouble.of(pos.x)); coords.add(NbtDouble.of(pos.y)); coords.add(NbtDouble.of(pos.z)); nbt.put("Pos",coords);
            }
            if(entity==null) {
                if(saved.getBoolean("replayPlayer")) {
                    GameProfile profile=new GameProfile(uuid,saved.getString("replayName"));
                    for(NbtElement texture:saved.getList("replayTextures",NbtElement.COMPOUND_TYPE)) {
                        NbtCompound t=(NbtCompound)texture; profile.getProperties().put("textures",new Property("textures",t.getString("value"),t.contains("signature")?t.getString("signature"):null));
                    }
                    entity=new ReplayPlayer(client.world,profile);
                } else entity=EntityType.getEntityFromNbt(nbt,client.world).orElse(null);
                if(entity==null) continue;
                entity.setId(nextEntityId--); GHOSTS.put(uuid,entity); GHOST_IDENTITIES.add(entity);
            }
            double oldX=entity.getX(),oldY=entity.getY(),oldZ=entity.getZ(); float oldYaw=entity.getYaw(),oldPitch=entity.getPitch();
            float oldHead=entity instanceof LivingEntity l?l.headYaw:oldYaw,oldBody=entity instanceof LivingEntity l?l.bodyYaw:oldYaw;
            if (readNbt) { entity.readNbt(nbt); PERSISTENT.put(uuid, persistent); }
            byte[] tracked = saved.getByteArray("replayTracked");
            if (readNbt || !Arrays.equals(tracked, TRACKED.get(uuid))) {
                ReplayEntityState.apply(entity, saved); TRACKED.put(uuid, tracked);
            }
            entity.age = saved.getInt("replayAge");
            NbtList rotation = saved.getList("Rotation", NbtElement.FLOAT_TYPE);
            entity.refreshPositionAndAngles(pos.x, pos.y, pos.z, rotation.getFloat(0), rotation.getFloat(1));
            boolean snap = discontinuity || fresh || new Vec3d(oldX,oldY,oldZ).squaredDistanceTo(pos) > 256;
            entity.lastRenderX=snap?pos.x:oldX; entity.lastRenderY=snap?pos.y:oldY; entity.lastRenderZ=snap?pos.z:oldZ;
            entity.prevX=entity.lastRenderX; entity.prevY=entity.lastRenderY; entity.prevZ=entity.lastRenderZ;
            entity.prevYaw=snap?entity.getYaw():oldYaw; entity.prevPitch=snap?entity.getPitch():oldPitch;
            if(entity instanceof ReplayPlayer player) {
                player.dead=saved.getBoolean("replayDead");
                player.getInventory().selectedSlot = Math.clamp(saved.getInt("SelectedItemSlot"), 0, 8);
            }
            entity.setPose(EntityPose.valueOf(saved.getString("replayPose")));
            entity.setSneaking(saved.getBoolean("replaySneak")); entity.setSprinting(saved.getBoolean("replaySprint")); entity.setInvisible(saved.getBoolean("replayInvisible"));
            if(entity instanceof LivingEntity living) {
                living.hurtTime = saved.getShort("HurtTime"); living.deathTime = saved.getShort("DeathTime");
                living.prevHeadYaw=living.headYaw; living.headYaw=saved.getFloat("replayHead");
                living.prevBodyYaw=living.bodyYaw; living.bodyYaw=saved.getFloat("replayBody");
                living.lastHandSwingProgress=living.handSwingProgress; living.handSwingProgress=saved.getFloat("replaySwing");

                if(saved.getBoolean("replayUsing")) living.setCurrentHand(net.minecraft.util.Hand.valueOf(saved.getString("replayHand")));
                else living.clearActiveItem();
            }
            long interval = (long)frameIntervalNanos;
            Motion previous = MOTIONS.get(uuid);
            Vec3d previousRecorded = previous == null ? pos : previous.to;
            float distance = ReplayWalkAnimation.distance(pos.x - previousRecorded.x, pos.z - previousRecorded.z, snap);
            float phase = snap || previous == null ? 0 : previous.limbTo;
            float displayedPhase=snap||previous==null?phase:MathHelper.lerp((float)dev.mapselect.replay.ReplayMotionTiming.fraction(arrival,previous.started,previous.interval),previous.limbFrom,previous.limbTo);
            float walking = ReplayWalkAnimation.speed(distance, tick - previousTick);
            MOTIONS.put(uuid,new Motion(snap?pos:new Vec3d(oldX,oldY,oldZ),pos,oldYaw,entity.getYaw(),oldPitch,entity.getPitch(),arrival,interval,snap,displayedPhase,phase + distance * 4,walking,
                snap?(entity instanceof LivingEntity l?l.headYaw:entity.getYaw()):oldHead,(entity instanceof LivingEntity l?l.headYaw:entity.getYaw()),snap?(entity instanceof LivingEntity l?l.bodyYaw:entity.getYaw()):oldBody,(entity instanceof LivingEntity l?l.bodyYaw:entity.getYaw())));
            // Keep the displayed position until the render pass advances our interpolation.
            if (!snap && !paused) {
                entity.setPosition(oldX, oldY, oldZ);entity.setYaw(oldYaw);entity.setPitch(oldPitch);
                if(entity instanceof LivingEntity living){living.headYaw=oldHead;living.prevHeadYaw=oldHead;living.bodyYaw=oldBody;living.prevBodyYaw=oldBody;}
            }
            entity.setVelocity(Vec3d.ZERO);
        }
        for(UUID uuid:List.copyOf(GHOSTS.keySet())) if(!present.contains(uuid)) {
            Entity e=GHOSTS.remove(uuid); MOTIONS.remove(uuid); PERSISTENT.remove(uuid); TRACKED.remove(uuid); GHOST_IDENTITIES.remove(e);
        }
        for(NbtElement element:frame.getList("blocks",NbtElement.COMPOUND_TYPE)) {
            NbtCompound block=(NbtCompound)element;
            if (block.getBoolean("reset")) continue; // Native stage sends the restored block and its NBT through chunk updates.
            if(!block.contains("nbt")) continue;
            BlockPos local=BlockPos.fromLong(block.getLong("pos")).subtract(origin).add(BlockPos.ofFloored(offset));
            var be=client.world.getBlockEntity(local);
            if(be!=null) { NbtCompound nbt=block.getCompound("nbt").copy(); nbt.putInt("x",local.getX()); nbt.putInt("y",local.getY()); nbt.putInt("z",local.getZ()); be.read(nbt,client.world.getRegistryManager()); }
        }
        Entity target=pov==null?null:GHOSTS.get(pov);
        client.setCameraEntity(target==null?client.player:target);
        if(speed==1 && net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("voicechat")) ReplayVoicePlayback.play(frame.getList("voice",NbtElement.COMPOUND_TYPE));
        effects(frame.getList("effects",NbtElement.COMPOUND_TYPE));
        long applyMillis = (System.nanoTime() - applyStarted) / 1_000_000L;
        if (applyMillis >= 25) MapSelect.LOGGER.warn("Replay client: applying tick {} took {} ms ({} entities)", tick, applyMillis, GHOSTS.size());
    }
    public static void prepareRender() {
        if(!active) return;
        MinecraftClient replayClient = MinecraftClient.getInstance();
        if (replayClient.world != ghostWorld) return;
        // Capture once so every terrain layer and section sees exactly the same scenery phase.
        sceneryClock.beginFrame(System.nanoTime(), paused || buffering);
        // Apply before GameRenderer updates the lightmap, not just around WorldRenderer.render.
        replayClient.world.setTimeOfDay(worldTime);
        replayClient.world.setRainGradient(rain ? 1 : 0); replayClient.world.setThunderGradient(thunder ? 1 : 0);
        restoreMapEffect(dev.doctor4t.wathe.cca.GameWorldComponent.KEY.get(replayClient.world));
        long now=System.nanoTime();
        MOTIONS.forEach((uuid,m)->{
            Entity entity=GHOSTS.get(uuid); if(entity==null) return;
            if(buffering)return;
            double fraction=m.jump || paused?1:dev.mapselect.replay.ReplayMotionTiming.fraction(now,m.started,m.interval);
            Vec3d pos=m.from.lerp(m.to,fraction);
            entity.setPosition(pos); entity.lastRenderX=pos.x; entity.lastRenderY=pos.y; entity.lastRenderZ=pos.z;
            float yaw=MathHelper.lerpAngleDegrees((float)fraction,m.fromYaw,m.toYaw),pitch=MathHelper.lerp((float)fraction,m.fromPitch,m.toPitch);
            entity.setYaw(yaw); entity.prevYaw=yaw; entity.setPitch(pitch); entity.prevPitch=pitch;
            if (entity instanceof LivingEntity living) {
                living.headYaw=MathHelper.lerpAngleDegrees((float)fraction,m.fromHead,m.toHead);living.prevHeadYaw=living.headYaw;
                living.bodyYaw=MathHelper.lerpAngleDegrees((float)fraction,m.fromBody,m.toBody);living.prevBodyYaw=living.bodyYaw;
                float renderDelta = MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(false);
                var animator = (dev.mapselect.mixin.client.ReplayLimbAnimatorAccessor)living.limbAnimator;
                animator.gexpress$speed(m.walking); animator.gexpress$previousSpeed(m.walking);
                animator.gexpress$phase(MathHelper.lerp((float)fraction, m.limbFrom, m.limbTo) + m.walking * (1 - renderDelta));
            }
        });
    }
    private static void renderGhosts(WorldRenderContext context) {
        if (!active || context.world() != ghostWorld || context.matrixStack() == null || context.consumers() == null) return;
        MinecraftClient client = MinecraftClient.getInstance();
        Vec3d cameraPos = context.camera().getPos();
        float delta = context.tickCounter().getTickDelta(false);
        var dispatcher = client.getEntityRenderDispatcher();
        // Wathe disables terrain frustum culling for its moving exterior; actors still need normal culling.
        var actors = new net.minecraft.client.render.Frustum(context.positionMatrix(), context.projectionMatrix());
        actors.setPosition(cameraPos.x, cameraPos.y, cameraPos.z);
        for (Entity entity : GHOSTS.values()) {
            if (entity instanceof ReplayPlayer player && player.dead) continue;
            if (entity == client.getCameraEntity() && client.options.getPerspective().isFirstPerson()) continue;
            if (!dispatcher.shouldRender(entity, actors, cameraPos.x, cameraPos.y, cameraPos.z)) continue;
            dispatcher.render(entity, entity.getX()-cameraPos.x, entity.getY()-cameraPos.y, entity.getZ()-cameraPos.z,
                entity.getYaw(), delta, context.matrixStack(), context.consumers(), dispatcher.getLight(entity, delta));
        }
    }
    private static void effects(NbtList events) {
        var world=MinecraftClient.getInstance().world; if(world==null) return;
        for(NbtElement element:events) {
            NbtCompound e=(NbtCompound)element;
            Vec3d pos=new Vec3d(e.getDouble("x")-origin.getX()+offset.x,e.getDouble("y")-origin.getY()+offset.y,e.getDouble("z")-origin.getZ()+offset.z);
            if(e.getString("kind").equals("sound")) {
                world.playSound(pos.x,pos.y,pos.z,net.minecraft.sound.SoundEvent.of(net.minecraft.util.Identifier.of(e.getString("sound"))),
                    net.minecraft.sound.SoundCategory.valueOf(e.getString("category")),e.getFloat("volume"),e.getFloat("pitch"),false);
            } else net.minecraft.particle.ParticleTypes.TYPE_CODEC.parse(NbtOps.INSTANCE,e.get("particle")).result().ifPresent(particle->{
                int count=Math.clamp(e.getInt("count"),0,512); var random=world.random;
                if(count==0) world.addParticle(particle,pos.x,pos.y,pos.z,e.getDouble("dx")*e.getDouble("speed"),e.getDouble("dy")*e.getDouble("speed"),e.getDouble("dz")*e.getDouble("speed"));
                for(int i=0;i<count;i++) world.addParticle(particle,pos.x+random.nextGaussian()*e.getDouble("dx"),pos.y+random.nextGaussian()*e.getDouble("dy"),pos.z+random.nextGaussian()*e.getDouble("dz"),random.nextGaussian()*e.getDouble("speed"),random.nextGaussian()*e.getDouble("speed"),random.nextGaussian()*e.getDouble("speed"));
            });
        }
    }
    public static boolean isGhost(Entity entity) { return entity!=null && GHOST_IDENTITIES.contains(entity); }
    public static void cyclePov() {
        List<UUID> players=GHOSTS.entrySet().stream().filter(e->e.getValue() instanceof ReplayPlayer).map(Map.Entry::getKey).toList();
        if(players.isEmpty()) return;
        pov=players.get((players.indexOf(pov)+1)%players.size());
        control("camera", pov.toString());
        MinecraftClient.getInstance().setCameraEntity(GHOSTS.get(pov));
    }
    public static void freeCamera() {
        MinecraftClient client=MinecraftClient.getInstance();
        Entity focused=client.getCameraEntity();
        if (focused!=null && focused!=client.player && client.player!=null) {
            Vec3d position=focused.getPos().subtract(focused.getRotationVec(1).multiply(2));
            client.player.refreshPositionAndAngles(position.x,position.y,position.z,focused.getYaw(),focused.getPitch());
            client.player.setVelocity(Vec3d.ZERO);
        }
        pov=null; control("camera", ""); client.setCameraEntity(client.player);
    }
    public static String cameraName() {
        Entity entity=pov==null?null:GHOSTS.get(pov);
        return entity instanceof ReplayPlayer player ? player.getGameProfile().getName()+(player.dead?" (spectator)":" (first person)"):"Free camera";
    }
    public static void clear() {
        MinecraftClient client=MinecraftClient.getInstance();
        if(active) { client.setCameraEntity(client.player); if(previousPerspective!=null) client.options.setPerspective(previousPerspective); }
        GHOSTS.clear(); GHOST_IDENTITIES.clear(); MOTIONS.clear(); PERSISTENT.clear(); TRACKED.clear(); ghostWorld=null; lastFrameArrival=0; active=false; hasTrain=false; loading=false; buffering=false; pov=null; epoch=-1; previousPerspective=null;
        resetVoice();
        sceneryClock.reset(); applyingTrainSnapshot = false; recordedMapEffect = null;
        awaitingTerrain = false; terrainReadyTicks = terrainWaitTicks = 0;
        if(client.currentScreen instanceof ReplayControlsScreen) client.setScreen(null);
    }
    private static void resetVoice() { if(net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("voicechat")) ReplayVoicePlayback.reset(); }
    private static final class ReplayPlayer extends OtherClientPlayerEntity {
        boolean dead;
        final java.util.function.Supplier<net.minecraft.client.util.SkinTextures> skin;
        ReplayPlayer(ClientWorld world,GameProfile profile) { super(world,profile); skin=MinecraftClient.getInstance().getSkinProvider().getSkinTexturesSupplier(profile); }
        @Override public net.minecraft.client.util.SkinTextures getSkinTextures() { return skin.get(); }
        @Override public boolean isSpectator() { return dead; }
    }
    private record Motion(Vec3d from,Vec3d to,float fromYaw,float toYaw,float fromPitch,float toPitch,long started,long interval,boolean jump,float limbFrom,float limbTo,float walking,float fromHead,float toHead,float fromBody,float toBody) {}
}
