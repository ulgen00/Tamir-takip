# Tamir Defteri

Telefon tamircileri için cihaz takibi, kasa ve özet uygulaması (tek sayfa, `index.html`).
Veriler telefonun tarayıcısında (localStorage) saklanır.

## IMEI'den otomatik marka/model

Yeni cihaz kaydında IMEI yazıldığında ya da barkod tarandığında, IMEI'nin ilk 8 hanesi (TAC)
ile cihazın marka/modeli otomatik olarak "Cihaz (marka / model)" alanına yazılır.

Arama sırası:
1. **Kendi kayıtlarınız** – daha önce aynı TAC ile kaydettiğiniz model adı (düzeltmeleriniz önceliklidir).
2. **TAC veritabanı** – `tac/` klasörü (~275 bin TAC, 467 parça dosya). Sadece gereken küçük parça
   (en fazla ~70 KB) indirilir.

Veritabanı önce uygulamanın yanındaki `tac/` klasöründen, bulunamazsa
`cdn.jsdelivr.net/gh/ulgen00/Tamir-takip@main/tac/` adresinden okunur
(bu yedek adres yalnızca depo herkese açıksa çalışır).
Bu yüzden `index.html` ile `tac/` klasörünü birlikte yayınlayın (ör. GitHub Pages).

Alan elle doldurulmuşsa üzerine yazılmaz; bulunan model mesajda gösterilir.

### Veritabanını güncellemek

```
python3 tools/build_tac.py            # kaynaktan indirip tac/ klasörünü yeniden oluşturur
python3 tools/build_tac.py dosya.csv  # yerel CSV'den
```

Veri kaynağı: [MoazEb/tac-database](https://github.com/MoazEb/tac-database) (MIT lisansı, bkz. `tac/LICENSE`).

## e-Devlet IMEI kayıt sorgusu

IMEI 15 hane ve geçerli olduğunda uygulama, e-Devlet'teki BTK "IMEI Sorgulama" hizmetini otomatik
sorgular ve IMEI kutusunun altında sonucu gösterir: **Kayıtlı**, **Kayıtsız**, **Kapalı**
(kayıp/çalıntı/kapatılmış vb.) etiketi, e-Devlet'in durum metni ve sorgu tarihi. Sonuç cihaz kaydıyla
birlikte saklanır ve listede etiket olarak görünür. e-Devlet doğrulama kodu isterse kodun resmi ve bir
kutucuk açılır; kodu yazıp **Gönder**'e basarsınız.

Tarayıcı e-Devlet'e doğrudan bağlanamadığı için sorgu küçük bir Cloudflare Worker üzerinden yapılır
(`worker/imei-worker.js`). Kurulum:

1. Cloudflare panelinde **Workers & Pages → Create → Worker** ile yeni bir Worker açın
   (ör. adı `cep-atolyesi-imei`), **Edit code** ile `worker/imei-worker.js` içeriğini yapıştırıp
   **Deploy** deyin.
2. Worker'ın adresini (`https://cep-atolyesi-imei.<hesap>.workers.dev`) `index.html` içindeki
   `const EDV_API` satırına yazın (şu an: `https://cep-atolyesi-imei.hvt8b9y5zt.workers.dev`).
3. Uygulama `https://ulgen00.github.io` dışında bir adreste yayınlanıyorsa, Worker ayarlarında
   `ALLOWED_ORIGINS` değişkenine o adresi ekleyin (virgülle ayırarak birden fazla yazılabilir).

`EDV_API` boşken bu özellik görünmez. Sonuç bilgilendirme amaçlıdır; e-Devlet'in kendi notuna göre
kesin kayıt değildir.

## Android uygulaması (APK)

`android/` klasöründe Kotlin + Jetpack Compose ile yazılmış **yerel** Android uygulaması var (sürüm 2.1).
Siteye bağlı değildir; internetsiz çalışır (internet yalnızca e-Devlet IMEI sorgusu için gerekir).
Geliştirmeye bu uygulama üzerinden devam ediliyor; iPhone için web sürümü (`index.html`) kullanılmaya devam ediyor.

- Hazır APK: `apk/cep-atolyesi.apk` (Pages açıksa: `https://ulgen00.github.io/Tamir-takip/apk/cep-atolyesi.apk`)
- Sayfalar: Tamir, Kasa, Özet, IMEI Sorgula, Ayarlar. Soldan menü ya da alt çubuk (Ayarlar'dan), güneş/ay ile tema.
- IMEI: yazınca/yapıştırınca marka/model (önce dükkanın kendi kayıtları, sonra uygulama içindeki `tac/`
  veritabanı), 15 hane olunca e-Devlet kaydı (Worker üzerinden, `X-Cep-Atolyesi: android` başlığıyla).
- Barkod: CameraX + Google ML Kit (model uygulamanın içinde), kamera ve galeriden fotoğraf.
- Kayıtlar telefonda tek bir JSON dosyasında; biçim web sürümüyle aynı. Ayarlar > Yedekleme ile dışa/içe aktarılır.
- Etiket yazdırma: ağdaki termal etiket yazıcısına (Xprinter, Gprinter vb., TSPL, ham TCP 9100) cihaz formundaki
  "Etiket yazdır" düğmesiyle ya da yeni kayıtta otomatik. Etiket (varsayılan 60x30 mm) uygulamada resim olarak çizilir
  (Türkçe karakterler için) ve BITMAP komutuyla gönderilir: dükkan adı, tarih, müşteri, telefon, model, arıza,
  kayıt no ve barkodu. Yazıcı IP'si, boyut, 180° çevirme ve ters renk Ayarlar'da (`Label.kt`).

Kod düzeni (`android/app/src/main/java/com/cepatolyesi/app/`):
`Model.kt` (kayıtlar), `Label.kt` (etiket), `Util.kt` (IMEI, telefon, para), `Lookup.kt` (TAC ve e-Devlet), `AppState.kt`
(ayarlar ve IMEI mantığı), `ui/` (ekranlar).

Derleme ve test (Android SDK platform 35, Java 17+):

```
cd android
echo "sdk.dir=/android/sdk/yolu" > local.properties
gradle testDebugUnitTest      # birim + ekran testleri; ekran görüntüleri app/build/ekranlar/
gradle assembleRelease        # çıktı: app/build/outputs/apk/release/app-release.apk
```

Ekran testleri uygulamayı bilgisayarda Android taklidiyle (Robolectric) çalıştırır: cihaz ekleme, zorunlu alanlar,
IMEI'den model, teslim, kasa, sekmeler, tema ve gerçek bir e-Devlet sorgusu.

İmza: `android/keystore.properties` dosyası (depoya eklenmez) `storeFile`, `storePassword`, `keyAlias`,
`keyPassword` içerir. Dosya yoksa APK hata ayıklama anahtarıyla imzalanır. Güncellemelerin telefona
kurulabilmesi için hep aynı anahtar kullanılmalı. Simgeler `python3 tools/make_icons.py` ile üretilir.
