# Death Count v1.0 – perbaikan total 1.21.x

## Status
Ini SOURCE pengembangan. Tidak ada JAR distribusi dalam ZIP ini, karena Minecraft/Fabric/Gradle dependency tidak dapat diunduh di lingkungan pembuatan. Jangan memakai file LOCAL-TEST-UNVERIFIED dari rilis sebelumnya.

## Perbaikan
- `fabric.mod.json` kini `environment: "*"` dan hanya memakai entrypoint `main`: bukan mod client-only.
- Server integrasi Singleplayer menangani hitungan dan label, juga dapat berjalan di dedicated server.
- Jumlah kematian dibaca langsung dari statistik vanilla yang disimpan per-world (`stats/<UUID>.json`). Tidak menggunakan perintah `scoreboard players get` dan tidak bergantung pada metode refleksi.
- Tampilan actionbar dikirim langsung dengan Minecraft `displayClientMessage`, dan `text_display` di atas kepala dibuat lewat perintah di sisi server.
- Mempertahankan tengkorak merah, tinggi 2.30 blok dan smoothing 3 tick.
- Mempertahankan ikon pixel-art sebelumnya, masih berstatus AI-generated dan tidak cocok untuk halaman publikasi Modrinth sesuai kebijakan mereka.

## Build resmi di GitHub Actions (Internet dibutuhkan)
1. Unggah semua isi ZIP ini ke repository GitHub milikmu (private lebih aman untuk pengembangan).
2. Actions > 'Build Death Count 1.21.x (real Fabric Loom)' > Run workflow.
3. Unduh artifact Minecraft 1.21.1 / 1.21.11 jika job build BERHASIL. Artifact berisi JAR remapped keluaran Fabric Loom, bukan JAR simulasi.
4. Pasang satu JAR sesuai versi Minecraft dan Fabric API yang sesuai, jangan gabung JAR lama.
5. Jika build gagal, lihat log job; source belum bisa diklaim sudah terkompilasi terhadap Minecraft asli.

Alternatif lokal (Gradle 9 + JDK 21):
`gradle -p builds/1.21.1 build` atau `gradle -p builds/1.21.11 build`.

## Verifikasi
- World baru: muncul actionbar `☠ Death Count: 0` dalam 1 detik.
- F5: label `☠ Death Count: 0` muncul di kepala (perhatikan kamera).
- Mati sekali: menjadi 1. Keluar-masuk: tetap 1. World baru: 0.
- Periksa log `[Death Count]` dan syntax error jika label tidak terbentuk.
- Jika setting lama keduanya false, tutup game dan hapus `config/death-count.properties` untuk mengembalikan true.

**Tidak menjamin semua patch 1.21.x:** dua konfigurasi adalah target build terpisah, bukan satu JAR universal. Pengujian runtime tetap wajib.
