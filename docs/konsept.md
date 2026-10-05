# DJI Mini 4 Pro: Jestle Kontrol ve Otonom Takip Konsepti

> **Durum:** Konsept v1 tamamlandı, tüm tasarım soruları kapandı. Henüz kod yok.
> Sıradaki adım: Saha testleriyle doğrulanacak maddeler (§14) ve Aşama 1 (§12).
> **Son güncelleme:** 2026-10-05
>
> Bu doküman, proje fikrini şekillendiren soru-cevap konuşmalarında alınan kararların
> toplandığı yerdir. Her yeni karar buraya ve en alttaki **Karar Günlüğü**'ne işlenir.

---

## 1. Amaç

Kullanıcı hareket/aksiyon halindeyken drone'un onu **otomatik takip etmesi** ve kullanıcının
drone'a **telefona/kumandaya dokunmadan, vücut jestleriyle** komut verebilmesi.

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
| Görüntü işleme | MediaPipe Pose Landmarker (v1). El modelleri v2+ için ayrıldı. |
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
yazıyoruz (bkz. §8).

---

## 4. Genel Mimari

### 4.1 Veri akışı

```
Drone kamerası ──(O4)──► RC-N2 ──(USB)──► Android telefon
                                              │
                         ┌────────────────────┼─────────────────────┐
                         ▼                    ▼                     ▼
                   Jest tanıma          Görsel takip          Telefon GPS
                   (MediaPipe Pose)          (kişi konumu)         (kullanıcı konumu)
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

### 5.1 Tasarım ilkeleri

1. **Tamamı vücut jesti.** Komut anında drone 10-18 m uzakta olabilir. Bu mesafede parmak jestleri okunmaz, kol pozisyonları okunur.
   İlk sürümde **sadece MediaPipe Pose** kullanılır, el modeline gerek yoktur.
2. **Sabit pozlar.** Pozlar belirli bir süre tutulur. Tek hareketli jest orbit ("kement") jestidir.
3. **Kullanıcının bakış açısı.** Drone kullanıcıya baktığı için görüntü aynalanmıştır. Sistem bunu ters çevirir: "sağ kol" her zaman **kullanıcının sağı** demektir.
4. **Tek kollu jestlerde diğer kol aşağıda olmalı.** Kombinasyonlarla karışmayı önler.
5. **Risk derecelendirmesi.** Zararsız komutlar (fotoğraf) kolay tetiklenir, riskli komutlar (iniş) onay ister.
6. **Operatör kilidi.** Uyandırma jestini yapan kişi oturumun sahibidir. Kadrajdaki diğer kişilerin jestleri yok sayılır.

### 5.2 "Y" ve "X" dili

| Jest | Anlam | Duruma göre davranış |
|---|---|---|
| **Y**: iki kol çapraz yukarı | "Evet / dikkat" | Takipte **uyandır**, onay beklerken **onayla**, durdurulmuşken **takibe devam** |
| **X**: kollar göğüste çapraz | "Hayır / dur" | Her durumda **acil dur (havada kal)** ve **iptal** |

### 5.3 Durum akışı

```
          Y              komut              riskli komut
[TAKİP] ────► [KOMUT MODU] ────► uygula    ────► [ONAY BEKLE] ──Y──► uygula
   ▲               │  ▲                              │
   │        5-10 sn│  └── zincirleme komut            └──X / 3 sn──► iptal
   │        komutsuz
   └───────────────┘

