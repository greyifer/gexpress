package dev.mapselect.testing;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheRoles;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheItems;
import dev.mapselect.command.admin.TestCommand;
import dev.mapselect.game.GexpressGameModes;
import dev.mapselect.permissions.GexpressPermissions;
import dev.mapselect.role.mafia.MafiaManager;
import dev.mapselect.role.medic.MedicShieldComponent;
import dev.mapselect.role.medic.MedicShieldManager;
import dev.mapselect.role.pelican.PelicanManager;
import dev.mapselect.role.twins.TwinsManager;
import dev.mapselect.role.vengeful.VengefulSpiritManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.EntityPose;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.SetCameraEntityS2CPacket;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import net.minecraft.nbt.NbtCompound;

import java.util.*;

/** Optional Carpet integration through its command dispatcher; no required Carpet dependency. */
public final class BotTestTools {
    private static final List<String> SCENARIOS = List.of("juggernaut_shield", "juggernaut_last_round_shield", "vengeful_revenge", "godfather_families", "twins", "pelican");
    private static final Map<ServerWorld, Session> SESSIONS = new HashMap<>();
    private static final Map<String, Long> CANCELLED_SPAWNS = new HashMap<>();
    private static final Map<UUID, PendingRestore> RESTORES = new HashMap<>();
    private static boolean registered;

    private BotTestTools() {}

