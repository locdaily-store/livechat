package id.loclive.singleplayer;

/** Minecraft 1.21.5+ uses a structured SNBT text component on text_display. */
public final class TextDisplayPayload {
    private TextDisplayPayload() {}

    public static String component(String viewer, String message) {
        return component(viewer,message,LiveChatAppearance.DEFAULT);
    }

    public static String component(String viewer, String message, LiveChatAppearance style) {
        String name = quote(trim(viewer, 28));
        String content = quote(trim(message, 80));
        String header = quote(trim(style.header(),32));
        // SNBT compound and validated #RRGGBB values. Never send raw JSON strings.
        return "{text:\"" + header + "\\n\",color:\"" + style.titleColor() + "\",bold:true,extra:["
            + "{text:\"@" + name + "\\n\",color:\"" + style.nameColor() + "\",bold:true},"
            + "{text:\"" + content + "\",color:\"" + style.messageColor() + "\",bold:false}]}";
    }

    private static String trim(String value, int limit) {
        if (value == null) return "";
        String normalized = value.replaceAll("[\\p{Cntrl}\\p{Cf}&&[^\u200d]]", " ")
            .replaceAll("\\s+", " ").strip();
        if (normalized.codePointCount(0, normalized.length()) <= limit) return normalized;
        int end = normalized.offsetByCodePoints(0, limit);
        while (end < normalized.length()) {
            int cp = normalized.codePointAt(end);
            int previous = normalized.codePointBefore(end);
            if (cp == 0x200D || cp == 0xFE0E || cp == 0xFE0F
                || Character.getType(cp) == Character.NON_SPACING_MARK || previous == 0x200D) {
                end += Character.charCount(cp);
            } else break;
        }
        return normalized.substring(0, end);
    }

    private static String quote(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "\\r");
    }
}