Her durumdan ──X──► [DURDURULDU: havada asılı] ──Y──► [TAKİP]
```

- Komut modunda jestler arka arkaya verilebilir (ör. 3 kez "T" yapılırsa mesafe +6 m), her seferinde uyandırma gerekmez.
- 5-10 sn jest gelmezse takip moduna dönülür.

### 5.4 Jest seti (onaylandı)

**Sistem**

| Jest | Komut | Tutma süresi |
|---|---|---|
| İki kol çapraz yukarı **"Y"** | Uyandır / Onay / Takibe devam | 1 sn |
| Kollar göğüste çapraz **"X"** | **Acil dur (havada kal) / İptal** | 0,5 sn, her zaman aktif |

**Hareket ve takip ayarları** (kol, drone'un gitmesi istenen yönü gösterir)

| Jest | Komut | Tutma süresi |
|---|---|---|
| Sağ kol yana yatay | Sağa kay (takip açısı +15°) | 1 sn |
| Sol kol yana yatay | Sola kay (takip açısı −15°) | 1 sn |
| Tek kol düz yukarı | Yüksel (takip irtifası +2 m) | 1 sn |
| Tek kol yana-aşağı çapraz (~45°) | Alçal (takip irtifası −2 m) | 1 sn |
| İki el omuzlarda ("bana gel") | Yaklaş (takip mesafesi −2 m) | 1 sn |
| **T pozu** (iki kol yana yatay, "alan aç") | Uzaklaş (takip mesafesi +2 m) | 1 sn |
| Kol başın üstünde daire ("kement") | Orbit: sağ kolla saat yönünde, sol kolla tersine | 1 tam tur |

**Kamera**

| Jest | Komut | Not |
|---|---|---|
| Eller belde | Fotoğraf | 1 sn. Kulaklıkta **3 sn geri sayım** ("3-2-1"), sonra çekim. Kullanıcı geri sayımda serbestçe poz verir. Kayıt sürerken kare işareti konur (§11.3). |
| İki el başın üstünde ("çatı") | Kayıt başlat / durdur | 1 sn. Sesli durum bildirimi |

**Kritik**

| Jest | Komut | Not |
|---|---|---|
| **Çömel** (diz bük) | İniş | İki adımlı: çömel, ardından "İniş onaylansın mı?" sorusuna **Y**. 3 sn içinde onay yoksa iptal. |

**İniş davranışı:** Drone **bulunduğu yere dikey olarak** iner. Aşağı sensörler zemini kontrol eder.
Zeminin uygunluğundan kullanıcı sorumludur.

### 5.5 Takip ayarları ve sınırları (onaylandı)

Takip mesafesi, irtifa ve açı jestlerle ayarlanır (bkz. §5.4). Her değişiklik sesli olarak doğrulanır ("Mesafe 10 metre").

| Ayar | Varsayılan | Min | Maks | Adım |
|---|---|---|---|---|
| **Mesafe** (yatay) | **8 m** | 4 m | 15 m | ±2 m |
| **İrtifa** (kullanıcıya göre) | **5 m** | 3 m | 10 m | ±2 m |
| **Açı** | **+30°** (sağ-arka) | Tam tur serbest | | ±15° |

- **Açı tanımı:** Kullanıcının hareket yönüne göredir: 0° arka, +90° sağ, 180° ön, −90° sol.
  Kullanıcı durduğunda drone son pozisyonunu korur. Sol-arkaya (−30°) geçmek için dört kez "sola kay" jesti yapılır (+30° → +15° → 0° → −15° → −30°).
- **Gerekçeler:**
  - 8 m / 5 m: Kamera eğimi ~25°, çapraz mesafe ~9,5 m. Jestler çok iyi okunur, valfe fren payı kalır.
  - +30°: Videoda daha sinematik bir görüntü verir ve kullanıcının profili görünür.
  - Min 4 m: Pervane ve kullanıcı için güvenlik payı.
  - Min 3 m irtifa: Baş ve kaldırılmış kolun üstünde kalır.
  - Maks 15 m / 10 m: En uç noktada çapraz mesafe ~18 m olur, jest okunabilirlik sınırına denk gelir.

**Fiziksel sınırlar** (Mini 4 Pro'nun ~82° görüş açısı, 1080p canlı görüntü, 1,75 m boyunda bir kişi için):

| Çapraz mesafe | Kişinin görüntüdeki boyu | Jest tanıma |
|---|---|---|
| 8 m | ~275 px | ✅ Çok iyi |
| 10 m | ~220 px | ✅ İyi |
| 15 m | ~150 px | 🟢 Güvenilir |
| 18 m | ~125 px | 🟡 Sınırda |
| 20 m+ | ≤110 px | ❌ Güvenilmez |

**Kombinasyon kuralları:**
- Drone ile kullanıcı arasındaki **çapraz mesafe ≤ 18 m**.
- **Kamera eğimi ≤ 45°**, yani irtifa mesafeden fazla olamaz. Çok tepeden bakınca kollar gövdeyle üst üste biner.
- Bir jest bu sınırları ihlal edecekse uygulanmaz, sebebi sesli söylenir (ör. 4 m mesafede "yüksel" → "Önce uzaklaş").
- Engel aşma manevrası (§9) sırasındaki geçici irtifa artışı bu kurallardan muaftır, o sırada jest beklenmez.

### 5.6 "Drone'a dön" kuralı

Drone varsayılan olarak kullanıcının arkasında/yanındadır. Arkadan bakıldığında model sağ ve solu karıştırabilir.

- Uyandırma jestinden sonra kullanıcı **drone'a döner**.
- **Yüz görünüyorsa** (burun ve göz noktaları): tüm jestler kabul edilir.
- **Yüz görünmüyorsa:** Sadece simetrik jestler kabul edilir (Y, X, T, yaklaş, yüksel, alçal, çömel, fotoğraf, kayıt).
  Asimetrik jestler (sağa/sola kay, kement yönü) yok sayılır ve "Bana dön" uyarısı verilir.
- Gerekçe: Yanlış yöne gitmektense hiç gitmemek daha güvenlidir.

### 5.7 Orbit (onaylandı)

| Ayar | Değer |
|---|---|
| Yarıçap | O anki takip mesafesi (en az 5 m) |
| İrtifa | O anki takip irtifası |
| Hız | Çevresel hız **2 m/s** sabit |
| Tur süresi | 8 m'de ~25 sn, 15 m'de ~47 sn |
| Kapsam | 1 tam tur, sonra başladığı açıya döner |
| Yön | Sağ kolla kement saat yönünde, sol kolla saat yönünün tersine |
| İptal | X jesti, kullanıcının harekete geçmesi ya da engel ("Orbit yarıda kaldı, engel") |

### 5.8 Karışabilecek jest çiftleri (test listesi)

| Çift | Neden karışabilir | Ayırt edici özellik |
|---|---|---|
| Ön ↔ Arka görünüm | Arkadan bakınca sağ-sol ters algılanabilir | Yüz noktalarının görünürlüğü (bkz. §5.6) |
| X ↔ Yaklaş | İkisinde de eller omuz hizasında | X'te bilekler **karşı** omuzda, Yaklaş'ta **kendi** omzunda |
| Y ↔ Kayıt | İkisinde de kollar yukarıda | Y'de dirsekler düz, Kayıt'ta bükük ve bilekler başa yakın |
| Alçal ↔ Rahat duruş | Kol aşağıda | Alçal'da kol gövdeden belirgin şekilde açık (~45°) |
| Fotoğraf ↔ Dinlenme pozu | Eller belde doğal bir duruş | Sadece komut modunda geçerli. Yanlış tetiklense de zararsız. |

Bu çiftler, test yol haritasındaki "kayıtlı videolarla jest geliştirme" aşamasında özellikle denenir.

### 5.9 Geri bildirim

- **Birincil:** Bluetooth kulaklıktan sesli bildirim.
- **Yedek:** Drone'un "onay hareketi" (ör. küçük sağ-sol yaw salınımı).

### 5.10 İleride (v2+)

- Yakın mesafede el/parmak jestleri: Önce vücut pozu kişiyi bulur, sonra el bölgesi kırpılıp el modeline verilir.
- Wear OS saat ile ek komut kanalı.

---

## 6. Oturum Akışı (onaylandı)

```
[1. HAZIRLIK] ──► [2. KALKIŞ] ──► [3. OPERATÖR KİLİDİ] ──► [4. TAKİP] ──► [5. OTURUM SONU]
  telefon elde     telefon elde     telefon çantada          normal akış
