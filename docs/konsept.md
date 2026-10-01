# DJI Mini 4 Pro: Jestle Kontrol ve Otonom Takip Konsepti

> **Durum:** Teorik / konsept aşaması. Henüz kod yok.
> **Son güncelleme:** 2026-10-01
>
> Bu doküman, proje fikrini şekillendiren soru-cevap konuşmalarında alınan kararların
> toplandığı yerdir. Her yeni karar buraya ve en alttaki **Karar Günlüğü**'ne işlenir.

---

## 1. Amaç

Kullanıcı hareket/aksiyon halindeyken drone'un onu **otomatik takip etmesi** ve kullanıcının
drone'a **telefona/kumandaya dokunmadan, vücut ve el jestleriyle** komut verebilmesi.

- Komut verilirken kullanıcı **sabit durur**. Aksiyon sırasında komut verilmez, sadece takip edilir.
- Güvenlik en yüksek önceliktir: drone'un mevcut sensörlerinden mümkün olan en yüksek
  düzeyde faydalanılmalıdır.

---

## 2. Donanım ve Platform

| Bileşen | Seçim |
|---|---|
| Drone | DJI Mini 4 Pro |
| Kumanda | DJI RC-N2 (telefon kabloyla bağlanır) |
| Telefon | Android |
| SDK | DJI Mobile SDK v5 (MSDK) |
| Dil | Kotlin |
| Görüntü işleme | MediaPipe (Pose Landmarker, Hand Landmarker / Gesture Recognizer) |
| Geri bildirim | Bluetooth kulaklık (sesli bildirim / TTS) |
| Akıllı saat | Şimdilik **kapsam dışı** (Wear OS ileride ek komut kanalı olabilir) |

Kullanım sırasında telefon + RC-N2 kullanıcının üzerinde taşınır (göğüs/sırt çantası).

---

## 3. Teknik Fizibilite Özeti

| Soru | Cevap |
|---|---|
| Kumandasız, sadece telefonla kontrol | ❌ Mini 4 Pro'nun Wi-Fi'ı sadece dosya aktarımı (QuickTransfer) için. Uçuş O4 üzerinden, kumanda şart. |
| RC-N2 + telefon + DJI Fly | ✅ Standart kullanım. |
| RC-N2 + Android + kendi uygulamamız (MSDK) | ✅ Canlı görüntü, Virtual Stick, gimbal, kamera, telemetri erişilebilir. |
| DJI RC 2 (ekranlı kumanda) + kendi uygulamamız | ❌ Kapalı sistem. |
| DJI Fly'ın ActiveTrack / engelden kaçınma kodunu kullanmak | ❌ DJI Fly açık kaynak değil. Ayrıca bu özellikler uygulamada değil **drone'un firmware'inde** çalışıyor. Tersine mühendislik yasal, teknik ve güvenlik açısından reddedildi. |
| Virtual Stick modunda DJI'ın otomatik engelden kaçınması | ⚠️ DJI MSDK dokümanına göre resmi olarak **sadece kurumsal modellerde** (M300/M350 RTK, M30, Mavic 3E/3M) destekleniyor. **Mini 4 Pro listede yok.** Pratikte davranışı test ile doğrulanacak. |

**Sonuç:** Proje teknik olarak mümkün. En büyük risk, kendi uygulamamızla uçarken DJI'ın
otomatik engelden kaçınmasının devrede olmaması. Bu yüzden kendi güvenlik katmanımızı
yazıyoruz (bkz. §7).

---

## 4. Genel Mimari

### 4.1 Veri akışı

```
Drone kamerası ──(O4)──► RC-N2 ──(USB)──► Android telefon
                                              │
                         ┌────────────────────┼─────────────────────┐
                         ▼                    ▼                     ▼
                   Jest tanıma          Görsel takip          Telefon GPS
                   (MediaPipe)          (kişi konumu)         (kullanıcı konumu)
                         │                    │                     │
                         └──────► İstenen hız / gimbal komutu ◄─────┘
                                              │
                                    [ GÜVENLİK VALFİ ] ◄── PerceptionManager
                                              │            (360° engel mesafeleri)
                                              ▼
Drone ◄──(O4)── RC-N2 ◄──────────── Virtual Stick komutları
```

