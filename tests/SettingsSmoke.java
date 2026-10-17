package id.loclive.singleplayer;
import java.util.Properties;
import java.nio.file.*;
public class SettingsSmoke {
 public static void main(String[] a)throws Exception {
  var p=new Properties();p.setProperty("tiktok_username","@rinx_soy");p.setProperty("max_boxes","999");
  var c=OwnerSettings.parse(p);
  if(!"rinx_soy".equals(c.tiktokUsername()) || c.maxBoxes()!=12)throw new AssertionError("normalization");
  if(!OwnerSettings.normalizeUsername("@abc.12").equals("abc.12"))throw new AssertionError("username");
  try{OwnerSettings.normalizeUsername("@name /op");throw new AssertionError("security");}catch(IllegalArgumentException expected){}
  Path dir=Files.createTempDirectory("loclive-r136-test");OwnerSettings.load(dir);
  OwnerSettings.save(c);
  if(!OwnerSettings.get().tiktokUsername().equals("rinx_soy"))throw new AssertionError("persist");
  OwnerSettings.load(dir);
  if(!OwnerSettings.get().tiktokUsername().equals("rinx_soy"))throw new AssertionError("reload");
  System.out.println("PASS: 5 konfigurasi pemilik / sanitasi username");
 }
}
