package id.loclive.singleplayer;

import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

/** Strictly validated owner settings. Only safe values can enter SNBT commands. */
public record LiveChatAppearance(String header, String titleColor, String nameColor,
        String messageColor, String backgroundColor, int opacity, int width, int scale, boolean shadow,
        boolean seeThrough) {
    public static final LiveChatAppearance DEFAULT = new LiveChatAppearance(
        "✦ LIVE CHAT ✦", "#55E6E6", "#FFD76A", "#FFFFFF", "#1E2775", 45, 180, 100, true, false);
    private static final Pattern HEX = Pattern.compile("#[0-9a-fA-F]{6}");
    private static String hex(String input, String fallback) {
        return input != null && HEX.matcher(input).matches() ? input.toUpperCase(java.util.Locale.ROOT) : fallback;
    }
    private static int number(String input, int fallback, int lo, int hi) {
        try { return Math.max(lo, Math.min(hi, Integer.parseInt(input))); }
        catch (RuntimeException ex) { return fallback; }
    }
    private static String title(String input) {
        if (input == null) return DEFAULT.header();
        String value = input.replaceAll("[\\p{Cntrl}\\p{Cf}]", " ").replaceAll("\\s+", " ").strip();
        return value.isEmpty() ? DEFAULT.header() : value.substring(0, Math.min(32, value.length()));
    }
    public static LiveChatAppearance parse(Map<String,String> values) {
        LiveChatAppearance d = DEFAULT;
        return new LiveChatAppearance(title(values.get("style_header")),
            hex(values.get("style_title_color"), d.titleColor()),
            hex(values.get("style_name_color"), d.nameColor()),
            hex(values.get("style_message_color"), d.messageColor()),
            hex(values.get("style_background_color"), d.backgroundColor()),
            number(values.get("style_opacity"), d.opacity(), 0, 100),
            number(values.get("style_width"), d.width(), 100, 400),
            number(values.get("style_scale"), d.scale(), 60, 160),
            "true".equals(values.getOrDefault("style_shadow", "true")),
            "true".equals(values.getOrDefault("style_see_through", "false")));
    }
    public static LiveChatAppearance fromProperties(Properties props) {
        java.util.HashMap<String,String> settings = new java.util.HashMap<>();
        for (String key : new String[]{"style_header","style_title_color","style_name_color",
                "style_message_color","style_background_color","style_opacity","style_width",
                "style_shadow","style_see_through","style_scale"}) {
            if (props.containsKey(key)) settings.put(key, props.getProperty(key));
        }
        return parse(settings);
    }
    public int backgroundArgb() {
        int rgb = Integer.parseInt(backgroundColor().substring(1), 16);
        int alpha = Math.round(opacity() * 255f / 100f);
        return (alpha << 24) | rgb;
    }
    public String displayFields(String viewer, String message) {
        String factor=String.format(java.util.Locale.ROOT,"%.2ff",scale()/100.0);
        return "transformation:{scale:["+factor+","+factor+","+factor+"]},line_width:" + width() + ",shadow:" + (shadow() ? "1b":"0b")
            + ",see_through:" + (seeThrough() ? "1b":"0b")
            + ",background:" + backgroundArgb() + ",text:"
            + TextDisplayPayload.component(viewer,message,this);
    }
}