### 4.2 Katmanlar

```
Katman 0 – DJI uçuş kontrolcüsü  → RTH, geofence, (varsa) kendi freni. ASLA kapatılmaz.
Katman 1 – Güvenlik valfi        → engel mesafesi, hız limiti, batarya, veri tazeliği
Katman 2 – Takip                 → görsel takip + GPS yedeği + engel aşma manevrası
Katman 3 – Komutlar              → kamera jestleri
```

### 4.3 Öncelik sırası

```
Güvenlik valfi  >  ACİL DURDUR jesti  >  Diğer jestler  >  Takip
```

### 4.4 Altın kural

> **Emin olmadığında drone havada asılı kalır.**
> Takip kayıpsa, komut belirsizse, sensör verisi eskiyse ya da uygulama çöktüyse: hover.

---

## 5. Komut Sistemi (Jestler)

### 5.1 Komut akışı

```
[Serbest mod] ──"uyandırma" jesti (1 sn tut)──► [Komut modu]
                                                   │
                                     jest algılandı + 1 sn tutuldu
                                                   │
                                   Sesli geri bildirim: "Komut: X" ──► uygula
                                                   │
                                  5-10 sn komut yoksa ──► [Serbest mod]

[ACİL DURDUR jesti] → her modda geçerli → drone havada asılı kalır
```

### 5.2 Kurallar

- **Uyandırma jesti + tutma süresi:** Aksiyon sırasındaki rastgele kol hareketleri komut sayılmaz.
- **Operatör kilidi:** Uyandırma jestini yapan kişi oturumun sahibidir. Kadrajdaki diğer kişilerin jestleri yok sayılır.
- **İki adımlı onay:** Geri dönüşü olan komutlar (ör. iniş) "komut, ardından onay jesti" şeklinde verilir.
- **Tek adımlı güvenli komutlar:** "Dur / havada kal" anında çalışır, onay istemez.
- **Mesafe stratejisi:** Uzakta vücut pozu jestleri (10-15 m+), yakında el/parmak jestleri kullanılır.
  Önce vücut pozu modeli kişiyi bulur, sonra **el bölgesi kırpılıp** el modeline verilir. Bu sayede el jestlerinin okunabildiği mesafe artar.

### 5.3 Komut seti (taslak, tümü kapsamda)

| Jest | Komut | Not |
|---|---|---|
| İki kol yukarı "Y" | Uyandır / komut modu | 1 sn tut |
| Kollar göğüste "X" | **ACİL: havada kal** | Her zaman aktif, uyandırma gerekmez |
| Tek kol sağa / sola | O yöne kay / etrafımda dön (takip açısını değiştir) | |
| Avuç ileri, itme | Uzaklaş (**takip mesafesini artır**) | |
| Avuç kendine, çekme | Yaklaş (**takip mesafesini azalt**) | |
| El yukarı / aşağı | Yüksel / alçal (**takip irtifasını değiştir**) | |
| Başparmak yukarı | Onay | Kritik komutlar için |
| _Belirlenecek_ | İniş | İki adımlı onay |
| _Belirlenecek_ | Kayıt başlat / durdur | Kamera komutu |
| _Belirlenecek_ | Fotoğraf çek | Kamera komutu |
| _Belirlenecek_ | Orbit (etrafımda tam tur) | |

### 5.4 Takip ayarlarının jestle değiştirilmesi

Takip mesafesi, irtifa ve açı **sabit değildir**, jestlerle ayarlanır:

- Yaklaş/uzaklaş, yüksel/alçal ve sağa/sola jestleri takip modundayken **takip ofsetini** değiştirir.
- Her jest bir adım uygular (öneri: ±2 m / ±15°).
- Ayarlar güvenli sınırlar içinde tutulur (öneri: min. mesafe 4 m, min. irtifa 3 m; kesin değerler açık soru).
- Her değişiklik sesli olarak doğrulanır ("Mesafe 10 metre").

### 5.5 Geri bildirim

- **Birincil:** Bluetooth kulaklıktan sesli bildirim.
- **Yedek:** Drone'un "onay hareketi" (ör. küçük sağ-sol yaw salınımı).

