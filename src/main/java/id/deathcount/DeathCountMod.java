package id.deathcount;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** A logical-server mod: works in Singleplayer integrated server and dedicated servers. */
public final class DeathCountMod implements ModInitializer {
    private final Map<UUID, Tracker> tracking = new HashMap<>();
    private DeathCountConfig config;
    private MinecraftServer currentServer;
    private int ticks;
    private int commandFailures;

    static final class Tracker {
        final String tag;
        int deaths = -1;
        String dimension = "";
        String name;
        Tracker(UUID id, String name) {
            this.tag = "dc_" + id.toString().replace("-", "");
            this.name = name;
        }
    }

    @Override public void onInitialize() {
        config = DeathCountConfig.load();
        ServerLifecycleEvents.SERVER_STARTED.register(this::start);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::stop);
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
        System.out.println("[Death Count] v1.0 1.21.x rewrite loaded (environment=*, server statistics based)");
    }

    private void start(MinecraftServer server) {
        currentServer = server;
        tracking.clear();
        ticks = 0;
        commandFailures = 0;
        // Labels are temporary and can be left behind by an unclean exit.
        purgeAllLabels();
        System.out.println("[Death Count] World/server started. Deaths read from each world's vanilla stats files.");
    }

    private void stop(MinecraftServer server) {
        if (server != currentServer) return;
        purgeAllLabels();
        tracking.clear();
        currentServer = null;
    }

    private void tick(MinecraftServer server) {
        if (server != currentServer) return;
        ticks++;
        if (ticks % 10 == 1) updatePlayers(server);
        if (config.floating() && ticks % config.followInterval() == 0) followPlayers(server);
    }

    private void updatePlayers(MinecraftServer server) {
        Set<UUID> connected = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID uuid = player.getUUID();
            connected.add(uuid);
            String name = player.getName().getString();
            Tracker state = tracking.computeIfAbsent(uuid, id -> new Tracker(id, name));
            state.name = name;
            String dimension = player.level().dimension().location().toString();
            if (!dimension.equals(state.dimension)) {
                // When switching Nether/End, remove any display from the former dimension.
                deleteLabels(state);
                state.dimension = dimension;
                state.deaths = -1;
            }

            // Unlike the previous build, no reflective scoreboard/command-result reads.
            // These statistics are saved in <world>/stats/<uuid>.json, per world.
            int deaths;
            try {
                deaths = Math.max(0, player.getStats().getValue(Stats.CUSTOM.get(Stats.DEATHS)));
            } catch (Exception ex) {
                if (commandFailures++ < 5) System.err.println("[Death Count] Cannot read vanilla deaths for " + name + ": " + ex);
                deaths = Math.max(0, state.deaths);
            }
            if (deaths != state.deaths) {
                state.deaths = deaths;
                if (config.floating()) {
                    // Recreate on value changes, rather than relying on data-merge syntax
                    // that changed between 1.21.4 and 1.21.5.
                    deleteLabels(state);
                }
            }
            if (config.floating()) ensureLabel(state);
            else deleteLabels(state);
            if (config.actionbar()) {
                player.displayClientMessage(Component.literal("☠ ").withStyle(ChatFormatting.RED)
                    .append(Component.literal("Death Count: " + deaths).withStyle(ChatFormatting.WHITE)), true);
            }
        }
        tracking.entrySet().removeIf(entry -> {
            if (connected.contains(entry.getKey())) return false;
            deleteLabels(entry.getValue());
            return true;
        });
    }

    private void ensureLabel(Tracker state) {
        // Re-check every 10 ticks; '/execute unless entity' prevents duplicates.
        // Position is centered above the player at the actual spawn point.
        if (!state.name.matches("[A-Za-z0-9_]{1,16}")) return;
        String nbt = "{Tags:[\"death_count_label\",\"" + state.tag + "\"]," +
            "billboard: \"center\",alignment: \"center\",background:0," +
            "default_background:0b,shadow:0b,see_through:0b," +
            "teleport_duration:" + config.smoothing() + "," +
            "text:" + DisplayText.asNbt(state.deaths) + "}";
        String command = "execute as " + state.name + " at @s unless entity " +
            "@e[type=minecraft:text_display,tag=" + state.tag + ",limit=1] run summon " +
            "minecraft:text_display ~ ~" + fmt(config.height()) + " ~ " + nbt;
        execute(command);
    }

    private void followPlayers(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Tracker state = tracking.get(player.getUUID());
            if (state == null || !state.name.matches("[A-Za-z0-9_]{1,16}")) continue;
            execute("execute as " + state.name + " at @s run tp " +
                "@e[type=minecraft:text_display,tag=" + state.tag + ",limit=1] ~ ~" + fmt(config.height()) + " ~");
        }
    }

    private static String fmt(double value) { return String.format(Locale.ROOT, "%.3f", value); }

    private void deleteLabels(Tracker state) {
        if (currentServer == null) return;
        for (String dim : new String[] {"minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"}) {
            execute("execute in " + dim + " run kill @e[type=minecraft:text_display,tag=" + state.tag + "]");
        }
    }

    private void purgeAllLabels() {
        if (currentServer == null) return;
        for (String dim : new String[] {"minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"}) {
            execute("execute in " + dim + " run kill @e[type=minecraft:text_display,tag=death_count_label]");
        }
    }

    private void execute(String command) {
        if (currentServer == null) return;
        try {
            // This method returns int on some 1.21.x patches and void on others;
            // the return value is deliberately ignored, and the count NEVER uses it.
            currentServer.getCommands().performPrefixedCommand(
                currentServer.createCommandSourceStack().withSuppressedOutput(), command);
        } catch (Exception ex) {
            if (commandFailures++ < 10) System.err.println("[Death Count] Command could not execute: " + command + " | " + ex);
        }
    }
}
