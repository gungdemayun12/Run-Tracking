# Halaman Reset Password RunTrack

Halaman web ini menggantikan tampilan bawaan Firebase saat pengguna menekan
tautan reset password dari email. Tampilannya mengikuti brand RunTrack
(dark hero, kartu putih, aksen oranye) dan langsung mengatur kata sandi lewat
Firebase Authentication REST API.

## Isi folder

```
web/
└─ public/
   ├─ index.html                      halaman pembuka
   ├─ 404.html                        halaman error
   └─ reset-password/index.html       halaman reset password (utama)

firebase.json                        konfigurasi Hosting + Firestore (root project)
.firebaserc                          project default: running-track-b0911
```

## 1. Coba tampilan dulu (pratinjau)

Buka `web/public/reset-password/index.html` di browser. Karena tidak ada
`oobCode` di URL, halaman otomatis masuk **mode pratinjau**: form tampil penuh
dan tersedia tautan kecil untuk melihat tampilan **berhasil** dan
**tautan kedaluwarsa**.

Untuk pratinjau lewat server lokal:

```bash
cd web/public
npx serve .
# buka http://localhost:3000/reset-password/
```

## 2. Deploy ke Firebase Hosting

Prasyarat: Node.js dan Firebase CLI.

```bash
npm install -g firebase-tools
firebase login
# jalankan dari folder root project (yang berisi firebase.json)
firebase deploy --only firestore:rules,hosting --project running-track-b0911
```

URL halaman setelah deploy:

```
https://running-track-b0911.web.app/reset-password/
```

Jika project belum punya site Hosting, buat dulu di Firebase Console
(Build → Hosting → Get started) atau jalankan `firebase hosting:sites:create`.

## 3. Arahkan email reset ke halaman ini (wajib, sekali saja)

1. Buka [Firebase Console](https://console.firebase.google.com/project/running-track-b0911/authentication/emails).
2. Tab **Templates** → pilih **Password reset**.
3. Klik ikon **pensil / Edit**.
4. Pada **Customize action URL** (atau *Action URL*), isi:
   ```
   https://running-track-b0911.web.app/reset-password/
   ```
5. Simpan.

Setelah langkah ini, semua email reset baru akan membuka halaman RunTrack,
bukan halaman polos bawaan Firebase. Email yang sudah terlanjur dikirim
sebelum perubahan tetap memakai halaman lama.

> Domain `*.web.app` sudah termasuk authorized domain Firebase, jadi tidak
> perlu menambahkan domain lagi.

## 4. Pastikan email benar-benar terkirim

Email reset dikirim oleh Firebase Authentication, bukan oleh halaman web.

1. Firebase Console → **Authentication** → **Sign-in method**:
   pastikan **Email/Password** aktif.
2. Di aplikasi RunTrack: **Masuk → Lupa kata sandi?** → masukkan email akun →
   **Kirim tautan reset**.
3. Halaman akan menampilkan status **Email terkirim** bila Firebase menerima
   permintaan. Jika email tidak terlihat, cek folder **Spam/Promosi**.
4. Pengirim default: `noreply@running-track-b0911.firebaseapp.com`.
   Bisa diganti di **Authentication → Templates → SMTP settings** (butuh
   domain/SMTP kustom pada paket berbayar).

## 5. Cara kerja halaman

- Membaca parameter `mode`, `oobCode`, dan `apiKey` dari URL email.
- Memverifikasi kode ke:
  `POST https://identitytoolkit.googleapis.com/v1/accounts:resetPassword`
  dengan body `{ "oobCode": "..." }` → menampilkan email akun.
- Menyimpan kata sandi baru dengan endpoint yang sama dan body
  `{ "oobCode": "...", "newPassword": "..." }`.
- Menangani kode kedaluwarsa/terpakai, kata sandi lemah, dan batas percobaan.
- Tombol **Buka aplikasi RunTrack** memakai deep link
  `intent://open#Intent;scheme=runtrack;package=com.example.runtrack;end`
  (didukung Chrome di Android).

Kunci API fallback ada di dalam file. Halaman tetap memprioritaskan `apiKey`
yang dikirim Firebase di URL email. API key Firebase memang bersifat publik;
keamanan dijaga oleh `oobCode` sekali pakai dan aturan Firestore.

## 6. Sesi login aplikasi

Perbaikan sesi ada di kode Android, bukan di halaman web:

- `SessionManager` menyimpan waktu aktif terakhir.
- Sesi bertahan selama pengguna masih membuka aplikasi; baru kedaluwarsa
  setelah **30 hari tanpa aktivitas**.
- Logout hanya terjadi lewat tombol **Keluar** di Profil (dengan konfirmasi).
- Back dari layar utama tidak lagi menghapus sesi.
