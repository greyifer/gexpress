package dev.mapselect.client.role.counterfeiter;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.mapselect.client.game.ClientRoleRevealState;
import dev.mapselect.network.role.counterfeiter.*;
import dev.mapselect.registry.MapSelectRoles;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.minecraft.client.MinecraftClient;

public final class ClientCounterfeiterState {
    private static CounterfeiterStatePayload state = new CounterfeiterStatePayload(false, false, 0,-1);
    private static Object world;
    private static long lastRequest;
    private ClientCounterfeiterState() {}
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(CounterfeiterStatePayload.ID, (payload, context) ->
            context.client().execute(() -> { state = payload; world = context.client().world; }));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            state = new CounterfeiterStatePayload(false, false, 0,-1); world = null; lastRequest = 0;
        });
    }
    public static boolean visible(MinecraftClient client) {
        if (!state.active() || client.player == null || client.world == null || world != client.world
            || !ClientRoleRevealState.canUseRoleAbility(client)) return false;
        var game = GameWorldComponent.KEY.getNullable(client.world);
        var role = game == null ? null : game.getRole(client.player);
        return role != null && MapSelectRoles.COUNTERFEITER_ID.equals(role.identifier());
    }
    public static int selected(){return state.selected();}
    public static boolean spent() { return state.spent(); }
    public static boolean available(int index) { return (state.choices() & (1 << index)) != 0; }
    public static void forge(int index) {
        long now = System.nanoTime();
        if (!visible(MinecraftClient.getInstance()) || spent() || !available(index)
            || now - lastRequest < 250_000_000L || !ClientPlayNetworking.canSend(CounterfeiterForgePayload.ID)) return;
        lastRequest = now;
        ClientPlayNetworking.send(new CounterfeiterForgePayload(index));
    }
}
