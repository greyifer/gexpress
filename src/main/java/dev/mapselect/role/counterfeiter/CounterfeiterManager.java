package dev.mapselect.role.counterfeiter;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.mapselect.game.*;
import dev.mapselect.network.PayloadSync;
import dev.mapselect.network.role.counterfeiter.*;
import dev.mapselect.registry.MapSelectRoles;
import dev.mapselect.role.pelican.PelicanManager;
import dev.mapselect.testing.GexpressTestState;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import java.util.*;

public final class CounterfeiterManager {
    private static final Map<ServerWorld, ForgeAllowance> ROUNDS = new HashMap<>();
    private CounterfeiterManager() {}
    public static void register() {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity,world)->{
            if(dev.mapselect.item.CounterfeitTrapItem.placing() && entity.getClass().getName().equals("org.agmas.noellesroles.entities.RoleMineEntity"))entity.addCommandTag(dev.mapselect.item.CounterfeitTrapItem.TAG);
        });
        PayloadTypeRegistry.playC2S().register(CounterfeiterForgePayload.ID, CounterfeiterForgePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(CounterfeiterStatePayload.ID, CounterfeiterStatePayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(CounterfeiterForgePayload.ID,
            (payload, context) -> context.server().execute(() -> forge(context.player(), payload.choice())));
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            if (world.getTime() % 10 != 0) return;
            for (var player : world.getPlayers()) sync(player);
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> sync(handler.player)));
        RoundLifecycleHooks.clearWorld(world -> {
            if (world instanceof ServerWorld serverWorld) {
                ROUNDS.remove(serverWorld);
                for (var player : serverWorld.getPlayers()) sync(player);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> ROUNDS.keySet().removeIf(world -> world.getServer() == server));
    }
    private static boolean active(ServerPlayerEntity player) {
        var game = GameWorldComponent.KEY.getNullable(player.getWorld());
        var role = game == null ? null : game.getRole(player);
        return role != null && MapSelectRoles.COUNTERFEITER_ID.equals(role.identifier())
            && (game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE || GexpressTestState.isRoleTester(player))
            && (DeadPlayerStatus.isLivingRoundParticipant(player) || GexpressTestState.isRoleTester(player))
            && !PelicanManager.isStashed(player) && !KinsWatheSafePreparation.isActive(player.getWorld());
    }
    private static void forge(ServerPlayerEntity player, int index) {
        if (!active(player) || index < 0 || index >= CounterfeiterItems.CHOICES.size()) return;
        var choice = CounterfeiterItems.CHOICES.get(index);
        if (!choice.available()) return;
        ForgeAllowance allowance = ROUNDS.computeIfAbsent(player.getServerWorld(), ignored -> new ForgeAllowance());
        boolean forged = allowance.forge(player.getUuid(), index, () -> dev.doctor4t.wathe.util.ShopEntry.insertStackInFreeSlot(player, choice.forgery()));
        if (forged) {
            player.getInventory().markDirty(); player.currentScreenHandler.sendContentUpdates();
            player.sendMessage(Text.translatable("gexpress.counterfeiter.forged", choice.name()), true);
        } else if (!allowance.spent(player.getUuid())) {
            player.sendMessage(Text.translatable("gexpress.counterfeiter.full"), true);
        }
        sync(player);
    }
    private static void sync(ServerPlayerEntity player) {
        ForgeAllowance allowance = ROUNDS.get(player.getServerWorld());
        PayloadSync.sendIfChanged(player, CounterfeiterStatePayload.ID,
            new CounterfeiterStatePayload(active(player), allowance != null && allowance.spent(player.getUuid()), CounterfeiterItems.availableMask(), allowance==null?-1:allowance.selected(player.getUuid())));
    }
}
