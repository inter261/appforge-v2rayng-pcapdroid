# V2Ray Me — v2rayNG 2.3.11 + PCAPdroid

Satu codebase: **v2rayNG** memegang VPN (package `com.v2ray.me`, nama aplikasi "V2Ray Me"),
**PCAPdroid** (analisis trafik) ikut sebagai modul `:pcapdroid` dan bisa di-ON/OFF. Tanpa root.

> **Status: belum pernah dikompilasi.** Lingkungan tempat kode ini ditulis tidak punya akses ke Android SDK
> dan Gradle, jadi hanya sintaks yang diperiksa. Build pertama di GitHub Actions kemungkinan butuh
> satu-dua perbaikan kecil; kirim log-nya (artifact `build-reports` / log step "Build APK").

## Struktur

```
.github/workflows/build.yml   CI (arm64-v8a saja)
scripts/fetch-deps.sh         ambil submodule PCAPdroid, hev-socks5-tunnel, libv2ray.aar (versi dikunci)
compile-hevtun.sh             build hev-socks5-tunnel (arm64-v8a)
V2rayNG/                      project Gradle
  app/                        v2rayNG (applicationId com.v2ray.me, namespace tetap com.v2ray.ang)
  pcapdroid/                  PCAPdroid sebagai library (namespace com.emanuelef.remote_capture)
  generated/                  file generated libpcap/nDPI milik PCAPdroid
licenses/                     lisensi PCAPdroid (GPLv3)
```

## Cara kerja ON/OFF

```
PCAP OFF : tun -> hev-socks5-tunnel -> Xray           (v2rayNG biasa)
PCAP ON  : tun -> PCAPdroid -> SOCKS5 lokal -> Xray
```

* VPN (`CoreVpnService`) jalan di proses `:daemon`, PCAPdroid di proses utama. Serah-terima fd tun
  lewat `PcapTunController` (daemon) <-> `PcapLinkService` (proses utama).
* ON: buka menu samping -> **Traffic analysis (PCAPdroid)** -> tombol Start. OFF: tombol Stop.
  Saat pindah, koneksi yang sedang berjalan putus sebentar.
* Hanya satu pembaca tun pada satu waktu: hev dihentikan dulu sebelum PCAPdroid mulai, dan hev
  dijalankan lagi begitu capture berakhir (stop, error, atau proses utama mati).
* Syarat ON: V2Ray sedang tersambung dan mode **HEV tunnel** aktif (default).

## Batasan yang diketahui (mode ON)

1. **UDP tidak lewat Xray.** zdtun (mesin PCAPdroid) hanya mem-proxy-kan TCP ke SOCKS5; UDP (termasuk DNS)
   keluar langsung. QUIC otomatis diblokir supaya aplikasi jatuh ke TCP. Mode OFF tidak terpengaruh.
2. **TLS decryption (MITM)** bisa dipakai (butuh PCAPdroid-mitm + CA). Saklar "Decrypted traffic via V2Ray" (default ON): koneksi keluar addon lewat Xray; OFF = langsung keluar. Memakai patch `scripts/zdtun-alt-proxy.patch` pada zdtun. Belum dikompilasi/diuji di HP.
3. Filter per-aplikasi memakai pengaturan **Per-app proxy** v2rayNG (bukan filter PCAPdroid).
4. Mode root dan buka file PCAP milik PCAPdroid tetap tersedia seperti biasa.

## Build di GitHub Actions

1. Push seluruh isi folder ini ke repo (branch `master` atau `main`), atau jalankan manual (workflow_dispatch).
2. Signing: `keystore/testkey.keystore` (alias `testkey`, password `android`, V1+V2+V3 aktif) sudah ada di repo
   dan dipakai otomatis; tidak ada secret yang wajib diisi. Pakai repo **private**: siapa pun yang punya
   file ini bisa menandatangani APK atas nama kunci yang sama. Untuk keystore lain tanpa meng-commit-nya,
   isi secret `APP_KEYSTORE_BASE64`, `APP_KEYSTORE_PASSWORD`, `APP_KEYSTORE_ALIAS`, `APP_KEY_PASSWORD`.
3. APK ada di artifact `V2RayMe-arm64-v8a`.

## Mengganti nama aplikasi

`V2rayNG/app/src/main/res/values/strings.xml` -> `app_name` (sekarang "V2Ray Me").

## Yang belum ada

* Filter AdGuard (kelola sumber URL, filter kustom, ON/OFF per sumber): tahap berikutnya,
  setelah build dasar ini terbukti jalan.