---

## 6. Takip

- **Birincil:** Drone kamera görüntüsünde operatörün tespiti, ardından drone ve gimbal ile kişiyi kadraj merkezinde tutma (PID kontrol).
- **Yedek:** Kullanıcının üzerindeki **telefonun GPS konumu** (~3-5 m doğruluk). Görsel takip kaybolduğunda kaba takip sağlar.
- **Yeniden tanıma (re-ID):** Görsel temas geri geldiğinde operatör; kıyafet rengi, boy ve GPS konumuyla örtüşme gibi ipuçlarıyla doğrulanır. Gerekirse kullanıcı **uyandırma jestiyle** kendini tekrar tanıtır.
- **Gimbal:** İrtifa farkı ve yatay mesafeye göre eğim açısı hesaplanarak operatör kadrajda tutulur.
- **Gecikme bütçesi:** Görüntü + işlem + komut ≈ 200-300 ms. Yavaş ve orta hızlı takip için yeterli.

---

## 7. Güvenlik: Seçenek A (Kendi Güvenlik Valfimiz)

**Karar:** Ana yol Seçenek A. Sensör verisi alınamazsa Seçenek B'ye (açık alan, yüksek irtifa,
düşük hız) düşülür. B kabul edilebilir ama tercih edilmeyen bir yedek.

### 7.1 Çalışma prensibi

Takip ve jest modülleri drone'a doğrudan komut göndermez. Her komut, saniyede 10-20 kez
çalışan **güvenlik valfinden** geçer. Valfin sorusu şu:
_"Bu yöne bu hızla gidersem, gerekirse zamanında durabilir miyim?"_

### 7.2 Durma mesafesine göre hız limiti

Gecikme (~0,3 sn) ve fren kabiliyeti hesaba katılır. Güvenlik payı 2 m için örnek:

| Gidiş yönünde engel | İzin verilen azami hız |
|---|---|
| 10 m | ~5 m/s |
| 5 m | ~3 m/s |
| 3 m | ~1,5 m/s |
| ≤ 2 m | **0, o yöne hareket yasak** |

### 7.3 Yönsel kesme

Sadece engele doğru olan hız bileşeni kısılır, diğer yönlerdeki hareket serbesttir.
Böylece drone engelin yanından **kayarak** basit bir kaçınma yapar.

### 7.4 Veri tazeliği bekçisi

Sensör verisi 200-300 ms'den eskiyse drone hover'a geçer ve sesli uyarı verilir.

### 7.5 Kademeli güvenli mod (A'dan B'ye)

Sensörler uzun süre veri vermezse (ör. düşük ışık) sistem otomatik olarak B moduna geçer:
hız limiti düşer, takip yalnızca belirlenen minimum irtifanın üstünde sürer. Durum sesli bildirilir.

### 7.6 Bilinen kör noktalar

- **Düşük ışık / karanlık:** Görüş sensörleri çalışmaz (bunu 7.4 ve 7.5 yakalar).
- **İnce dallar, teller, cam:** Geç algılanır ya da hiç algılanmaz. Güvenlik payları yüksek tutulur.
- **Takipte kamera yönü:** Kamera operatöre bakar, drone çoğunlukla geri geri uçar. Ana kamera çarpma yönünü görmez, bu yüzden görsel engel tespiti için ana kameraya güvenilmez.
- **DJI'ın kendi freni:** Virtual Stick'te Mini 4 Pro için çalışıp çalışmadığı test edilecek. Çalışıyorsa ikinci emniyet olur, ama tasarım buna **dayanmaz**.

---

## 8. Engel Aşma: Tırmanma Manevrası

**Karar:** Takip yolu tamamen kapanırsa drone engelin **üstünden tırmanarak** aşar.
Mümkün değilse bekler ve haber verir.

### 8.1 Durum makinesi

