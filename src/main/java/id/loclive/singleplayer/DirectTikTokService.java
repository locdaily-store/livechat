package id.loclive.singleplayer;

import java.lang.reflect.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Prototype in-process TikTok connector. No external Bridge process and no credentials. */
public final class DirectTikTokService implements AutoCloseable {
    @FunctionalInterface public interface CommentReceiver { void receive(String username,String message); }
    private final CommentReceiver receiver;
    private final AtomicBoolean stopped=new AtomicBoolean();
    private volatile Object connectedClient;
    private volatile String status="Belum tersambung";
    public DirectTikTokService(CommentReceiver receiver){ this.receiver=receiver; }
    public String status(){return status;}
    public void connectAsync(String username){
        String user=OwnerSettings.normalizeUsername(username);
        if(user.isBlank())throw new IllegalArgumentException("Isi username TikTok terlebih dahulu.");
        stopped.set(false);
        Thread thread=new Thread(()->connect(user),"LocLive-TikTok");
        thread.setDaemon(true);thread.start();
    }
    private void connect(String username){
        try {
            // Library names can change between releases; fail visibly, never invent comments.
            Class<?> api=null;
            for(String candidate:new String[]{"io.github.jwdeveloper.tiktok.TikTokLive",
                "io.github.jwdeveloper.tiktok.TikTokLiveClient",
                "io.github.jwdeveloper.tiktok.TikTokLive"}) {
                try{api=Class.forName(candidate);break;}catch(ClassNotFoundException ignored){}
            }
            if(api==null)throw new ClassNotFoundException("TikTok LIVE Java SDK belum tersedia pada JAR mod.");
            Object builder=api.getMethod("newClient",String.class).invoke(null,username);
            for(Method method:builder.getClass().getMethods()){
                if(!method.getName().equals("onComment") || method.getParameterCount()!=1)continue;
                Class<?> listener=method.getParameterTypes()[0];
                if(!listener.isInterface())continue;
                Object proxy=Proxy.newProxyInstance(listener.getClassLoader(),new Class[]{listener},(p,called,args)->{
                    if(stopped.get())return null;
                    if(args!=null && args.length>=2){
                        String name=extract(args[1],"getUser","getUniqueId");
                        if(name.isBlank())name=extract(args[1],"getUser","getName");
                        String comment=extract(args[1],"getText");
                        if(!name.isBlank() && !comment.isBlank())receiver.receive(name,comment);
                    }
                    return null;
                });
                method.invoke(builder,proxy);break;
            }
            if(stopped.get())return;
            status="Menghubungkan @"+username;
            connectedClient=builder.getClass().getMethod("buildAndConnect").invoke(builder);
            if(!stopped.get())status="Koneksi dibuka. Menunggu komentar LIVE.";
        }catch(Exception ex){status="TikTok belum bisa tersambung: "+ex.getClass().getSimpleName();
            System.err.println("[LocLive] "+status+" : "+ex.getMessage());}
    }
    private static String extract(Object item,String...methods){
        try {Object cur=item;for(String n:methods){if(cur==null)return "";cur=cur.getClass().getMethod(n).invoke(cur);}return cur==null?"":cur.toString();}
        catch(Exception ignored){return "";}
    }
    @Override public void close(){
        stopped.set(true);
        Object c=connectedClient;connectedClient=null;
        if(c!=null)for(String method:new String[]{"disconnect","close","stop"}){
            try{c.getClass().getMethod(method).invoke(c);break;}catch(Exception ignored){}
        }
        status="Terputus";
    }
}
