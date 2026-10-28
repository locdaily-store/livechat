package id.deathcount;

import net.fabricmc.loader.api.FabricLoader;

/** TextDisplay's NBT text changed from a JSON string to a component compound in 1.21.5. */
final class DisplayText {
    static final boolean LEGACY = oldFormat();
    private DisplayText() { }
    static boolean oldFormat() {
        String version = FabricLoader.getInstance().getModContainer("minecraft")
            .map(container -> container.getMetadata().getVersion().getFriendlyString()).orElse("1.21.1");
        if (version.equals("1.21")) return true;
        if (!version.startsWith("1.21.")) return false;
        try { return Integer.parseInt(version.substring(5).split("[^0-9]", 2)[0]) < 5; }
        catch (Exception ex) { return false; }
    }
    static String asNbt(int count) {
        String txt = " Death Count: " + Math.max(0, count);
        if (!LEGACY) {
            return "{text:\"☠\",color:\"red\",extra:[{text:\"" + txt + "\",color:\"white\"}]}";
        }
        return "'{\"text\":\"☠\",\"color\":\"red\",\"extra\":[{\"text\":\"" + txt + "\",\"color\":\"white\"}]}'";
    }
}