    public static LiteralArgumentBuilder<ServerCommandSource> setupBranch() {
        if (!registered) {
            registered = true;
            ServerTickEvents.END_WORLD_TICK.register(BotTestTools::tick);
            net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
                var p = handler.player;
                Long until = CANCELLED_SPAWNS.remove(p.getGameProfile().getName());
                if (until != null && System.currentTimeMillis() < until && isCarpetBot(p)) p.networkHandler.disconnect(Text.literal("Cancelled test setup"));
                PendingRestore restore = RESTORES.remove(p.getUuid());
                if (restore != null) restore.snapshot.restore(p, restore.world);
            });
            ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
                for (Session s : List.copyOf(SESSIONS.values())) reset(s.source);
            });
            ServerLifecycleEvents.SERVER_STOPPED.register(server -> { SESSIONS.clear(); CANCELLED_SPAWNS.clear(); RESTORES.clear(); });
        }
        return CommandManager.literal("setup").requires(GexpressPermissions::canUseAdminCommands)
            .then(CommandManager.argument("scenario", StringArgumentType.word())
                .suggests((ctx, b) -> { SCENARIOS.stream().filter(s -> s.startsWith(b.getRemainingLowerCase())).forEach(b::suggest); return b.buildFuture(); })
                .executes(ctx -> setup(ctx.getSource(), StringArgumentType.getString(ctx, "scenario"))));
    }

    public static LiteralArgumentBuilder<ServerCommandSource> actionBranch() {
        var actions = CommandManager.argument("bot", EntityArgumentType.player());
        for (String action : List.of("shoot", "stab", "crouch", "stand", "stop", "ability", "look")) {
            actions.then(CommandManager.literal(action)
                .executes(ctx -> action(ctx, action, null))
                .then(CommandManager.argument("target", EntityArgumentType.player())
                    .executes(ctx -> action(ctx, action, EntityArgumentType.getPlayer(ctx, "target")))));
        }
        return CommandManager.literal("action").requires(GexpressPermissions::canUseAdminCommands).then(actions);
    }

    public static LiteralArgumentBuilder<ServerCommandSource> reviveBranch() {
        return CommandManager.literal("revive").requires(GexpressPermissions::canUseAdminCommands)
            .then(CommandManager.argument("player", EntityArgumentType.player()).executes(ctx -> {
                var src = ctx.getSource();
                if (!testWorld(src)) return 0;
                var player = EntityArgumentType.getPlayer(ctx, "player");
                if (player.getWorld() != src.getWorld()) return error(src, "Player must be in this test world.");
                if (GameFunctions.isPlayerAliveAndSurvival(player)) return error(src, "That player is already alive.");
                revive(player);
                src.sendFeedback(() -> Text.literal("Revived " + player.getName().getString() + "."), false);
                return 1;
            }));
    }

    public static LiteralArgumentBuilder<ServerCommandSource> stateBranch() {
        return CommandManager.literal("state").requires(GexpressPermissions::canUseAdminCommands)
            .then(CommandManager.argument("player", EntityArgumentType.player()).executes(ctx -> {
                var p = EntityArgumentType.getPlayer(ctx, "player");
                var game = GameWorldComponent.KEY.get(p.getWorld());
                Role role = game.getRole(p);
                var shield = MedicShieldComponent.KEY.get(p.getWorld());
                String details = p.getName().getString() + ": role=" + (role == null ? "none" : role.identifier())
                    + ", alive=" + GameFunctions.isPlayerAliveAndSurvival(p) + ", family=" + MafiaManager.familyRoot(p.getUuid())
                    + ", shield=" + shield.hasShield(p.getUuid()) + ", swallowed=" + PelicanManager.isStashed(p)
                    + ", last-round shield=" + dev.mapselect.game.LastDeathShieldManager.hasShield(p.getUuid())
                    + "\nGun cooldown=" + p.getItemCooldownManager().getCooldownProgress(WatheItems.REVOLVER, 0)
                    + ", knife cooldown=" + p.getItemCooldownManager().getCooldownProgress(WatheItems.KNIFE, 0)
                    + ", medic cooldown=" + shield.cooldownRemainingTicks(p.getUuid()) + " ticks"
                    + "\nAbilities: " + dev.mapselect.network.ability.AbilityCooldownSync.describe(p)
                    + "\nVengeful: " + VengefulSpiritManager.describeState(p);
                ctx.getSource().sendFeedback(() -> Text.literal(details), false);
                return 1;
            }));
    }

    private static int action(CommandContext<ServerCommandSource> ctx, String action, ServerPlayerEntity target) throws CommandSyntaxException {
        var src = ctx.getSource();
        if (!testWorld(src)) return 0;
        var bot = EntityArgumentType.getPlayer(ctx, "bot");
        if (!isCarpetBot(bot)) return error(src, "Choose a Carpet fake player, not a real player or Twin body.");
        if (bot.getWorld() != src.getWorld() || target != null && target.getWorld() != src.getWorld()) return error(src, "Both players must be in this test world.");
        if (target == bot) return error(src, "A bot cannot target itself.");
        if (List.of("ability", "shoot", "stab").contains(action)) {
            var guard = dev.mapselect.game.GexpressAbilityGuards.checkItemAbility(bot);
            if (!guard.allowed()) return error(src, guard.message());
            if (PelicanManager.isStashed(bot)) return error(src, "A swallowed bot cannot use abilities or weapons.");
            if (dev.mapselect.role.timemaster.TimeMasterManager.isFrozen(bot)) return error(src, "The bot is frozen.");
        }
        if (target != null && !look(src, bot, target)) return 0;
        if (action.equals("look")) return target == null ? error(src, "Choose a target to look at.") : 1;
        if (action.equals("ability")) {
            Role role = GameWorldComponent.KEY.get(bot.getWorld()).getRole(bot);
            if (role == null) return error(src, "The bot has no role.");
            if (!role.identifier().getNamespace().equals("gexpress")) return error(src, "This command does not support addon or vanilla role abilities.");
            switch (role.identifier().getPath()) {
                case "medic" -> MedicShieldManager.tryShield(bot);
                case "pelican" -> PelicanManager.tryEat(bot);
                case "twins" -> TwinsManager.trySwap(bot);
                case "juggernaut" -> dev.mapselect.role.juggernaut.JuggernautManager.tryBreakDoor(bot);
                case "tracker" -> dev.mapselect.role.tracker.TrackerManager.tryTrack(bot);
                default -> { return error(src, "No bot ability handler for " + role.identifier() + ". Supported: medic, pelican, twins, juggernaut, tracker."); }
            }
            src.sendFeedback(() -> Text.literal("Requested " + role.identifier() + " ability. Normal range and cooldown rules still apply; use /g test state to check the result."), false);
            return 1;
        }
        if (action.equals("shoot") || action.equals("stab")) {
            if (!GameFunctions.isPlayerAliveAndSurvival(bot)) return error(src, "The bot must be alive to use a weapon.");
            Item weapon = action.equals("shoot") ? WatheItems.REVOLVER : WatheItems.KNIFE;
            int slot = -1;
            for (int i = 0; i < 9; i++) if (bot.getInventory().getStack(i).isOf(weapon)) { slot = i; break; }
            if (slot < 0) return error(src, "Put a " + (action.equals("shoot") ? "loaded gun" : "knife") + " in the bot's hotbar first.");
            if (!carpet(src, bot, "hotbar " + (slot + 1))) return 0;
            if (bot.getItemCooldownManager().isCoolingDown(weapon)) return error(src, "That weapon is still on cooldown.");
            var collision = dev.mapselect.role.twins.TwinWeaponTargeting.findTarget(bot, action.equals("shoot") ? 15 : 3);
            int hit = collision instanceof net.minecraft.util.hit.EntityHitResult entityHit ? entityHit.getEntity().getId() : -1;
            if (target != null && hit != target.getId()) return error(src, "The target is out of range or the bot's aim is blocked.");
            if (action.equals("stab") && hit < 0) return error(src, "No player in knife range (3 blocks).");
            var context = new net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.Context() {
                public net.minecraft.server.MinecraftServer server() { return src.getServer(); }
                public ServerPlayerEntity player() { return bot; }
                public net.fabricmc.fabric.api.networking.v1.PacketSender responseSender() {
                    return net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.getSender(bot);
                }
            };
            if (action.equals("shoot")) new dev.doctor4t.wathe.util.GunShootPayload.Receiver().receive(new dev.doctor4t.wathe.util.GunShootPayload(hit), context);
            else new dev.doctor4t.wathe.util.KnifeStabPayload.Receiver().receive(new dev.doctor4t.wathe.util.KnifeStabPayload(hit), context);
            src.sendFeedback(() -> Text.literal("Requested " + action + " from " + bot.getName().getString() + "."), false);
            return 1;
        }
        return carpet(src, bot, switch (action) { case "crouch" -> "sneak"; case "stand" -> "unsneak"; default -> "stop"; }) ? 1 : 0;
    }

    private static boolean look(ServerCommandSource src, ServerPlayerEntity bot, ServerPlayerEntity target) throws CommandSyntaxException {
        Vec3d eyes = target.getEyePos();
        return carpet(src, bot, "look at " + eyes.x + " " + eyes.y + " " + eyes.z);
    }

    private static boolean carpet(ServerCommandSource src, ServerPlayerEntity bot, String action) throws CommandSyntaxException {
        return execute(src, "player " + bot.getGameProfile().getName() + " " + action) > 0;
    }

    private static int execute(ServerCommandSource src, String command) throws CommandSyntaxException {
        return src.getServer().getCommandManager().getDispatcher().execute(command, src);
    }

    static boolean isCarpetBot(ServerPlayerEntity player) {
        for (Class<?> type = player.getClass(); type != null; type = type.getSuperclass())
            if (type.getName().equals("carpet.patches.EntityPlayerMPFake")) return true;
        return false;
    }

    private static boolean testWorld(ServerCommandSource src) {
        var game = GameWorldComponent.KEY.get(src.getWorld());
        if (game.getGameStatus() != GameWorldComponent.GameStatus.ACTIVE || !GexpressGameModes.isTest(game)) {
            error(src, "Use /g test setup <scenario>, or start a test match first. These tools only run in test matches.");
            return false;
        }
        return true;
    }

    private static int setup(ServerCommandSource src, String scenario) throws CommandSyntaxException {
        if (!SCENARIOS.contains(scenario)) return error(src, "Unknown scenario. Choose " + String.join(", ", SCENARIOS) + ".");
        var owner = src.getPlayerOrThrow();
        var world = src.getWorld();
        if (dev.mapselect.game.GexpressWorlds.hasOtherActiveGameWorld(world)) return error(src, "Finish the match in the other game world before starting a bot preset.");
        if (SESSIONS.containsKey(world) || GameWorldComponent.KEY.get(world).getGameStatus() != GameWorldComponent.GameStatus.INACTIVE) return error(src, "Reset the current test or end the match first.");
        if (world.getPlayers().size() != 1) return error(src, "Setup needs a world with only you in it. Existing bots can use the other /g test commands.");
        if (src.getServer().getCommandManager().getDispatcher().getRoot().getChild("player") == null) return error(src, "Carpet's /player command is unavailable. Install Carpet and enable its player command.");
        for (String name : List.of("GE_Test1", "GE_Test2", "GE_Test3"))
            if (src.getServer().getPlayerManager().getPlayer(name) != null) return error(src, name + " is already online. Setup will not take over an existing player.");
            else if (CANCELLED_SPAWNS.getOrDefault(name, 0L) > System.currentTimeMillis()) return error(src, "A cancelled Carpet spawn is still pending. Wait up to a minute before retrying setup.");
        Session session = new Session(src, scenario, owner);
        SESSIONS.put(world, session);
        try {
            for (String name : session.names) {
                if (execute(src, "player " + name + " spawn") <= 0) {
                    reset(src);
                    return error(src, "Carpet could not spawn " + name + ". Check /carpet allowSpawningOfflinePlayers, then retry.");
                }
            }
        } catch (CommandSyntaxException ex) { reset(src); throw ex; }
        src.sendFeedback(() -> Text.literal("Spawning test bots for " + scenario + ". Setup will finish when they join."), false);
        return 1;
    }

    private static void tick(ServerWorld world) {
        CANCELLED_SPAWNS.values().removeIf(until -> until < System.currentTimeMillis());
        Session s = SESSIONS.get(world);
        if (s == null) return;
        try {
            var owner = world.getServer().getPlayerManager().getPlayer(s.owner.id);
            if (owner == null || owner.getWorld() != world) { reset(s.source); return; }
            if (s.ready) {
                if (GameWorldComponent.KEY.get(world).getGameStatus() == GameWorldComponent.GameStatus.INACTIVE) reset(s.source);
                return;
            }
            if (--s.timeout <= 0) { error(s.source, "Test setup timed out. Restoring your state."); reset(s.source); return; }
            List<ServerPlayerEntity> bots = s.names.stream().map(world.getServer().getPlayerManager()::getPlayer).filter(Objects::nonNull).toList();
            if (bots.size() != s.names.size()) return;
            if (bots.stream().anyMatch(p -> !isCarpetBot(p) || p.getWorld() != world)) {
                error(s.source, "Test bot name was taken by another player."); reset(s.source); return;
            }
            if (!s.started) {
                if (world.getPlayers().size() != 4) { error(s.source, "Another player joined during setup."); reset(s.source); return; }
                var ready = dev.doctor4t.wathe.cca.MapVariablesWorldComponent.KEY.get(world).getReadyArea();
                if (!ready.contains(owner.getPos())) { error(s.source, "Stand in the map's ready area before using setup."); reset(s.source); return; }
                for (var bot : bots) bot.teleport(world, owner.getX(), owner.getY(), owner.getZ(), 0, 0);
                if (execute(s.source, "g game start test generic") <= 0) { reset(s.source); return; }
                s.started = GameWorldComponent.KEY.get(world).getGameStatus() != GameWorldComponent.GameStatus.INACTIVE;
                if (!s.started) { error(s.source, "The test match did not start. Check the ready area and map setup."); reset(s.source); return; }
            }
            var game = GameWorldComponent.KEY.get(world);
            if (game.getGameStatus() != GameWorldComponent.GameStatus.ACTIVE) return;
            owner.changeGameMode(GameMode.SPECTATOR);
            TestCommand.setRole(s.source, WatheRoles.CIVILIAN, List.of(owner));
            List<String> roles = switch (s.scenario) {
                case "juggernaut_shield", "juggernaut_last_round_shield" -> List.of("juggernaut", "medic", "killer");
                case "vengeful_revenge" -> List.of("vengeful_spirit", "killer", "civilian");
                case "godfather_families" -> List.of("godfather", "godfather", "civilian");
                case "twins" -> List.of("twins", "killer", "civilian");
                default -> List.of("pelican", "civilian", "killer");
            };
            Vec3d anchor = bots.getFirst().getPos();
            for (int i = 0; i < bots.size(); i++) {
                var bot = bots.get(i);
                bot.changeGameMode(GameMode.ADVENTURE);
                stageBot(bot, anchor, i);
                var modifiers = WorldModifierComponent.KEY.get(world);
                var assigned = new ArrayList<>(modifiers.getModifiers(bot.getUuid()));
                modifiers.getModifiers(bot.getUuid()).clear();
                for (var modifier : assigned) org.agmas.harpymodloader.events.ModifierRemoved.EVENT.invoker().removeModifier(bot, modifier);
                modifiers.sync();
                Role role = resolve(roles.get(i));
                if (role == null) throw new IllegalStateException("Missing role " + roles.get(i));
                bot.getInventory().clear();
                TestCommand.setRole(s.source, role, List.of(bot));
                if (roles.get(i).equals("killer")) {
                    bot.getInventory().setStack(0, WatheItems.REVOLVER.getDefaultStack());
                    bot.getInventory().setStack(1, WatheItems.KNIFE.getDefaultStack());
                }
                carpet(s.source, bot, "stop");
            }
            if (s.scenario.equals("juggernaut_shield")) MedicShieldComponent.KEY.get(world).assignShield(bots.get(1).getUuid(), bots.get(0).getUuid());
            if (s.scenario.equals("juggernaut_last_round_shield")) dev.mapselect.game.LastDeathShieldManager.applyTestShield(bots.getFirst());
            s.ready = true;
            s.source.sendFeedback(() -> Text.literal("Ready: GE_Test1=" + roles.get(0) + ", GE_Test2=" + roles.get(1) + ", GE_Test3=" + roles.get(2)
                + ". Use /g test action, /g test state and /g test revive. /g test reset restores your state."), false);
        } catch (Exception ex) {
            dev.mapselect.MapSelect.LOGGER.warn("Bot test setup failed", ex);
            error(s.source, "Test setup failed: " + ex.getMessage());
            reset(s.source);
        }
    }

    private static Role resolve(String path) {
        if (path.equals("civilian")) return WatheRoles.CIVILIAN;
        if (path.equals("killer")) return WatheRoles.KILLER;
        return WatheRoles.ROLES.stream().filter(r -> r.identifier().equals(net.minecraft.util.Identifier.of("gexpress", path))).findFirst().orElse(null);
    }

    private static void stageBot(ServerPlayerEntity bot, Vec3d anchor, int index) {
        if (index == 0) return;
        var world = bot.getServerWorld();
        var play = dev.doctor4t.wathe.cca.MapVariablesWorldComponent.KEY.get(world).getPlayArea();
        for (int distance : new int[]{2, 4, 6}) {
            for (Vec3d offset : List.of(new Vec3d(distance, 0, 0), new Vec3d(-distance, 0, 0), new Vec3d(0, 0, distance), new Vec3d(0, 0, -distance))) {
                Vec3d pos = anchor.add(offset);
                var floor = net.minecraft.util.math.BlockPos.ofFloored(pos).down();
                var box = bot.getBoundingBox().offset(pos.subtract(bot.getPos()));
                if (!play.contains(pos) || !world.isSpaceEmpty(bot, box)
                        || !world.getBlockState(floor).isSideSolidFullSquare(world, floor, net.minecraft.util.math.Direction.UP)
                        || world.getPlayers().stream().anyMatch(p -> p != bot && !p.isSpectator() && p.getBoundingBox().intersects(box))) continue;
                bot.teleport(world, pos.x, pos.y, pos.z, 0, 0);
                return;
            }
        }
    }

    public static boolean reset(ServerCommandSource src) {
        var world = src.getWorld();
        Session s = SESSIONS.remove(world);
        if (s == null) {
            var game = GameWorldComponent.KEY.get(world);
            if (!GexpressGameModes.isTest(game) || game.getGameStatus() == GameWorldComponent.GameStatus.INACTIVE) return false;
            GameFunctions.finalizeGame(world);
            for (var player : world.getPlayers()) { GexpressTestState.unmarkRole(player.getUuid()); GexpressTestState.clearModifiers(player.getUuid()); }
            src.sendFeedback(() -> Text.literal("Ended the test match and reset players to the lobby."), false);
            return true;
        }
        if (s.started && GexpressGameModes.isTest(GameWorldComponent.KEY.get(world))
                && GameWorldComponent.KEY.get(world).getGameStatus() != GameWorldComponent.GameStatus.INACTIVE) GameFunctions.finalizeGame(world);
        for (String name : s.names) {
            var bot = src.getServer().getPlayerManager().getPlayer(name);
            if (bot == null) CANCELLED_SPAWNS.put(name, System.currentTimeMillis() + 60_000);
            if (bot != null && isCarpetBot(bot)) {
                GexpressTestState.unmarkRole(bot.getUuid());
                GexpressTestState.clearModifiers(bot.getUuid());
                bot.networkHandler.disconnect(Text.literal("Test reset"));
            }
        }
        var game = GameWorldComponent.KEY.get(world);
        game.readFromNbt(s.gameState, world.getRegistryManager()); game.sync();
        var mods = WorldModifierComponent.KEY.get(world);
        mods.readFromNbt(s.modifiers, world.getRegistryManager()); mods.sync();
        dev.mapselect.game.LastDeathShieldManager.restoreAfterTesting(s.shields);
        var owner = src.getServer().getPlayerManager().getPlayer(s.owner.id);
        if (owner != null) s.owner.restore(owner, world);
        else RESTORES.put(s.owner.id, new PendingRestore(s.owner, world));
        src.sendFeedback(() -> Text.literal("Test reset. Removed the test bots and restored your inventory, role, modifiers, position and game mode."), false);
        return true;
    }

    private static void revive(ServerPlayerEntity player) {
        player.stopRiding(); player.setSneaking(false); player.setPose(EntityPose.STANDING);
        player.changeGameMode(GameMode.ADVENTURE);
        player.setHealth(player.getMaxHealth()); player.setFireTicks(0); player.fallDistance = 0;
        player.setVelocity(Vec3d.ZERO); player.velocityModified = true;
        player.networkHandler.sendPacket(new SetCameraEntityS2CPacket(player));
        dev.doctor4t.wathe.compat.TrainVoicePlugin.resetPlayer(player.getUuid());
        for (var entity : player.getServerWorld().iterateEntities())
            if (entity instanceof dev.doctor4t.wathe.entity.PlayerBodyEntity body && player.getUuid().equals(body.getPlayerUuid())) body.discard();
    }

    private static int error(ServerCommandSource src, String message) { src.sendError(Text.literal(message)); return 0; }
    private record PendingRestore(PlayerSnapshot snapshot, ServerWorld world) {}

    private static final class Session {
        final ServerCommandSource source;
        final String scenario;
        final List<String> names = List.of("GE_Test1", "GE_Test2", "GE_Test3");
        final PlayerSnapshot owner;
        final NbtCompound gameState = new NbtCompound();
        final NbtCompound modifiers = new NbtCompound();
        final dev.mapselect.game.LastDeathShieldManager.TestingState shields = dev.mapselect.game.LastDeathShieldManager.snapshotForTesting();
        int timeout = 1200;
        boolean started, ready;
        Session(ServerCommandSource source, String scenario, ServerPlayerEntity player) {
            this.source = source; this.scenario = scenario; this.owner = new PlayerSnapshot(player);
            GameWorldComponent.KEY.get(source.getWorld()).writeToNbt(gameState, source.getWorld().getRegistryManager());
            WorldModifierComponent.KEY.get(source.getWorld()).writeToNbt(modifiers, source.getWorld().getRegistryManager());
        }
    }

    private static final class PlayerSnapshot {
        final UUID id; final Vec3d pos; final float yaw, pitch, health; final GameMode mode;
        final List<ItemStack> inventory = new ArrayList<>(); final int selectedSlot;
        final boolean roleTester;
        final NbtCompound mood = new NbtCompound(), shop = new NbtCompound(), hunger = new NbtCompound();
        PlayerSnapshot(ServerPlayerEntity p) {
            p.closeHandledScreen(); id = p.getUuid(); pos = p.getPos(); yaw = p.getYaw(); pitch = p.getPitch(); health = p.getHealth();
            mode = p.interactionManager.getGameMode(); selectedSlot = p.getInventory().selectedSlot;
            roleTester = GexpressTestState.isRoleTester(p);
            dev.doctor4t.wathe.cca.PlayerMoodComponent.KEY.get(p).writeToNbt(mood, p.getRegistryManager());
            dev.doctor4t.wathe.cca.PlayerShopComponent.KEY.get(p).writeToNbt(shop, p.getRegistryManager());
            p.getHungerManager().writeNbt(hunger);
            for (int i = 0; i < p.getInventory().size(); i++) inventory.add(p.getInventory().getStack(i).copy());
        }
        void restore(ServerPlayerEntity p, ServerWorld world) {
            p.changeGameMode(mode); p.teleport(world, pos.x, pos.y, pos.z, yaw, pitch); p.setHealth(health);
            for (int i = 0; i < inventory.size(); i++) p.getInventory().setStack(i, inventory.get(i).copy());
            p.getInventory().selectedSlot = selectedSlot; p.currentScreenHandler.sendContentUpdates();
            var moodComp = dev.doctor4t.wathe.cca.PlayerMoodComponent.KEY.get(p);
            moodComp.readFromNbt(mood, world.getRegistryManager()); moodComp.sync();
            var shopComp = dev.doctor4t.wathe.cca.PlayerShopComponent.KEY.get(p);
            shopComp.readFromNbt(shop, world.getRegistryManager()); shopComp.sync();
            p.getHungerManager().readNbt(hunger);
            if (roleTester) GexpressTestState.markRole(id); else GexpressTestState.unmarkRole(id);
        }
    }
}
