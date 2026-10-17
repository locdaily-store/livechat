# LocLive Singleplayer by RinxAsoy • R136 (SOURCE/EXPERIMENTAL)

Ini source khusus untuk pengguna, diteruskan dari source Singleplayer LocLive R135 yang ada dalam paket maintenance. **Bukan salinan atau mod yang diberi nama ulang dari mod pihak ketiga.**

## Yang sudah ada dalam source
- 3D `TextDisplay` + `Interaction`: klik kiri hapus, klik kanan pegang/lepas, digerakkan mengikuti arah yaw dan pitch, easing; cleanup entity saat keluar world (berasal dari source LocLive sendiri).
- Pengaturan username TikTok di Mod Menu; nilai disimpan di config Minecraft, tidak meminta kata sandi/cookie.
- Jalur langsung di dalam proses Minecraft menuju TikTok LIVE; implementasinya bersifat prototipe dan belum dibuktikan terkoneksi. TikTok SDK adalah **library teknis MIT**, bukan mod pihak ketiga. Integrasi ke library membutuhkan pengujian nyata.
- No BAT/CMD/PowerShell dibutuhkan **untuk memasang JAR sesudah JAR selesai dibangun**.

## Kenapa belum ada JAR siap main?
Lingkungan build sekarang tidak memiliki JDK 25, Gradle, cache Minecraft/Fabric dan akses Maven/Fabric. Jadi build Minecraft 26.3 **belum dapat dijalankan atau diuji**. Jangan mengubah ZIP ini menjadi JAR atau menempatkannya di `mods`.

## Cara menghasilkan JAR nantinya tanpa mengetik CMD
1. Unggah isi folder ini ke repo GitHub privat yang kamu kuasai (jangan unggah data akun/password).
2. Jalankan GitHub Actions > `Build LocLive Private Minecraft Mod` > `Run workflow`.
3. Jika build berhasil, ambil artefak `.jar` hasil build. **Workflow masih perlu validasi dependency/API dan mungkin gagal sampai diperbaiki.**
4. Setelah JAR lulus uji, pasang Fabric untuk Minecraft 26.3 + Fabric API + Mod Menu, lalu tambahkan JAR ke PrismLauncher > Edit > Mods > Add File.

## Masalah yang belum selesai
- Verifikasi API Minecraft 26.3 untuk render entity, arah dan hitbox, serta Mod Menu UI.
- Fabric API 0.163.0+26.3 terdaftar di Maven resmi, tetapi belum dapat diunduh dan diuji dari lingkungan ini.
- Resolusi runtime SDK TikTokLiveJava, shading dependensi, interaksi, reconnect; **koneksi TikTok belum teruji dan bisa gagal**.
- Tes langsung dalam Minecraft, reproduksi crash, HUD emoji berwarna, klik dan drag; yang sudah diuji lokal hanya settings Java.

## Hak atas hasil kustom
Source perubahan khusus ini untuk proyek LocLive milik pengguna. Ketergantungan Fabric, Mod Menu dan library TikTok mempunyai lisensi sendiri, tidak menjadi milik eksklusif pengguna. Tidak ada JAR mod publik yang disamarkan sebagai karya pengguna.
