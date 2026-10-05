# Android Projesi

| Modül | Amaç | Durum |
|---|---|---|
| `sensortest` | **Aşama 1: Kader testi.** Mini 4 Pro'nun engel sensörü verisini SDK'ya verip vermediğini ölçer. | Hazır, sahada denenecek |

> ⚠️ `sensortest` drone'a **hiçbir uçuş komutu göndermez**. Sadece okur.

---

## 1. Kurulum (bir kerelik)

1. **Android Studio**'yu kur (Ladybug veya daha yeni bir sürüm).
2. Android Studio'da **File → Open** ile repodaki `android/` klasörünü aç (repo kökünü değil).
3. `android/local.properties.example` dosyasını `android/local.properties` adıyla kopyala ve doldur:
   ```properties
   dji.appKey=<developer.dji.com'daki App Key>
   dji.packageName=<App Key'i oluştururken girdiğin paket adı>
   ```
   - `local.properties` git'e girmez, anahtarın repoda görünmez.
   - **Paket adı birebir aynı olmalı.** Aynı değilse SDK kaydı "App Key geçersiz" hatası verir.
4. Telefonda **Geliştirici seçenekleri → USB hata ayıklama**'yı aç.
5. Telefonu bilgisayara bağla ve Android Studio'da **Run ▶** ile `sensortest`'i yükle.

**İlk açılışta internet şart:** SDK, App Key'i DJI sunucusunda doğrular. Sonraki açılışlarda internet gerekmez.

**İpucu:** Telefonun USB girişi kumandaya bağlı olacağı için test sırasında bilgisayara kabloyla bağlı kalamazsın. Uygulama kendi başına çalışır.
Log görmek istersen Android 11+ telefonlarda **Kablosuz hata ayıklama** kullanılabilir.

---

## 2. Kader testi prosedürü

### Güvenlik
- Test **yerde** yapılır. **Pervaneleri sök**, ya da en azından motorları hiç çalıştırma.
- Uygulama motorlar çalışırsa kırmızı uyarı gösterir.

### Hazırlık
1. **DJI Fly'ı tamamen kapat.** Kumandayı aynı anda yalnızca bir uygulama kullanabilir.
2. Kumandayı ve drone'u aç, eşleşmelerini bekle.
3. Telefonu kumandaya bağla. Android hangi uygulamanın açılacağını sorarsa **Sensör Testi**'ni seç.
4. Drone'u **açık bir alana**, etrafında 3 m boşluk olacak şekilde yere koy. İyi ışık olmalı (görüş sensörleri karanlıkta çalışmaz).

### Adımlar

| # | Ne yapılacak | Ekranda bakılacak yer |
|---|---|---|
| 1 | Uygulama açılınca bekle | `SDK: kayıtlı ✅`, `Ürün: DJI_MINI_4_PRO`, `Motorlar: kapalı` |
| 2 | Birkaç saniye bekle | **`ENGEL VERİSİ: GELİYOR ✅` mı?** Sıklık (Hz), sektör sayısı ve açı aralığı |
| 3 | **"Kaydı başlat"**'a bas | Alttaki dosya adı |
| 4 | **Yön testi:** Büyük bir kartonu drone'un **burnunun** 1 m önünde tut, 10 sn bekle. Sonra sırayla **sağ, arka, sol** | "En yakın" satırındaki **sektör numarası** ve radarda kırmızı/turuncu dilimin yeri |
| 5 | **Mesafe testi:** Metreyle ölçerek kartonu önde **1 m, 2 m, 3 m** mesafeye koy | "En yakın" mesafe ölçülen değere uyuyor mu? |
| 6 | **Yukarı/aşağı:** Kartonu drone'un ~50 cm üstünde tut | "Yukarı" değeri |
| 7 | **Kaydı durdur**, sonra **Kaydı paylaş** ile CSV dosyasını kendine gönder | |

### Veri gelmezse
Drone yerdeyken ve motorlar kapalıyken sensörler veri göndermiyor olabilir. O zaman:
1. Pervaneleri tak, uygulama açıkken drone'u **kumandanın çubuklarıyla elle** 2 m'ye kaldır ve havada beklet. Uygulama yine sadece okur, uçuşu sen yaparsın.
2. Ekranda veri gelip gelmediğine bak, 4. ve 5. adımları havada güvenli mesafeden tekrarla (kartonu drone'a yaklaştırmak yerine drone'u kartona doğru yavaşça yanaştır).

### Bana gönderilecek sonuçlar
```
Engel verisi geliyor mu (yerde / havada):
Sıklık (Hz):
Sektör sayısı ve açı aralığı:
Yön testi -> ön: sektör __, sağ: sektör __, arka: sektör __, sol: sektör __
Mesafe testi -> 1 m: __, 2 m: __, 3 m: __
Yukarı değeri (50 cm):
Çalışan sensörler satırı:
+ CSV dosyası
```

---

## 3. Teknik notlar

- **SDK:** DJI Mobile SDK v5 **5.18.0** (`com.dji:dji-sdk-v5-aircraft`).
- **Veri kaynağı:** `PerceptionManager.addObstacleDataListener` ve `addPerceptionInformationListener`.
- **Birim:** Engel mesafeleri **milimetre** (DJI'ın UX SDK'sı da `/ 1000` ile metreye çeviriyor).
- **Sektör yönü:** 0. sektörün drone'un önü olduğu ve saat yönünde ilerlediği **varsayılıyor**. Yön testi bunu doğrulayacak.
- **Geçersiz değerler:** 0, negatif ve 60 m üstü değerler "ölçüm yok" sayılıyor. Ham değerler ekranda ve CSV'de olduğu gibi görünür.
- **CSV biçimi:** `elapsed_ms,type,angle_interval_deg,up_mm,down_mm,horizontal_mm,info`. `horizontal_mm` sektör mesafelerini `;` ile ayırır. Bu kayıtlar Aşama 3'teki güvenlik valfi simülasyonunda kullanılacak.
- **Doğrulama durumu:** Kod, MSDK 5.18.0'ın gerçek sınıflarına karşı derlenip tip kontrolünden geçti. Tam Android derlemesi (APK) ve cihazda çalıştırma **henüz yapılmadı**. İlk derlemede çıkabilecek hataları bana iletmen yeterli.