```

### 6.1 Hazırlık: otomatik uçuş öncesi kontrol listesi

Uygulama açılınca (telefon + RC-N2 + drone bağlıyken) kontroller otomatik yapılır, sonuç ekranda ve sesli bildirilir.

| Kontrol | Başarısızsa |
|---|---|
| Drone GPS'i ve ev noktası kaydı | ⛔ **Kalkış yok** (takip ve eve dönüş için şart) |
| Sinyal kaybı davranışı ve RTH irtifası (30 m) DJI'a yüklendi (§8.11) | ⛔ Kalkış yok |
| Drone pili **≥ %40** | ⛔ Kalkış yok |
| DJI sistem durumu (pusula, IMU, uçuşa yasak bölge) | ⛔ Kalkış yok, DJI'ın uyarısı okunur |
| Engel sensörü verisi geliyor mu? | ⚠️ "B modunda devam edilsin mi?" sorulur, karar kullanıcının |
| Telefon GPS'i | ⚠️ Uyarı (GPS yedeği çalışmaz) |
| Bluetooth kulaklık | ⚠️ Uyarı, "Kulaklık testi" sesi çalınır |
| Telefon pili | ⚠️ Uyarı |
| SD kartta **≥ 15 GB** boş alan (§11.2) | ⚠️ Uyarı |
| Işık seviyesi | ⚠️ "Düşük ışık, sensörler kısıtlı" |

⛔ = güvenlik şartı, ⚠️ = konfor / yedek.

### 6.2 Kalkış: ekrandaki butonla

- Kalkış **ekrandaki "kaydırarak kalk" butonuyla** yapılır, jestle yapılmaz.
  - Telefon bu anda zaten elde.
  - Yerdeki drone kamerası jest okumak için kötü bir açıda.
  - Kazara kalkış riski ortadan kalkar.
- Akış:
  1. Kaydırarak kalk. Drone **3 m'ye yükselir ve havada asılı kalır.**
  2. "Kalkış tamam. Telefonu kaldır, karşıma geç, Y yap."
  3. Kullanıcı telefonu çantaya koyar, drone bu sırada sadece bekler.

### 6.3 Operatör kilidi

- Kullanıcı drone'un **5-8 m önüne geçer**, ona döner ve **Y'yi 2 sn tutar.** İlk kilit, yanlış kişiye kilitlenmemek için normal Y'den (1 sn) uzundur.
- Bu sırada **görünüm profili** çıkarılır: üst/alt kıyafet renkleri, boy oranı, telefon GPS'iyle konum eşleşmesi.
- "Seni tanıdım, takip başlıyor" anonsunun ardından drone varsayılan pozisyona geçer (8 m, 5 m, +30°).
- **Kilit kurulamazsa:** 20 sn'de bir hatırlatma yapılır. **2 dakika** içinde Y gelmezse "Operatör yok, iniyorum" denir ve drone kalkış noktasına iner.

### 6.4 Oturum sonu

- İniş sonrası "Oturum bitti" anonsu ve özet: uçuş süresi, video/fotoğraf sayısı.
- **Pil değişimi:** Görünüm profili gün boyu hatırlanır. Yeni kalkışta Y yine gerekir ama tanıma anında olur.

### 6.5 Telefonun çantada çalışması

- **Ekran kapalı çalışma:** Uygulama Android "ön plan servisi" olarak çalışır. Video çözme ve MediaPipe'ın ekran kapalıyken kesintisiz çalıştığı test edilecek.
- **Isınma:** Video çözme ve yapay zekâ işlemi telefonu ısıtır, ısınan telefon yavaşlar ve gecikme artar.
  - Uygulama telefon sıcaklığını izler. Isınınca önce analiz hızını düşürür (ör. 30 → 15 FPS).
  - Kritik seviyede "Telefon ısındı" uyarısı verilir.
  - Hava alan bir göğüs çantası önerilir.

### 6.6 Jest antrenman modu

Drone olmadan, **telefonun ön kamerasıyla** çalışan bir mod. Kullanıcı jestleri yapar, ekranda
"Algılandı: Yaklaş ✅" gibi geri bildirim görür.

- Sahaya çıkmadan jestleri öğrenmeyi sağlar.
- Geliştirme sırasında jest tanıma kurallarını drone uçurmadan test etmeyi sağlar (bkz. yol haritası, Aşama 2).

---

## 7. Takip

- **Birincil:** Drone kamera görüntüsünde operatörün tespiti, ardından drone ve gimbal ile kişiyi kadraj merkezinde tutma (PID kontrol).
- **Yedek:** Kullanıcının üzerindeki **telefonun GPS konumu** (~3-5 m doğruluk). Görsel takip kaybolduğunda kaba takip sağlar.
- **Yeniden tanıma (re-ID):** Görsel temas geri geldiğinde operatör; kıyafet rengi, boy ve GPS konumuyla örtüşme gibi ipuçlarıyla doğrulanır. Gerekirse kullanıcı **uyandırma jestiyle** kendini tekrar tanıtır.
- **Gimbal:** İrtifa farkı ve yatay mesafeye göre eğim açısı hesaplanarak operatör kadrajda tutulur.
- **Gecikme bütçesi:** Görüntü + işlem + komut ≈ 200-300 ms. Yavaş ve orta hızlı takip için yeterli.

---

## 8. Güvenlik: Seçenek A (Kendi Güvenlik Valfimiz)

**Karar:** Ana yol Seçenek A. Sensör verisi alınamazsa Seçenek B'ye (açık alan, yüksek irtifa,
düşük hız) düşülür. B kabul edilebilir ama tercih edilmeyen bir yedek.

### 8.1 Çalışma prensibi

Takip ve jest modülleri drone'a doğrudan komut göndermez. Her komut, saniyede 10-20 kez
çalışan **güvenlik valfinden** geçer. Valfin sorusu şu:
_"Bu yöne bu hızla gidersem, gerekirse zamanında durabilir miyim?"_

### 8.2 Durma mesafesine göre hız limiti

Gecikme (~0,3 sn) ve fren kabiliyeti hesaba katılır. Güvenlik payı 2 m için örnek:

| Gidiş yönünde engel | İzin verilen azami hız |
|---|---|
| 10 m | ~5 m/s |
| 5 m | ~3 m/s |
| 3 m | ~1,5 m/s |
| ≤ 2 m | **0, o yöne hareket yasak** |

### 8.3 Yönsel kesme

Sadece engele doğru olan hız bileşeni kısılır, diğer yönlerdeki hareket serbesttir.
Böylece drone engelin yanından **kayarak** basit bir kaçınma yapar.

### 8.4 Veri tazeliği bekçisi

Sensör verisi 200-300 ms'den eskiyse drone hover'a geçer ve sesli uyarı verilir.

### 8.5 Kademeli güvenli mod (A'dan B'ye)

Sensörler uzun süre veri vermezse (ör. düşük ışık) sistem otomatik olarak B moduna geçer:
hız limiti düşer, takip yalnızca belirlenen minimum irtifanın üstünde sürer. Durum sesli bildirilir.

### 8.6 Bilinen kör noktalar

- **Düşük ışık / karanlık:** Görüş sensörleri çalışmaz (bunu §8.4 ve §8.5 yakalar).
- **İnce dallar, teller, cam:** Geç algılanır ya da hiç algılanmaz. Güvenlik payları yüksek tutulur.
- **Takipte kamera yönü:** Kamera operatöre bakar, drone çoğunlukla geri geri uçar. Ana kamera çarpma yönünü görmez, bu yüzden görsel engel tespiti için ana kameraya güvenilmez.
- **DJI'ın kendi freni:** Virtual Stick'te Mini 4 Pro için çalışıp çalışmadığı test edilecek. Çalışıyorsa ikinci emniyet olur, ama tasarım buna **dayanmaz**.

### 8.7 Batarya (onaylandı)

| Pil seviyesi | Davranış | Sesli bildirim |
|---|---|---|
| < %30 | Tırmanma manevrası yapılmaz, engelde "bekle ve haber ver" uygulanır | (sadece engel anında) "Pil yetersiz, tırmanamıyorum" |
| **%20** | **Uyarı.** Takip sürer. %15 ve %12'de tekrar edilir. | "Pil yüzde 20" |
| **%10** | **Eve dönüş:** Takip biter, drone **bulunduğu yere** iner | "Pil yüzde 10, iniyorum" |

- **"Ev" = kullanıcının yanı.** Drone zaten kullanıcıdan 4-15 m uzakta. Kalkış noktasına dönmek
  (kullanıcı oradan kilometrelerce uzaklaşmış olabilir) hem pili hem kullanıcıyı boşa yorar.
  İniş, onaylanmış iniş davranışıyla aynıdır (§5.4): olduğu yere dikey iniş.
- **X ile iptal:** Zemin uygun değilse (su, yol vb.) X jesti inişi durdurur, drone havada kalır.
  Kullanıcı kumandayı alıp elle indirir (bkz. §8.10). DJI'ın kritik pil inişi yine de devrededir.
- **Dinamik ev noktası:** DJI'ın ev noktası sürekli kumandanın, yani kullanıcının konumuna güncellenir.
  Sinyal kaybında DJI'ın kendi RTH'si de böylece kullanıcıya döner. _(MSDK desteği test ile doğrulanacak.)_
- **DJI ile çakışma:** DJI'ın kendi "kritik pil zorunlu iniş" eşiği %10'a çok yakın olabilir.
  Testte bu eşik okunur. Gerekirse bizim eşiğimiz DJI eşiğinin **+%3 üstüne** çekilir, böylece iniş kontrolü bizde kalır.
- **Rüzgârda** eşikler +%5 kayar (§8.8).

### 8.8 Rüzgâr

DJI'ın rüzgâr uyarı seviyesi okunur _(MSDK'dan okunabildiği test ile doğrulanacak)_. Yedek olarak drone'un
havada sabit dururken yaptığı eğim açısından tahmin yapılır.

| Seviye | Davranış | Sesli bildirim |
|---|---|---|
| **Orta** | Maks. takip mesafesi 10 m, tırmanma limiti +20 m, orbit kapalı | "Rüzgâr kuvvetli, sınırlı mod" |
| **Güçlü** | Takip en yakın ayarda (6 m mesafe, 3 m irtifa) ve düşük hızda, tırmanma kapalı, pil eşikleri +%5 (uyarı %25, iniş %15). 30 sn'de bir iniş önerisi. | "Rüzgâr çok kuvvetli, inmeni öneririm" |

- Otomatik iniş yapılmaz, çünkü güçlü rüzgârda kontrolsüz iniş daha riskli olabilir. Kararı kullanıcı çömel + Y ile verir.
- Gerekçe: 249 g gövde rüzgârdan çok etkilenir, rüzgâr da yükseldikçe ve açıklık arttıkça güçlenir.

### 8.9 Kalabalık

Kadrajdaki kişi sayısı sayılır (MediaPipe çoklu poz / kişi tespiti).

| Durum | Davranış | Sesli bildirim |
|---|---|---|
| Operatör dışında **≥ 5 kişi** | Orbit kapalı, hız limiti yarıya iner, irtifa en az 5 m | "Kalabalık alan" |
| Operatör kilidi belirsizleşti | Jestler kabul edilmez, takip son bilinen operatörle sürer | "Seni ayırt edemiyorum, Y yap" |

- **Sınır:** Ana kamera kullanıcıya baktığı için drone'un **altındaki** insanları göremez. Kalabalık üzerinde uçmamak,
  yasal olarak da kullanıcının sorumluluğundadır (SHGM).

### 8.10 Kumandayla devralma

Her an, her durumda **kumanda öncelikli olmalıdır.** Kullanıcı RC-N2'deki duraklatma (fren) düğmesine
bastığında Virtual Stick modundan çıkılır ve kontrol tamamen elle uçuşa geçer.
_(Mini 4 Pro'da bu davranış test ile doğrulanacak.)_

### 8.11 Bağlantı kayıpları (onaylandı)

> **Temel gerçek:** Drone ↔ kumanda bağlantısı koptuğunda uygulama drone'a hiçbir şey yaptıramaz.
> O anda drone'u yalnızca DJI'ın kendi yazılımı yönetir. Bu yüzden sinyal kaybı davranışı **kalkıştan önce DJI'a yüklenir** (§6.1).
> Bu modda DJI'ın kendi engelden kaçınması tam olarak devrededir.

```
 Drone ══(1) O4 radyo══ RC-N2 ──(2) USB kablo── Telefon ──(4) Bluetooth── Kulaklık
   │
   └──(3) Canlı video (O4 içinde)
