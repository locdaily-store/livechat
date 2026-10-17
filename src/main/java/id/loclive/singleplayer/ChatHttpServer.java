package id.loclive.singleplayer;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Small loopback-only bridge for the existing LocLive control center.
 *  No Minecraft classes here, so protocol/security can be tested in isolation. */
public final class ChatHttpServer implements AutoCloseable {
    public record Chat(String viewer, String message, String cardPng, int maxBoxes, int duration, int distance, String interaction) {}
    @FunctionalInterface public interface Receiver { void onChat(Chat chat); }
    private final HttpServer server;
    private final ExecutorService pool;
    private final String secret;
    private final Receiver receiver;
    private final AtomicBoolean installed = new AtomicBoolean(false);
    private final AtomicBoolean enabled = new AtomicBoolean(false);
    private final AtomicBoolean clearRequested = new AtomicBoolean(false);
    private volatile LiveChatAppearance appearance = LiveChatAppearance.DEFAULT;
    private final java.util.concurrent.atomic.AtomicInteger styleVersion = new java.util.concurrent.atomic.AtomicInteger();
    public LiveChatAppearance appearance() { return appearance; }
    public int styleVersion() { return styleVersion.get(); }
    public boolean takeClearRequested() { return clearRequested.getAndSet(false); }
    public void restoreState(boolean wasInstalled, boolean wasEnabled, LiveChatAppearance style) {
        installed.set(wasInstalled);
        enabled.set(wasInstalled && wasEnabled);
        appearance = style;
    }

