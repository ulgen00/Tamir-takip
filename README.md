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
