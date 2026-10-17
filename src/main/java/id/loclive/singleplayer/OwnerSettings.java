package id.loclive.singleplayer;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Properties;

/** Local-only streamer settings. Never store account password, session ID or cookies. */
public final class OwnerSettings {
    public record Config(String tiktokUsername, boolean enabled, int maxBoxes, int durationSeconds,
                         int distance, int opacity, int scale) {}
    private static volatile Config current = new Config("", false, 4, 14, 4, 45, 100);
    private static Path path;
    public static Config get() { return current; }
    public static synchronized void load(Path configDir) {
        path = configDir.resolve("loclive-rinxasoy-singleplayer.properties");
        Properties p = new Properties();
        try { if (Files.isRegularFile(path)) try (var r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) { p.load(r); } }
        catch (Exception ex) { System.err.println("[LocLive] Pengaturan tidak dapat dibaca: " + ex.getMessage()); }
        current = parse(p);
    }
    public static Config parse(Properties p) {
        return new Config(normalizeUsername(p.getProperty("tiktok_username", "")),
            Boolean.parseBoolean(p.getProperty("enabled", "false")),
            number(p.getProperty("max_boxes"),4,1,12),
            number(p.getProperty("duration_seconds"),14,5,120),
            number(p.getProperty("distance"),4,2,8),
            number(p.getProperty("opacity"),45,0,100),
            number(p.getProperty("scale"),100,60,160));
    }
    public static String normalizeUsername(String input) {
        String value = input == null ? "" : input.trim().replaceFirst("^@", "");
        if (value.length() > 30 || !value.matches("[A-Za-z0-9._]*"))
            throw new IllegalArgumentException("Username TikTok hanya boleh memakai huruf, angka, titik, dan underscore.");
        return value;
    }
    private static int number(String text,int fallback,int min,int max) {
        try { return Math.max(min, Math.min(max,Integer.parseInt(text))); }
        catch(Exception ignored) {return fallback;}
    }
    public static synchronized void save(Config c) throws Exception {
        if(path==null) throw new IllegalStateException("Folder konfigurasi belum siap");
        String user = normalizeUsername(c.tiktokUsername());
        Config clean = new Config(user,c.enabled(),Math.max(1,Math.min(12,c.maxBoxes())),
            Math.max(5,Math.min(120,c.durationSeconds())),Math.max(2,Math.min(8,c.distance())),
            Math.max(0,Math.min(100,c.opacity())),Math.max(60,Math.min(160,c.scale())));
        Properties p = new Properties();
        p.setProperty("tiktok_username",user);p.setProperty("enabled",String.valueOf(clean.enabled()));
        p.setProperty("max_boxes",String.valueOf(clean.maxBoxes()));
        p.setProperty("duration_seconds",String.valueOf(clean.durationSeconds()));
        p.setProperty("distance",String.valueOf(clean.distance()));
        p.setProperty("opacity",String.valueOf(clean.opacity()));
        p.setProperty("scale",String.valueOf(clean.scale()));
        Files.createDirectories(path.getParent());
        Path tmp=Files.createTempFile(path.getParent(),"loclive-", ".tmp");
        try {
            try(var w=Files.newBufferedWriter(tmp,StandardCharsets.UTF_8)) {p.store(w,"LocLive RinxAsoy Singleplayer");}
            try { Files.move(tmp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch(AtomicMoveNotSupportedException ex){Files.move(tmp,path,StandardCopyOption.REPLACE_EXISTING);}
        } finally {Files.deleteIfExists(tmp);}
        current=clean;
    }
    private OwnerSettings() {}
}
