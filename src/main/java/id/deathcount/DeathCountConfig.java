package id.deathcount;

import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.Reader;
import java.io.Writer;
import java.util.Properties;

record DeathCountConfig(boolean actionbar, boolean floating, double height, int smoothing, int followInterval) {
    static DeathCountConfig load() {
        Properties p = new Properties();
        Path file = FabricLoader.getInstance().getConfigDir().resolve("death-count.properties");
        try {
            if (Files.exists(file)) try (Reader r = Files.newBufferedReader(file)) { p.load(r); }
            p.putIfAbsent("show_actionbar", "true");
            p.putIfAbsent("show_floating_label", "true");
            p.putIfAbsent("label_height_above_feet", "2.30");
            p.putIfAbsent("label_follow_smoothing_ticks", "3");
            p.putIfAbsent("follow_interval_ticks", "1");
            Files.createDirectories(file.getParent());
            try (Writer w = Files.newBufferedWriter(file)) { p.store(w, "Death Count v1.0"); }
        } catch (Exception ex) { System.err.println("[Death Count] Config loading: " + ex); }
        return new DeathCountConfig(Boolean.parseBoolean(p.getProperty("show_actionbar", "true")),
            Boolean.parseBoolean(p.getProperty("show_floating_label", "true")),
            clampDouble(p.getProperty("label_height_above_feet"), 2.30, 1.85, 4.0),
            clampInt(p.getProperty("label_follow_smoothing_ticks"), 3, 1, 6),
            clampInt(p.getProperty("follow_interval_ticks"), 1, 1, 5));
    }
    private static double clampDouble(String s, double fallback, double min, double max) {
        try { double d = Double.parseDouble(s); return Double.isFinite(d) ? Math.max(min, Math.min(max, d)) : fallback; }
        catch (Exception ex) { return fallback; }
    }
    private static int clampInt(String s, int fallback, int min, int max) {
        try { int n = Integer.parseInt(s); return Math.max(min, Math.min(max, n)); }
        catch (Exception ex) { return fallback; }
    }
}