    public ChatHttpServer(String host, int port, String secret, Receiver receiver) throws IOException {
        if (!"127.0.0.1".equals(host)) throw new IllegalArgumentException("Loopback only");
        if (secret == null || secret.length() < 12 || "CHANGE_ME_MINECRAFT".equals(secret))
            throw new IllegalArgumentException("Set MINECRAFT_SECRET in LocLive before starting the mod");
        this.secret = secret;
        this.receiver = receiver;
        this.server = HttpServer.create(new InetSocketAddress(host, port), 8);
        this.pool = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "LocLive-Singleplayer-HTTP");
            t.setDaemon(true);
            return t;
        });
        server.setExecutor(pool);
        server.createContext("/live-chat/status", this::status);
        server.createContext("/live-chat/control", this::control);
        server.createContext("/live-chat/show", this::show);
        server.createContext("/live-chat/style", this::style);
    }

    public void start() { server.start(); }
    public boolean isInstalled() { return installed.get(); }
    public boolean isEnabled() { return enabled.get(); }

    private void status(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) { respond(ex,405,"GET only"); return; }
        respond(ex,200,"{\"ok\":true,\"installed\":" + installed.get() + ",\"enabled\":" + enabled.get() + ",\"style_revision\":" + styleVersion.get() + ",\"platform\":\"fabric-singleplayer-preview\"}");
    }

    private void control(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) { respond(ex,405,"POST only"); return; }
        Map<String,String> form = form(ex);
        if (form == null) return;
        if (!authorized(form)) { respond(ex,403,"bad secret"); return; }
        switch (form.getOrDefault("action", "")) {
            case "install" -> installed.set(true);
            case "uninstall" -> { enabled.set(false); installed.set(false); clearRequested.set(true); }
            case "enable" -> { if (!installed.get()) { respond(ex,409,"not installed"); return; } enabled.set(true); }
            case "disable" -> { enabled.set(false); clearRequested.set(true); }
            case "clear" -> clearRequested.set(true);
            default -> { respond(ex,422,"bad action"); return; }
        }
        respond(ex,200,"{\"ok\":true}");
    }

    private void style(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) { respond(ex,405,"POST only"); return; }
        Map<String,String> values = form(ex);
        if (values == null) return;
        if (!authorized(values)) { respond(ex,403,"bad secret"); return; }
        appearance = LiveChatAppearance.parse(values);
        styleVersion.incrementAndGet();
        respond(ex,200,"{\"ok\":true,\"style_revision\":"+styleVersion.get()+"}");
    }

    private void show(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) { respond(ex,405,"POST only"); return; }
        Map<String,String> form = form(ex);
        if (form == null) return;
        if (!authorized(form)) { respond(ex,403,"bad secret"); return; }
        if (!installed.get() || !enabled.get()) { respond(ex,409,"not enabled"); return; }
        String viewer = clean(form.getOrDefault("viewer",""),28);
        String message = clean(form.getOrDefault("message",""),80);
        // Media arrives pre-rendered by the local Bridge, never via a remote URL.
        // Keep the limit below the maximum form size to avoid memory abuse.
        String card = form.getOrDefault("card_png", "");
        if (card.length() > 60000 || (!card.isEmpty() && !card.matches("[A-Za-z0-9+/=]+"))) {
            respond(ex,413,"chat image too large or malformed"); return;
        }
        if (viewer.isBlank() || message.isBlank()) { respond(ex,422,"viewer/message required"); return; }
        String interaction = form.getOrDefault("interaction", "both");
        if (!("both".equals(interaction) || "destroy".equals(interaction) || "display".equals(interaction))) {
            respond(ex,422,"invalid interaction"); return;
        }
        Chat chat = new Chat(viewer,message,card,number(form,"max_boxes",3,1,24),number(form,"duration",12,5,300),
            number(form,"distance",4,2,12),interaction);
        receiver.onChat(chat);
        respond(ex,200,"{\"ok\":true,\"queued\":true}");
    }

    private Map<String,String> form(HttpExchange ex) throws IOException {
        if (ex.getRemoteAddress() == null || !ex.getRemoteAddress().getAddress().isLoopbackAddress()) {
            respond(ex,403,"loopback only"); return null;
        }
        byte[] data;
        try (InputStream in = ex.getRequestBody()) { data = in.readNBytes(65537); }
        if (data.length > 65536) { respond(ex,413,"request too large"); return null; }
        Map<String,String> out = new HashMap<>();
        for (String part : new String(data,StandardCharsets.UTF_8).split("&")) {
            if (part.isEmpty()) continue;
            String[] kv = part.split("=",2);
            try {
                String key = URLDecoder.decode(kv[0],StandardCharsets.UTF_8);
                String value = URLDecoder.decode(kv.length > 1 ? kv[1] : "",StandardCharsets.UTF_8);
                out.put(key,value);
            } catch (IllegalArgumentException ex1) { respond(ex,422,"malformed form"); return null; }
        }
        return out;
    }
    private boolean authorized(Map<String,String> args) {
        return java.security.MessageDigest.isEqual(secret.getBytes(StandardCharsets.UTF_8),
            args.getOrDefault("secret","").getBytes(StandardCharsets.UTF_8));
    }
    private static String clean(String s,int max) {
        String text = s.replaceAll("[\\p{Cntrl}\\p{Cf}&&[^\u200d\ufe0f\ufe0e]]"," ").replaceAll("\\s+"," ").strip();
        if (text.codePointCount(0, text.length()) <= max) return text;
        int end = text.offsetByCodePoints(0, max);
        while (end < text.length()) {
            int cp = text.codePointAt(end);
            int previous = text.codePointBefore(end);
            if (cp == 0x200D || cp == 0xFE0E || cp == 0xFE0F
                || Character.getType(cp) == Character.NON_SPACING_MARK || previous == 0x200D) {
                end += Character.charCount(cp);
            } else break;
        }
        return text.substring(0, end);
    }
    private static int number(Map<String,String> data,String key,int d,int min,int max) {
        try { return Math.max(min,Math.min(max,Integer.parseInt(data.getOrDefault(key,Integer.toString(d))))); }
        catch (NumberFormatException ex) { return d; }
    }
    private static void respond(HttpExchange ex,int code,String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type",body.startsWith("{") ? "application/json; charset=utf-8" : "text/plain; charset=utf-8");
        ex.sendResponseHeaders(code,bytes.length);
        try (var stream=ex.getResponseBody()) { stream.write(bytes); }
    }
    @Override public void close() {
        server.stop(0);
        pool.shutdownNow();
    }
}
