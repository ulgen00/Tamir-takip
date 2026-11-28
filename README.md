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

`android/` klasöründe, GitHub Pages'teki uygulamayı açan bir Android kabuğu var. Uygulama her
açılışta sitenin güncel halini yüklediği için sitede yapılan değişiklikler uygulamaya da kendiliğinden
gelir. Kayıtlar telefonda (WebView depolamasında) kalır; tarayıcıdaki kayıtlarla ortak değildir.

- Hazır APK: `apk/cep-atolyesi.apk` (Pages açıksa: `https://ulgen00.github.io/Tamir-takip/apk/cep-atolyesi.apk`)
- Kamera izni (barkod), galeriden fotoğraf seçme, `tel:` ile arama ve geri tuşu (önce açık ekranı/menüyü
  kapatır) Android tarafında ayarlı. İnternet yoksa "bağlantı yok" ekranı açılır.

Derlemek için (Android SDK, platform 35 ve Java 17+ gerekir):

```
cd android
echo "sdk.dir=/android/sdk/yolu" > local.properties
gradle assembleRelease        # çıktı: app/build/outputs/apk/release/app-release.apk
```

İmza: `android/keystore.properties` dosyası (depoya eklenmez) `storeFile`, `storePassword`, `keyAlias`,
`keyPassword` içerir. Dosya yoksa APK hata ayıklama anahtarıyla imzalanır. Güncellemelerin telefona
kurulabilmesi için hep aynı anahtar kullanılmalı. Simgeler `python3 tools/make_icons.py` ile üretilir.