```
[TAKİP] ──engel: yol kapalı + yana kayarak aşılamıyor──► [KONTROL]
                                                             │
                     ┌──── ön koşullar sağlanmıyor ──────────┤
                     ▼                                       │ sağlanıyor
              [BEKLE & HABER VER]                            ▼
                     ▲                                  [TIRMAN]
                     │                                       │ ön taraf açıldı + ek pay
                     │                                       ▼
                     ├──── süre/irtifa limiti aşıldı ── [GEÇ] (yavaş ileri)
                     │                                       │ engel geride kaldı
                     │                                       ▼
                     └──── aşağısı belirsiz ─────────── [KONTROLLÜ İNİŞ]
                                                             │ takip irtifasına ulaşıldı
                                                             ▼
                                                          [TAKİP]
```

### 8.2 Ön koşullar (hepsi sağlanmalı)

- Yukarı sensör açık (ağaç, köprü altı ya da kapalı alanda tırmanma yok)
- İrtifa payı var: yasal limit 120 m ve proje limiti (öneri: başlangıca göre +30 m) aşılmaz
- Sensör verisi taze ve ışık yeterli
- Batarya tırmanma, geçiş ve dönüş için yeterli

### 8.3 Manevra adımları

1. **Tırman:** Yatay hareket durur, yaklaşık 1 m/s ile yükselinir. Ön taraf "açık" okunduktan sonra **+3-5 m ek pay** tırmanılır, çünkü sensörlerin dikey görüş açısı sınırlı ve ağaç tepeleri düzensiz.
2. **Geç:** Yavaş ileri hareket edilir. Aşağı sensör engel gösterdiği sürece irtifa korunur. Gimbal aşağı eğilerek operatör kadrajda tutulmaya çalışılır.
3. **İn:** Aşağı ve yatay sensörler temizse takip irtifasına **daha da yavaş** inilir. İniş, tırmanmadan daha riskli bir aşamadır.
4. Bu sırada görsel temas kaybolursa **GPS yedeği** devreye girer (bkz. §6).

---

## 9. Sesli Bildirimler (Taslak)

| Durum | Mesaj |
|---|---|
| Komut alındı | "Komut: {komut}" |
| Ayar değişti | "Mesafe {x} metre" / "İrtifa {x} metre" |
| Engel, tırmanma başladı | "Engel var, tırmanıyorum" |
| Engel aşıldı | "Engel aşıldı, iniyorum" |
| Görsel takip kayıp | "Seni göremiyorum, GPS ile geliyorum" |
| Tırmanma mümkün değil | "Tırmanamıyorum, burada bekliyorum" |
| Takip engel nedeniyle durdu | "Engel var, takip durdu" |
| Sensör verisi yok | "Sensör verisi yok, durdum" |
| Güvenli moda geçiş | "Sensörler kapalı, güvenli moda geçildi" |

---

## 10. Geliştirme ve Test Yol Haritası

Her aşama bir öncekinin başarısına bağlıdır. **Aşama 1'in sonucu projenin yolunu belirler.**

| # | Aşama | Risk | Amaç |
|---|---|---|---|
| 1 | **Kader testi: yerde sensör verisi** (motorlar kapalı) | Yok | PerceptionManager, Mini 4 Pro'da engel mesafesi veriyor mu? Drone'un etrafında kartonla dolaşılır. |
| 2 | Jest tanımayı **kayıtlı videolarda** geliştirme | Yok | DJI Fly ile çekilmiş jest videoları üzerinde MediaPipe kurallarının ayarlanması |
| 3 | Valf simülasyonu | Yok | Kaydedilmiş sensör verisiyle valf mantığını masa başında test |
| 4 | Yavaş uçuşta fren testi | Düşük | Açık alanda ~1 m/s ile karton kutuya: valf durduruyor mu? DJI freni devrede mi? |
| 5 | Canlı jest tanıma (uçuşsuz komut) | Düşük | Canlı yayında jestler algılanıp sadece ekrana/kulaklığa yazılır |
| 6 | Sadece **yaw** ile takip | Düşük | Drone yerinde dönerek operatörü kadrajda tutar |
| 7 | Tam takip + jest komutları | Orta | Valf aktif, açık alanda |
| 8 | Tırmanma manevrası, **elle tetikleme** | Orta | Açık alanda tek ve büyük bir engel önünde, uygulamadaki butonla |
| 9 | Tırmanma manevrası, otomatik tetikleme | Orta | |
| 10 | GPS yedeği ve yeniden tanıma | Orta | Görsel kayıp senaryoları |