```

#### (1) Drone ↔ Kumanda

- Olası sebep mesafe değil, **arada engel olması:** bina köşesi, tırmanmada arada kalan ağaç sırası, vücudun çantadaki kumandanın antenlerini kapatması.
- **Karar:** Drone **~1 dakika havada asılı kalır, ardından eve dönüş (RTH)** başlar.
- **Ev = kullanıcının son konumu** (dinamik ev noktası, §8.7). RTH irtifası: **30 m** (çoğu ağacın üstü). DJI'ın gelişmiş RTH'si engel sensörlerini kullanır.
- Bu sürede telefon kullanıcıya **yol tarif eder** (drone'un son konumu ile kullanıcı GPS'i biliniyor):
  "Drone bağlantısı koptu. Drone arkanda, 40 metre." Mesaj 10 sn'de bir güncellenir.
- 60 sn dolunca telefon, politika gereği RTH'nin başladığını bilir: "Drone eve dönüyor, sana geliyor. İniş için yer aç."
- **RTH sırasında bağlantı geri gelirse:** RTH iptal edilir, drone havada bekler, Y beklenir (bkz. ortak kural).
- ⚠️ **Uygulanabilirlik:** DJI'ın sinyal kaybı seçenekleri (havada kal / eve dön / in) normalde **anında** uygulanır, "1 dk bekle, sonra dön" gibi gecikmeli bir seçenek olmayabilir. Mini 4 Pro'da kendi uygulamamızı çalıştıramadığımız için bu zamanlayıcıyı biz de kuramayız.
  - Test edilecek: DJI sinyal kaybında RTH'den önce ne kadar bekliyor, süre ayarlanabiliyor mu?
  - **Yedek plan:** Gecikme mümkün değilse **anında RTH** kullanılır. Drone kullanıcıya doğru geldiği için bağlantı genellikle kısa sürede geri gelir. Geri gelince RTH iptal edilir ve drone havada bekler. Sonuç, istenen davranışa çok yakındır.
- ⚠️ **İniş noktası:** Ev noktası kullanıcının konumu olduğundan RTH inişi kullanıcının yakınına denk gelir.
  MSDK ev noktasını keyfi bir koordinata ayarlamaya izin veriyorsa ev noktası kullanıcının **~5 m yanına** konur _(test edilecek)_.

#### (2) Telefon ↔ Kumanda (kablo çıktı, uygulama çöktü, telefon ısındı)

- Kumanda drone'a bağlı kalır, uygulamadan komut gelmez. Komut kesilince drone **havada asılı kalır** _(ne kadar sürede durduğu test edilecek)_.
- **Uygulama çalışıyorsa** (sadece kablo çıktıysa): "Kumanda bağlantısı koptu, kabloyu kontrol et."
- **Uygulama çöktüyse:** Otomatik yeniden başlar ve kumandaya bağlanır, ama takibe kendiliğinden devam etmez: "Sistem yeniden başladı, Y yap."
- **Pratik önlem:** Kısa, dik açılı USB kablo. Kumanda göğüs çantasına sabitlenir.

#### (3) Video bozuldu, kontrol bağlantısı sağlam

- **Video sağlam, kullanıcı görünmüyor** (engel arkasında): GPS yedeğiyle takip sürer (§7).
- **Videonun kendisi donmuş veya kopmuş:** Drone **havada asılı kalır.** Drone kullanıcıyı göremediği için **X (acil dur) jestini de göremez.** Acil durdurma kanalı olmadan takip yapılmaz. "Görüntü kesildi, durdum."

#### (4) Kulaklık koptu

- Bildirimler telefon hoparlöründen yüksek sesle verilir.
- Onay geri bildirimi için drone'un yedek "onay hareketi" (§5.9) devreye girer.

#### Ortak kural: Bağlantı geri gelince

Hangi bağlantı koparsa kopsun, geri geldiğinde **takip otomatik olarak devam etmez.** Drone havada bekler: "Bağlantı geri geldi, Y yap."
Kullanıcı Y yapınca takip sürer. Gerekçe: Kopukluk sırasında ortam değişmiş olabilir.

#### "Hayattayım" sesi

Uygulama çökerse kulaklık sessizleşir ve bu fark edilmeyebilir. Bu yüzden **dakikada bir çok kısa ve hafif bir "tık"** sesi çalınır.
Başka bir bildirim çalınıyorsa o dakikanın tık sesi atlanır. **Sessizlik = bir şeyler ters.**

---

## 9. Engel Aşma: Tırmanma Manevrası

**Karar:** Takip yolu tamamen kapanırsa drone engelin **üstünden tırmanarak** aşar.
Mümkün değilse bekler ve haber verir.

### 9.1 Durum makinesi

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

### 9.2 Ön koşullar (hepsi sağlanmalı)

- Yukarı sensör açık (ağaç, köprü altı ya da kapalı alanda tırmanma yok)
- İrtifa payı var: manevranın başladığı irtifanın en fazla **+50 m** üstüne çıkılır (orta rüzgârda +20 m), kalkışa göre 120 m sınırı aşılmaz
- Sensör verisi taze ve ışık yeterli
- Pil **en az %30** (50 m tırmanma ve iniş birkaç dakika sürer ve pilin ~%7-10'unu tüketir)
- Rüzgâr "güçlü" seviyede değil (§8.8)

### 9.3 Manevra adımları

1. **Tırman:** Yatay hareket durur, yaklaşık 1 m/s ile yükselinir. Ön taraf "açık" okunduktan sonra **+3-5 m ek pay** tırmanılır, çünkü sensörlerin dikey görüş açısı sınırlı ve ağaç tepeleri düzensiz.
2. **Geç:** Yavaş ileri hareket edilir. Aşağı sensör engel gösterdiği sürece irtifa korunur. Gimbal aşağı eğilerek operatör kadrajda tutulmaya çalışılır.
3. **İn:** Aşağı ve yatay sensörler temizse takip irtifasına **daha da yavaş** inilir. İniş, tırmanmadan daha riskli bir aşamadır.
4. Bu sırada görsel temas kaybolursa **GPS yedeği** devreye girer (bkz. §7).

---

## 10. Sesli Bildirimler

### 10.1 Ses politikası

- **Dil:** Türkçe. Android'in çevrimdışı Türkçe TTS sesi kullanılır, internet gerekmez.
- **Kısa:** Mesajlar 2-4 kelimedir.
- **Öncelik seviyeleri:**

| Seviye | Örnek | Davranış |
|---|---|---|
| **Kritik** | Pil, acil dur, engel, sensör, rüzgâr | Diğer konuşmayı keser. Durum sürdükçe 30 sn'de bir tekrarlanır. |
| **Önemli** | Komut sonuçları, iniş onayı, orbit | Sıraya girer |
| **Bilgi** | Ayar değişiklikleri | Sıradaysa kritik mesajlar için atlanabilir |

- **Kısa sesler (bip):** Sık olaylarda konuşma yerine kullanılır, böylece kulaklık sürekli konuşmaz.
  - Jest algılandı (tutma süresi başladı): tek kısa bip
  - Komut moduna giriş: çift bip
  - Komut modundan çıkış: alçalan ton
  - "Hayattayım" sesi: dakikada bir hafif tık (§8.11)
- **Müzik:** Kullanıcı müzik dinliyorsa bildirim sırasında müziğin sesi kısılır (Android audio focus / ducking).
- **Detay seviyesi:** v1'de tek seviye ("sade"). Ayarlanabilir detay v2+.

### 10.2 Mesajlar

| Durum | Mesaj |
|---|---|
| Komut alındı | "Komut: {komut}" |
| Ayar değişti | "Mesafe {x} metre" / "İrtifa {x} metre" / "Açı {x} derece" |
| Sınır ihlali | "Önce uzaklaş" / "En yakın mesafe" / "En yüksek irtifa" |
| Arkadan asimetrik jest | "Bana dön" |
| Orbit | "Orbit başladı" / "Orbit tamam" / "Orbit yarıda kaldı, engel" |
| Fotoğraf | "3, 2, 1" + deklanşör sesi (kayıt sürerken: "Kare işaretlendi") |
| Kayıt | "Kayıt başladı" / "Kayıt durdu" |
| İniş onayı | "İniş onaylansın mı?" → "İniyorum" / "İniş iptal" |
| Acil dur | "Durdum" |
| Takibe devam | "Takibe devam" |
| Engel, tırmanma başladı | "Engel var, tırmanıyorum" |
| Engel aşıldı | "Engel aşıldı, iniyorum" |
| Görsel takip kayıp | "Seni göremiyorum, GPS ile geliyorum" |
| Tırmanma mümkün değil | "Tırmanamıyorum, burada bekliyorum" |
| Takip engel nedeniyle durdu | "Engel var, takip durdu" |
| Sensör verisi yok | "Sensör verisi yok, durdum" |
| Güvenli moda geçiş | "Sensörler kapalı, güvenli moda geçildi" |
| Pil uyarısı | "Pil yüzde 20" (ve %15, %12) |
| Pil inişi | "Pil yüzde 10, iniyorum" |
| Pil, tırmanma yok | "Pil yetersiz, tırmanamıyorum" |
| Rüzgâr orta | "Rüzgâr kuvvetli, sınırlı mod" |
| Rüzgâr güçlü | "Rüzgâr çok kuvvetli, inmeni öneririm" |
| Kalabalık | "Kalabalık alan" |
| Operatör belirsiz | "Seni ayırt edemiyorum, Y yap" |
| Drone bağlantısı koptu | "Drone bağlantısı koptu. Drone arkanda, {x} metre." (10 sn'de bir) |
| RTH başladı (60 sn sonra) | "Drone eve dönüyor, sana geliyor. İniş için yer aç." |
| Kumanda bağlantısı koptu | "Kumanda bağlantısı koptu, kabloyu kontrol et" |
| Uygulama yeniden başladı | "Sistem yeniden başladı, Y yap" |
| Görüntü kesildi | "Görüntü kesildi, durdum" |
| Bağlantı geri geldi | "Bağlantı geri geldi, Y yap" |

## 11. Kamera ve Kayıt Ayarları (onaylandı)

### 11.1 Kayıt ile canlı görüntü ayrımı

- **Kayıt:** Drone'un SD kartına yüksek kalitede yazılır.
- **Canlı görüntü:** Telefona gelir (~1080p). Jest tanıma ve takip **bununla** çalışır.

Çoğu kayıt ayarı canlı görüntüyü etkilemez, ama bazıları etkiler:

| Ayar | Canlı görüntüye etkisi | v1'de |
|---|---|---|
| **Dikey çekim** (gimbal 90° döner) | Görüntü dikey olur, takip hesapları değişir | ❌ Kapalı |
| **Dijital zoom** | Kadraj daralır, kullanıcı kadrajdan çabuk çıkar | ❌ Kapalı (1x) |
| **D-Log M** | Canlı görüntü soluk ve kontrastsız gelir, kişi tespiti zorlaşabilir | 🟡 Seçenek, test edilecek |
| **4K/100 yavaş çekim** | Bazı modlarda canlı görüntü değişebilir | 🟡 Ek profil, test edilecek |

### 11.2 Varsayılan kayıt profili

| Ayar | Değer | Gerekçe |
|---|---|---|
| Video | **4K / 60 fps** | Aksiyonda akıcı, sonradan %50 yavaşlatılabilir |
| Codec | **H.265** | Aynı kalitede daha küçük dosya |
| Renk | **Normal** | Renk düzeltme gerekmez, canlı görüntü net gelir |
| Yön / zoom | **Yatay / 1x** | §11.1 kısıtları |
| Pozlama | **Otomatik** | Işık sürekli değişiyor |
| Fotoğraf | **12 MP JPEG** | Hızlı kaydedilir (48 MP / RAW yavaş) |

- **Ek profil "Yavaş çekim":** 4K/100. Canlı görüntüye etkisi test edildikten sonra açılır.
- **Alan:** 4K/60 H.265 ≈ 1 GB/dk, pil başına ≈ 30 GB. Uçuş öncesi kontrol listesinde **< 15 GB boş alan → uyarı** (§6.1).

### 11.3 Kayıt sırasında fotoğraf: kare işareti

Mini 4 Pro'nun video kaydederken tam çözünürlüklü fotoğraf çekemediği varsayılır _(test edilecek)_.

- **Kayıt yokken:** Fotoğraf jesti normal 12 MP fotoğraf çeker (3 sn geri sayımla).
- **Kayıt sürerken:** Kayıt durmaz. Geri sayımın sonunda o an **işaretlenir** ("Kare işaretlendi").
  Oturum sonunda işaretli anlardan **4K kareler (8 MP)** çıkarılır.

### 11.4 Kayıt başlatma

- **Otomatik kayıt kapalı.** Kayıt yalnızca kayıt jestiyle (iki el başın üstünde, §5.4) başlar ve durur.
- İniş yapılırken kayıt sürüyorsa kayıt otomatik durdurulur ve dosya güvenle kapatılır.
- Her kayıt ayrı bir dosyadır. Dosyalar drone'un SD kartında kalır, oturum özetinde (§6.4) sayısı bildirilir.

---

## 12. Geliştirme ve Test Yol Haritası

Her aşama bir öncekinin başarısına bağlıdır. **Aşama 1'in sonucu projenin yolunu belirler.**

| # | Aşama | Risk | Amaç |
|---|---|---|---|
| 1 | **Kader testi: yerde sensör verisi** (motorlar kapalı) | Yok | PerceptionManager, Mini 4 Pro'da engel mesafesi veriyor mu? Drone'un etrafında kartonla dolaşılır. |
| 2 | Jest tanıma: **antrenman modu** ve **kayıtlı videolar** | Yok | Önce telefonun ön kamerasıyla (§6.6), sonra DJI Fly ile çekilmiş jest videolarıyla MediaPipe kurallarının ayarlanması |
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

## 13. Riskler

| Risk | Etki | Önlem |
|---|---|---|
| Mini 4 Pro, SDK'ya engel verisi vermiyor | Yüksek | Aşama 1'de erken tespit, B moduna düşme |
| Virtual Stick'te DJI freni yok | Yüksek | Kendi valfimiz (§8) |
| Jest yanlış algılama | Orta | Uyandırma jesti, tutma süresi, iki adımlı onay |
| Kadrajda birden fazla kişi | Orta | Operatör kilidi, re-ID |
| Düşük ışıkta sensör körlüğü | Orta | Veri tazeliği bekçisi, B moduna otomatik geçiş |
| İnce dal / tel | Orta | Yüksek güvenlik payı, tırmanmada ek pay |
| ~200-300 ms gecikme | Düşük-Orta | Hız limitleri |
| Rüzgâr (249 g gövde) | Orta | Seviyeye göre kısıtlı mod, pil eşiklerinin kayması (§8.8) |
| Yasal: görüş hattı (SHGM), kalabalık üstü uçuş | Orta | Kullanım kuralları, kalabalık modu (§8.9) |
| Telefonun ısınması / ekran kapalıyken kısıtlanması | Orta | Ön plan servisi, sıcaklık izleme, analiz hızını düşürme (§6.5) |
| Kablo çıkması / uygulama çökmesi | Orta | Dik açılı kısa kablo, otomatik yeniden başlama, "hayattayım" sesi (§8.11) |
| RTH inişinin kullanıcıya yakın olması | Orta | Sesli uyarı, mümkünse ev noktasına 5 m ofset (§8.11) |
| DJI kritik pil eşiğiyle çakışma | Orta | Testte eşiği okuyup bizimkini üstüne çekmek (§8.7) |

---

## 14. Açık Sorular

- [x] ~~Varsayılan takip mesafesi / irtifa / açı ve min.-maks. sınırlar~~ (bkz. §5.5)
- [x] ~~İniş, kayıt başlat/durdur, fotoğraf ve orbit için jest atamaları~~ (bkz. §5.4)
- [x] ~~Jest ayar adımları~~ (±2 m / ±15°)
- [x] ~~Orbit yarıçapı ve hızı~~ (bkz. §5.7)
- [x] ~~Tırmanma için proje irtifa limiti~~ (+50 m, bkz. §9.2)
- [x] ~~Sesli bildirim dili ve detay seviyesi~~ (bkz. §10.1)
- [x] ~~Batarya eşikleri~~ (bkz. §8.7)
- [x] ~~Rüzgâr ve kalabalık durumlarında davranış~~ (bkz. §8.8, §8.9)
- [x] ~~Kalkış ve oturum başlatma akışı~~ (bkz. §6)
- [x] ~~Sinyal kaybında davranış~~ (bkz. §8.11)
- [x] ~~Kayıt ayarları~~ (bkz. §11)

### Test ile doğrulanacaklar

- [ ] PerceptionManager engel mesafesi verisi Mini 4 Pro'da geliyor mu? (Aşama 1)
- [ ] Virtual Stick'te DJI freni çalışıyor mu? (Aşama 4)
- [ ] DJI'ın kritik pil zorunlu iniş eşiği kaç? (§8.7)
- [ ] Ev noktası kumanda konumuna dinamik güncellenebiliyor mu? (§8.7)
- [ ] Rüzgâr uyarı seviyesi MSDK'dan okunabiliyor mu? (§8.8)
- [ ] Kumandanın duraklatma düğmesi Virtual Stick'ten çıkarıyor mu? (§8.10)
- [ ] Ekran kapalıyken video çözme ve MediaPipe kesintisiz çalışıyor mu? (§6.5)
- [ ] Çantadaki telefon ne kadar ısınıyor, analiz hızı düşüyor mu? (§6.5)
- [ ] Sinyal kaybında DJI RTH'den önce ne kadar bekliyor, gecikme ayarlanabiliyor mu? (§8.11)
- [ ] Ev noktası kullanıcıdan ~5 m ofsetli bir koordinata ayarlanabiliyor mu? (§8.11)
- [ ] Virtual Stick komutları kesilince drone ne kadar sürede havada duruyor? (§8.11)
- [ ] RTH sırasında bağlantı geri gelince uygulama RTH'yi iptal edebiliyor mu? (§8.11)
- [ ] D-Log M'de canlı görüntüde kişi tespiti yeterli mi? (§11.1)
- [ ] 4K/100'de canlı görüntü değişiyor mu? (§11.1)
- [ ] Video kaydederken fotoğraf çekilebiliyor mu? (§11.3)

---

## 15. Karar Günlüğü

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
| 2026-10-02 | Jest seti onaylandı (§5.4): tamamı vücut jesti, v1'de sadece MediaPipe Pose |
| 2026-10-02 | "Y" = evet/uyandır/devam, "X" = acil dur/iptal. Başparmak ve avuç jestleri çıkarıldı. |
| 2026-10-02 | Ayar adımları: ±2 m (mesafe, irtifa), ±15° (açı) |
| 2026-10-02 | İniş: çömel + Y onayı, drone **bulunduğu yere** iner |
| 2026-10-02 | Fotoğraf: eller belde, **3 sn sesli geri sayım** |
| 2026-10-03 | Takip varsayılanları: mesafe **8 m** (4-15), irtifa **5 m** (3-10), açı **+30°** sağ-arka |
| 2026-10-03 | Kombinasyon kuralları: çapraz mesafe ≤ 18 m, kamera eğimi ≤ 45° |
| 2026-10-03 | "Drone'a dön" kuralı: yüz görünmüyorsa asimetrik jestler yok sayılır |
| 2026-10-03 | Orbit: yarıçap = takip mesafesi (min 5 m), 2 m/s, 1 tam tur |
| 2026-10-03 | Tırmanma limiti: başlangıç irtifasına göre **+50 m**, en az %30 pil |
| 2026-10-03 | Pil: **%20 uyarı**, **%10 eve dönüş** = kullanıcının yanında, olduğu yere iniş |
| 2026-10-03 | Rüzgâr ve kalabalık: uyarı + otomatik kısıtlı mod (otomatik iniş yok) |
| 2026-10-03 | Sesli bildirim: Türkçe, kısa, 3 öncelik seviyesi, sık olaylarda bip |
| 2026-10-03 | Kumanda her zaman devralabilir (duraklatma düğmesi) |
| 2026-10-03 | Oturum akışı (§6): otomatik kontrol listesi, oturum için en az **%40** pil |
| 2026-10-03 | Kalkış **ekrandaki butonla**, 3 m'de bekleme |
| 2026-10-03 | İlk operatör kilidi: Y 2 sn + görünüm profili. **2 dk** içinde kilit yoksa iniş |
| 2026-10-03 | **Jest antrenman modu** (telefon ön kamerası) kapsamda |
| 2026-10-03 | Drone-kumanda bağlantısı koparsa: **~1 dk havada kal, sonra RTH** (ev = kullanıcı, 30 m). Gecikme mümkün değilse anında RTH. |
| 2026-10-03 | Video koparsa havada kal (X görülemez). Telefon-kumanda koparsa havada kal. |
| 2026-10-03 | Bağlantı geri gelince takip için **Y beklenir** |
| 2026-10-03 | Dakikada bir **"hayattayım" tık sesi** |
| 2026-10-05 | Kayıt profili: **4K/60, H.265, Normal renk, yatay, 1x**. Fotoğraf: **12 MP JPEG** |
| 2026-10-05 | Kayıt sırasında fotoğraf jesti → **kare işareti**, oturum sonunda 4K kare çıkarılır |
| 2026-10-05 | **Otomatik kayıt kapalı**, kayıt jestle başlar/durur |

---

## 16. Kaynaklar

- [DJI MSDK: IVirtualStickManager](https://developer.dji.com/api-reference-v5/android-api/Components/IVirtualStickManager/IVirtualStickManager.html)
- [DJI Mobile SDK dokümantasyonu](https://developer.dji.com/doc/mobile-sdk-tutorial/en/)
- [DroneDJ: Mini 4 Pro MSDK desteği](https://dronedj.com/2025/03/21/dji-mini-4-pro-msdk/)
- [GitHub: Mobile-SDK-Android-V5, PerceptionInfo sorunu #618](https://github.com/dji-sdk/Mobile-SDK-Android-V5/issues/618)
- [GitHub: Mobile-SDK-Android-V5, Mini 4 Pro Virtual Stick sorusu #670](https://github.com/dji-sdk/Mobile-SDK-Android-V5/issues/670)
- [MediaPipe](https://ai.google.dev/edge/mediapipe/solutions/guide)
