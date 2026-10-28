# Uji langsung wajib
1. Build dengan dependensi asli Fabric Loom dan periksa artifact `.jar` remapped.
2. Minecraft 1.21.1 dengan Fabric API khusus 1.21.1: buka world baru, pastikan actionbar dan label F5 terlihat.
3. Mati 1-2 kali, konfirmasi hitungan bertambah, relog world, verifikasi tetap.
4. World kedua: pastikan mulai 0; balik ke world lama: nilai lama kembali.
5. Pergi ke Nether dan The End; pastikan label tidak mengganda di tiga dimensi.
6. Minecraft 1.21.11 lakukan semua uji yang sama dengan JAR versi khususnya.
7. Coba bersama mod lain; periksa `logs/latest.log` ketika label gagal.
8. Uji FPS F5, sprint dan meloncat, serta perubahan posisi mengikuti kepala.
9. Pada server dedicated, pasang mod di server, tidak harus client; tes pemain yang join.
10. Jangan rilis Modrinth sebelum semua uji dan tinjauan kebijakan AI/lisensi selesai.