- Aşama 1'de **veri geliyorsa:** Seçenek A ile devam.
- Aşama 1'de **veri gelmiyorsa:** Proje Seçenek B kurallarıyla sürer (açık alan, yüksek irtifa, düşük hız).

---

## 11. Riskler

| Risk | Etki | Önlem |
|---|---|---|
| Mini 4 Pro, SDK'ya engel verisi vermiyor | Yüksek | Aşama 1'de erken tespit, B moduna düşme |
| Virtual Stick'te DJI freni yok | Yüksek | Kendi valfimiz (§7) |
| Jest yanlış algılama | Orta | Uyandırma jesti, tutma süresi, iki adımlı onay |
| Kadrajda birden fazla kişi | Orta | Operatör kilidi, re-ID |
| Düşük ışıkta sensör körlüğü | Orta | Veri tazeliği bekçisi, B moduna otomatik geçiş |
| İnce dal / tel | Orta | Yüksek güvenlik payı, tırmanmada ek pay |
| ~200-300 ms gecikme | Düşük-Orta | Hız limitleri |
| Rüzgâr (249 g gövde) | Orta | Rüzgâr uyarısında takibi sınırla (detaylandırılacak) |
| Yasal: görüş hattı (SHGM), kalabalık üstü uçuş | Orta | Kullanım kuralları, kalabalık tespitinde uyarı (detaylandırılacak) |

---

## 12. Açık Sorular

- [ ] Varsayılan takip mesafesi / irtifa / açı ve min.-maks. sınırlar
- [ ] İniş, kayıt başlat/durdur, fotoğraf ve orbit için jest atamaları
- [ ] Jest ayar adımları (±2 m / ±15° önerisi kesinleşmeli)
- [ ] Tırmanma için proje irtifa limiti (+30 m önerisi)
- [ ] Sesli bildirim dili ve detay seviyesi
- [ ] Batarya eşikleri (takibi bitirme / eve dönüş)
- [ ] Rüzgâr ve kalabalık durumlarında davranış

---

## 13. Karar Günlüğü

| Tarih | Karar |
|---|---|
| 2026-10-01 | Platform: Android + MSDK v5 + RC-N2 |
| 2026-10-01 | Komut verirken kullanıcı sabit durur, kamera jestleri ana komut kanalı |
| 2026-10-01 | Wear OS şimdilik kapsam dışı |
| 2026-10-01 | Komutlar arası davranış: drone kullanıcıyı **takip eder** |
| 2026-10-01 | Önerilen komut setinin **tamamı** kapsamda |
| 2026-10-01 | Geri bildirim: Bluetooth kulaklıktan sesli |
| 2026-10-01 | DJI Fly / firmware tersine mühendisliği **reddedildi** |
| 2026-10-01 | Güvenlik: **Seçenek A** (kendi güvenlik valfimiz). B modu kabul edilebilir yedek. |
| 2026-10-01 | Yol tamamen kapanırsa: **tırmanarak aşma**. Mümkün değilse bekle ve haber ver. |
| 2026-10-01 | Takip mesafesi / irtifa / açı **jestlerle ayarlanabilir** |

---

## 14. Kaynaklar

- [DJI MSDK: IVirtualStickManager](https://developer.dji.com/api-reference-v5/android-api/Components/IVirtualStickManager/IVirtualStickManager.html)
- [DJI Mobile SDK dokümantasyonu](https://developer.dji.com/doc/mobile-sdk-tutorial/en/)
- [DroneDJ: Mini 4 Pro MSDK desteği](https://dronedj.com/2025/03/21/dji-mini-4-pro-msdk/)
- [GitHub: Mobile-SDK-Android-V5, PerceptionInfo sorunu #618](https://github.com/dji-sdk/Mobile-SDK-Android-V5/issues/618)
- [GitHub: Mobile-SDK-Android-V5, Mini 4 Pro Virtual Stick sorusu #670](https://github.com/dji-sdk/Mobile-SDK-Android-V5/issues/670)
- [MediaPipe](https://ai.google.dev/edge/mediapipe/solutions/guide)
