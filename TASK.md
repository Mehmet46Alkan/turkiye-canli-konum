# Türkiye Canlı Konum — APK build planı

GOAL: Türkiye haritası üzerinde canlı GPS konumu gösteren APK — GitHub Actions bulutta derlenecek, indirme linki verilecek.

- [x] GitHub token kaydedildi (Mehmet46Alkan)
- [x] UI önizleme (3 tasarım) sunuldu — kullanıcı "devam et" dedi → tasarım 1 (Kırmızı Bayrak) seçildi
- [x] Proje dosyaları yazıldı (WebView + Leaflet, MainActivity GPS köprüsü, ikon, dark mode)
- [x] JS sözdizimi + XML denge kontrolü geçti
- [x] GitHub'a push (Mehmet46Alkan/turkiye-canli-konum) — Actions run 39029193294 in_progress
- [x] Build başarılı → APK indirme linki verildi (release apk-2)

DURUM: TAMAMLANDI. APK: https://github.com/Mehmet46Alkan/turkiye-canli-konum/releases/download/apk-2/app-debug.apk

Mimari: WebView (assets/index.html) + Leaflet/OSM haritası, Türkiye merkezli (39,35 zoom6).
MainActivity: LocationManager (GPS+Network) → evaluateJavascript ile JS'e iter; GSAT uydu sayısı GnssStatus'tan.
JS: ters geokod Nominatim, iz + doğruluk dairesi, hız/yön/isabet kartı, durum sayfaları (izin/loading/error/offline).

NOW: Actions build sonucunu bekliyorum (run 39029193294).
