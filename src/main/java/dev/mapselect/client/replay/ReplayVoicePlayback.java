package dev.mapselect.client.replay;

import de.maxhenkel.voicechat.api.VoicechatClientApi;
import de.maxhenkel.voicechat.api.audiochannel.ClientLocationalAudioChannel;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.nbt.*;
import net.minecraft.util.math.Vec3d;
import java.util.*;
import java.util.concurrent.*;

/** Ordered, bounded voice decoding keeps codec work off the render thread. */
public final class ReplayVoicePlayback {
    private static volatile VoicechatClientApi api;
    private static volatile int generation;
    private static final Map<UUID, Stream> STREAMS = new HashMap<>();
    private static final ThreadPoolExecutor AUDIO = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(8), r -> { Thread t = new Thread(r, "Gexpress replay voice"); t.setDaemon(true); return t; });
    private ReplayVoicePlayback() {}
    public static void initialize(VoicechatClientApi clientApi) { reset(); api = clientApi; }
    private record Packet(UUID speaker, byte[] opus, Vec3d position, float distance) {}
    public static void play(NbtList packets) {
        VoicechatClientApi voice = api;
        if (voice == null || voice.isDisabled() || packets.isEmpty()) return;
        Vec3d listener = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
        List<Packet> batch = new ArrayList<>();
        float distance = (float)voice.getVoiceChatDistance();
        for (NbtElement element : packets) {
            NbtCompound packet = (NbtCompound)element;
            Vec3d position = ClientReplay.voicePosition(packet.getUuid("speaker"));
            // Older archives lack a distance field and use the configured proximity radius.
            float radius = packet.contains("distance") ? packet.getFloat("distance") : distance;
            if (position == null || radius <= 0 || position.squaredDistanceTo(listener) > radius * radius) continue;
            batch.add(new Packet(packet.getUuid("speaker"), packet.getByteArray("opus"), position, radius));
        }
        int expected = generation;
        try { AUDIO.execute(() -> {
            if (expected != generation) return;
            synchronized (STREAMS) {
                if (expected != generation) return;
                long now = System.nanoTime();
                for (Packet packet : batch) {
                    Stream stream = STREAMS.computeIfAbsent(packet.speaker, id -> new Stream(voice.createDecoder(),
                        voice.createLocationalAudioChannel(UUID.randomUUID(), voice.createPosition(packet.position.x, packet.position.y, packet.position.z))));
                    stream.channel.setLocation(voice.createPosition(packet.position.x, packet.position.y, packet.position.z));
                    stream.channel.setDistance(packet.distance);
                    if (now - stream.lastPlayed > 250_000_000L) stream.decoder.resetState();
                    try { stream.channel.play(stream.decoder.decode(packet.opus)); stream.lastPlayed = now; }
                    catch (RuntimeException ignored) { stream.decoder.resetState(); }
                }
                STREAMS.values().removeIf(stream -> {
                    if (now - stream.lastPlayed < 5_000_000_000L) return false;
                    stream.channel.play(new short[0]); stream.decoder.close(); return true;
                });
            }
        }); } catch (RejectedExecutionException ignored) {
            // Drop queued stale speech instead of building a delayed audio backlog.
            reset();
        }
    }
    public static void reset() {
        generation++; AUDIO.getQueue().clear();
        synchronized (STREAMS) {
            STREAMS.values().forEach(s -> { s.channel.play(new short[0]); s.decoder.close(); }); STREAMS.clear();
        }
    }
    private static final class Stream {
        final OpusDecoder decoder; final ClientLocationalAudioChannel channel; long lastPlayed;
        Stream(OpusDecoder decoder, ClientLocationalAudioChannel channel) { this.decoder = decoder; this.channel = channel; }
    }
}
