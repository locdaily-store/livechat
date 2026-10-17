# Checkpoint LocLive R136 • 2026-10-17
Baseline sumber: R135, BUKAN R122.
Permintaan: Singleplayer mod milik RinxAsoy, .jar yang bisa dipasang tanpa CLI, UI Mod Menu, username TikTok, box 3D bisa klik, drag, hancur, tanpa Paper dan Bridge.
Source terbuat: OwnerSettings.java, OwnerSettingsScreen.java, LocLiveModMenu.java, DirectTikTokService.java; integrasi pada LocLiveSingleplayerMod.java; Gradle setup + GitHub Actions.
Status: DEVELOPMENT SOURCE ONLY. **JAR playable belum berhasil dibangun/diuji.**
Uji lokal: SettingsSmoke (normalisasi username, batas nilai, penolakan injeksi, simpan dan baca ulang). Berhasil.
Blocker utama: JDK25, Fabric/Minecraft deps dan network build tidak tersedia; API 26.3 dan SDK perlu verifikasi; versi Fabric API terverifikasi lewat katalog Maven namun download lokal terblokir; Mod Menu GUI compile belum divalidasi.
Selanjutnya: cek versi Fabric API; test gradle 9.6 Java25; perbaiki SDK classpath/runtime, build JAR, jalankan Minecraft 26.3 PrismLauncher, tes klik/drag/cleanup/live, baru rilis Production dan Only Changes.
Jangan menyebut ZIP source sebagai mod JAR. Jangan merekayasa status berhasil atau menyarankan mod pihak ketiga untuk menggantikan karya sendiri.
