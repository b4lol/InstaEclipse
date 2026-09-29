# InstaEclipse — Kotlin ve Java Hibrit Geçiş Yol Haritası

> Hazırlanma tarihi: 29 Eylül 2026  
> Durum: Uygulama planı; Kotlin geçişi henüz başlamadı.  
> Referans: `fdcceb4`, `modernize/libxposed-101`, `v0.7.0-test.1`.

## 1. Amaç ve başarı tanımı

InstaEclipse'i mevcut libxposed çalışma modelini koruyarak, Java ve Kotlin'in birlikte kullanıldığı, bakımı daha kolay bir projeye dönüştürmek. Çalışan özellikleri topluca yeniden yazmak yerine yeni geliştirmelerde ve bakım ihtiyacı olan alanlarda Kotlin kullanmak.

Başarı, Kotlin dosyalarının oranıyla ölçülmez. Başarı ölçütleri:

- Instagram içindeki hook davranışları ve kullanıcı ayarları korunur.
- Yeni ekranlar ve arka plan işlerinin durum, hata ve yaşam döngüsü yönetimi açık hale gelir.
- Java–Kotlin sınırları test edilir; iki süreç arasında veri sözleşmeleri belgelenir.
- Başlangıç, kaydırma, bellek ve indirme performansında kabul edilmemiş gerileme oluşmaz.
- Her geçiş küçük, incelenebilir ve geri alınabilir değişikliklerden oluşur.
- Her yayımlanan APK belirli bir kaynak commit'i, imza ve doğrulama kaydıyla eşleşir.

Bu belge hedef mimariyi ve yapılacak işleri tanımlar. Kutular yalnızca ilgili kanıt eklendiğinde tamamlandı olarak işaretlenir. Tarih ve efor tahminleri Faz 0 envanterinden sonra belirlenir; burada verilen sıra teslim tarihi taahhüdü değildir.

## 2. Mevcut durum ve referans sürüm

| Alan | Mevcut yapı | Geçişte yaklaşım |
| --- | --- | --- |
| Uygulama | Tek `app` modülü, Java ve XML arayüzler | Önce aynı modülde birlikte kullanım |
| Kimlik | `ps.reso.instaeclipse` | Korunacak |
| Android | `minSdk 28`, `compileSdk/targetSdk 36` | Dil geçişinden bağımsız yönetilecek |
| Java hedefi | Java 17 kaynak ve bytecode uyumluluğu | Kotlin JVM hedefiyle eşleştirilecek |
| Derleme | AGP `8.13.2`, Gradle `8.13`, sürüm kataloğu | Kotlin uyumu doğrulanarak küçük bir değişiklikle eklenecek |
| Hook motoru | libxposed API `101.0.1`, service `101.0.0` | Mevcut API 101 modeli korunacak |
| Metot keşfi | DexKit, `LazyDexKit`, `DexKitCache` | Önbellek ve gecikmeli açılış korunacak |
| UI | Activity, Fragment, View ve XML | Kotlin için Compose zorunlu olmayacak |
| Ayarlar | Instagram tarafında `instaeclipse_prefs`, companion tarafında `instaeclipse_cache` | Anahtarlar ve anlamları korunacak |
| Süreçler arası iletişim | Paket hedefli broadcast, imza izni, nonce ve remote preferences | Güvenlik sözleşmeleri korunacak |
| Küçültme | Release için `minifyEnabled false` | R8 ayrı bir çalışma olarak değerlendirilecek |
| Doğrulama | JVM testleri, lint, debug APK derlemesi | Karma dil ve cihaz kontrolleriyle genişletilecek |

Referans test APK'sında 41 birim testi, lint ve APK imza doğrulaması başarılıdır. Bu, tüm özelliklerin cihazda doğrulandığı anlamına gelmez; bu APK için cihaz testi yapılmadı.

Belge hazırlanırken çalışma ağacında commitlenmemiş özellik değişiklikleri de bulunuyor. Bunlar referans sürümün tamamlanmış kapsamı sayılmaz. Faz 0'da seçilecek temiz başlangıç commit'ine hangilerinin dahil edildiği açıkça kaydedilmelidir.

İlgili mevcut belgeler:

- [Mimari](docs/ARCHITECTURE.md)
- [Katkı kuralları](CONTRIBUTING.md)
- [Güvenlik politikası](SECURITY.md)
- [Güvenlik incelemesi](docs/SECURITY_AUDIT.md)
- [Performans incelemesi](docs/PERFORMANCE_AUDIT.md)
- [Değişiklik geçmişi](CHANGELOG.md)

## 3. Kapsam ve mimari kararlar

### 3.1 Kabul edilen yön

1. libxposed temelli çalışma modeli devam eder.
2. Java hook çekirdeği başlangıçta korunur.
3. Kotlin önce Android'den bağımsız yeni mantıkta ve companion uygulamada kullanılır.
4. Her sınıf sırf dil birliği sağlamak için dönüştürülmez.
5. Dil dönüşümü, davranış değişikliği ve paket taşıması mümkün olduğunca ayrı değişikliklerde yapılır.
6. Modül sınırları önce kod bağımlılıklarıyla netleştirilir; Gradle modüllerine bölme daha sonra gerekçelendirilir.
7. Ürün davranışını değiştiren tercihlerin test ve sürüm notu karşılığı bulunur.

### 3.2 Bu yol haritasına dahil olmayan işler

- Tüm Java kaynaklarını Kotlin'e çevirmek.
- Instagram APK'sını doğrudan yamalayan yeni bir patcher geliştirmek.
- Morphe'nin kodunu veya modül düzenini aynen taşımak.
- libxposed API değişimi, SDK yükseltmesi ve Kotlin geçişini tek değişiklikte yapmak.
- Bütün ekranları Compose ile yeniden yazmak.
- Sırf Kotlin eklendiği için veritabanı, DataStore, DI framework'ü veya yeni ağ kütüphanesi eklemek.
- Dil değişiminden otomatik hız, daha az RAM kullanımı veya Instagram sürümlerine kalıcı uyumluluk beklemek.

Morphe Patcher, APK bytecode'u ve kaynakları üzerinde değişiklik yapan bir kütüphanedir. Buradaki plan ise Instagram çalışırken müdahale eden mevcut modülü geliştirmektir. İleride doğrudan APK yaması istenirse paketleme, yeniden imzalama, sürüm eşleştirme, dağıtım ve bakım maliyetleri için ayrı bir fizibilite belgesi hazırlanmalıdır.

## 4. Hedef sorumluluk ve dil dağılımı

| Alan / mevcut örnekler | Süreç | Hedef dil | Uygulama kararı |
| --- | --- | --- | --- |
| `Xposed/Module`, `hook/*` | Instagram | Java | Giriş noktası ve interceptor semantiği korunur |
| `LazyDexKit`, `DexKitCache`, `ResIds` | Ağırlıkla Instagram | Java | Keşif ve performans çekirdeği ilk geçişin dışında |
| `mods/ghost`, `mods/network`, `mods/ads`, feed ve UI hook'ları | Instagram | Öncelikle Java | Yalnızca somut bakım ihtiyacı varsa ayrı değerlendirme |
| `MainActivity`, `fragments/*` | Companion | Kademeli Kotlin | Ekran başına dönüşüm, mevcut XML ile başlanır |
| `VersionCheck*`, yedekleme ve saf doğrulama mantığı | Companion / ortak | Kotlin adayı | Önce I/O ve iş kuralları ayrılır |
| `DownloadSaveService`, indirme koordinasyonu | Companion | Sonraki aşamada Kotlin adayı | Servis yaşam döngüsü doğrulanmadan dönüştürülmez |
| `SettingsManager`, `RemotePrefs`, `IpcSecurity` | Her iki süreç | Java sözleşme + gerektiğinde Kotlin adaptör | Depolama/IPC davranışı korunur |
| `FeatureFlags`, `FeatureManager` | Instagram ağırlıklı | Başlangıçta Java | Sıcak yolda senkron ve ucuz okuma korunur |
| `DialogUtils`, Instagram içine eklenen görünümler | Instagram | Başlangıçta Java | Companion UI dönüşümüyle karıştırılmaz |
| Konum ve tema Activity'leri | Companion | İlerleyen aşamada Kotlin | Bağlı Instagram hook'larından ayrı ele alınır |
| Testler | JVM / cihaz | Java ve Kotlin | Sözleşme testleri iki dilden kullanım içerebilir |

### 4.1 Bağımlılık yönü

```text
Companion UI (Kotlin/Java) ──> companion iş mantığı ──> veri/IPC adaptörleri
                                                      │
                                           ortak veri sözleşmeleri
                                                      │
Instagram hook'ları (Java) ──> hook çekirdeği + ayarların yerel görünümü
```

Ortak sözleşmeler companion UI'ına, Fragment/Activity sınıflarına veya belirli Instagram sınıflarına bağımlı olmamalıdır. Aynı APK içinde paket ayrımı gerçek süreç izolasyonu sağlamaz; Context, ClassLoader, UID ve izin sahipliği her çağrıda doğru olmalıdır.

### 4.2 Olası sonraki Gradle modülleri

Aşağıdaki isimler öneridir; başlangıçta yeni modül oluşturulmayacaktır:

- `:core-contracts`: Android bağımlılığı gerektirmeyen veri ve davranış sözleşmeleri.
- `:hook-runtime`: libxposed ve Instagram tarafındaki çekirdek.
- `:companion`: yardımcı uygulamanın ekranları ve servisleri.
- `:app`: manifest, kaynaklar ve APK paketleme.

`R` kaynakları, manifest birleştirme, native DexKit paketleme, döngüsel bağımlılıklar ve Xposed giriş metadatası çözülmeden fiziksel modül ayrımı yapılmaz. Tek `app` modülünde kalmak da kabul edilebilir nihai sonuçtur.

## 5. Java–Kotlin birlikte kullanım kuralları

### 5.1 API ve bytecode sözleşmeleri

- Java çağıran kod için basit sınıf, arayüz, enum ve açık sonuç türleri tercih edilir.
- `suspend`, `Flow` ve Kotlin'e özgü işlev türleri doğrudan mevcut Java hook API'sine taşınmaz; ihtiyaç halinde companion tarafında adaptör sağlanır.
- Java çağrılarında `object`, `companion object`, varsayılan parametre ve property erişiminin ürettiği gerçek imzalar incelenir.
- `@JvmStatic`, `@JvmField`, `@JvmOverloads`, `@JvmName` ve `@Throws` yalnızca mevcut çağrı sözleşmesi gerektiriyorsa kullanılır.
- Reflection ile erişilen sınıf/metot adları, görünürlük, constructor ve parametre türleri korunur. Manifest, explicit intent ve `java_init.list` referansları ayrıca kontrol edilir.
- Paket ve sınıf adının aynı kalması tek başına yeterli sayılmaz; alan erişimi, static üyeler ve exception davranışı da doğrulanır.
- Kotlin `internal` görünürlüğü güvenlik veya süreç izolasyonu sınırı olarak kabul edilmez.

### 5.2 Null, koleksiyon ve veri uyumluluğu

- Java'dan gelen platform tipleri güvenilir non-null veri kabul edilmez; sınırda doğrulanır.
- Yeni kodda `!!` istisna olmalı; kullanımı gerekçelendirilmelidir.
- Intent extra, reflection sonucu, disk/JSON verisi ve host uygulama nesneleri doğrulanmadan kullanılmaz.
- Primitive/boxed tip farkları, nullable değerler ve mutable koleksiyon paylaşımı için test yazılır.
- Gson ile kullanılan modellerde alan adları, varsayılanlar, eksik/null alanlar ve constructor davranışı eski örneklerle test edilir; `data class` dönüşümü otomatik uyumluluk sağlamaz.
- Hata gizleyen boş sonuçlar yerine kullanıcıya veya çağırana anlamlı hata türü iletilir; mevcut fail-safe hook davranışı korunur.

### 5.3 Thread ve coroutine kuralları

- Hook callback'i senkron kalır; sonucu etkilemesi gereken iş coroutine'e ertelenmez.
- Hook, UI veya ana thread üzerinde `runBlocking` kullanılmaz.
- `GlobalScope` kullanılmaz; her işin sahibi ve iptal zamanı belirlenir.
- Fragment görünümüyle ilgili işler view yaşam döngüsüne bağlanır. Ekran yeniden oluşturulunca aynı işin iki kez başlaması engellenir.
- Servis işleri açık bir servis scope'u tarafından yönetilir; kapanış ve zaman aşımında iptal edilir.
- `CancellationException` normal hata olarak yutulmaz; geniş `catch` ve `runCatching` kullanımları bu açıdan incelenir.
- `Dispatchers.IO` tek başına indirme sayısını sınırlama mekanizması değildir. Eşzamanlılık ayrıca sınırlandırılır.
- Coroutine'e geçmek bloklayan I/O'yu kendiliğinden iptal edilebilir yapmaz; bağlantı, stream ve dosya kaynakları kapatılır.
- `volatile` alanların çoklu alan işlemlerini atomik yapmadığı dikkate alınır; tutarlı ayar snapshot'ı veya açık senkronizasyon tasarlanır.

### 5.4 ClassLoader ve performans

- Kotlin runtime sınıflarının modül yükleyicisi üzerinden doğru çözüldüğü gerçek framework üzerinde sınanır; Instagram'ın kendi Kotlin bağımlılıklarına güvenilmez.
- Companion ViewModel/lifecycle altyapısı Instagram hook sürecine taşınmaz.
- Sıcak hook yoluna coroutine, Flow collector, gereksiz lambda/collection zinciri veya yeni reflection taraması eklenmez.
- Özellik kapalıyken `isActive()` hızlı çıkışı korunur.
- View olaylarında `ViewAttachDispatcher`, kaynak çözümlemede `ResIds`, keşifte mevcut DexKit önbelleği kullanılır.
- Log kilidi altında disk I/O yapılmaz; main thread'e ağ, parola türetme veya uzun disk işi eklenmez.

## 6. Aşamalar ve tamamlanma kapıları

Bağımlılık sırası: **Faz 0 → Faz 1 → Faz 2 → Faz 3 → Faz 4 → Faz 5 → Faz 6**. Faz 7 isteğe bağlıdır. Güvenlik, test ve belge işleri her aşamanın parçasıdır.

### Faz 0 — Başlangıç sürümünü ve ölçümleri sabitleme

- [ ] Commitlenmemiş değişiklikleri inceleyip hangi özelliklerin başlangıç sürümüne dahil edileceğini belirle; ilgisiz çalışmaları geçiş PR'ına katma.
- [ ] Temiz ve yeniden derlenebilir başlangıç commit'ini, APK checksum'unu ve imza sertifikası parmak izini kaydet.
- [ ] Özellik envanteri çıkar: ayar anahtarı, varsayılan değer, hook sınıfı, süreç, bağımlılıklar, desteklenen sürüm ve doğrulama durumu.
- [ ] Özellikle feed, harici bağlantı ve caption değişikliklerinin durumunu başlangıç commit'i üzerinden kesinleştir.
- [ ] Reflection, manifest ve IPC üzerinden kullanılan sınıf/metot/veri sözleşmelerini listele.
- [ ] Ayar yedeği, eski JSON örnekleri ve bozuk giriş örnekleri için kişisel veri içermeyen test fixture'ları oluştur.
- [ ] Mevcut JVM/lint/derleme sonuçlarını kaydet; bilinen hataları yeni gerilemelerden ayır.
- [ ] Bölüm 8'deki cihaz ve performans ölçümlerini referans APK üzerinde yap.
- [ ] Her özellik için test sorumlusu, cihaz erişimi ve eksik doğrulama alanlarını kaydet.

**Çıkış ölçütü:** Kaynak commit'i belli bir baseline, özellik matrisi ve ölçüm kaydı vardır. Cihaz erişimi olmayan alanlar açıkça “doğrulanmadı” olarak işaretlidir; kararlı sürüm için gerekli alanların eksikliği kapatılmadan kararlı yayına geçilmez.

### Faz 1 — Kotlin derleme altyapısı

- [ ] Mevcut AGP/Gradle/JDK ile uyumlu Kotlin sürümünü uygulama tarihinde resmi uyumluluk belgelerinden seç ve sürüm kataloğuna sabitle.
- [ ] Mevcut AGP 8 yapılandırmasına uygun Kotlin Android plugin'ini ekle; farklı AGP nesillerine ait kurulum yöntemlerini karıştırma.
- [ ] Java ve Kotlin JVM hedeflerini 17 olarak hizala; CI JDK'sını açıkça sabitle.
- [ ] Kaynak dizini tercihini belirle: `app/src/main/kotlin` ve `app/src/test/kotlin`; mevcut Java dizinleri korunur.
- [ ] Kotlin runtime sürüm/bağımlılık ağacını incele; yinelenen ve gereksiz bağımlılıkları tespit et.
- [ ] Gerçek bir saf mantık ihtiyacını karşılayan küçük Kotlin bileşeni ve Java'dan çağrı testi ekle; kullanılmayan örnek sınıf bırakma.
- [ ] Java'dan Kotlin'e ve Kotlin'den Java'ya çağrı içeren derlemeyi doğrula.
- [ ] Temiz debug/release derlemesini, libxposed metadata ve native kütüphanelerin APK içinde kaldığını kontrol et.
- [ ] APK boyutu, method sayısı ve temiz/artımlı derleme sürelerindeki farkı kaydet.
- [ ] `CONTRIBUTING.md` içinde Kotlin stilini ve birlikte kullanım kurallarını güncelle.

**Çıkış ölçütü:** Karma dil derlemesi CI'da geçer, mevcut davranış değişmemiştir, framework modülü yükleyebilir. Bu faza Compose, depolama değişimi veya hook dönüşümü eklenmez.

### Faz 2 — Düşük riskli pilot geçiş

- [ ] Tek bir aday seç: Android'den bağımsız sürüm karşılaştırma veya benzer sınırlı bir doğrulama/politika bileşeni.
- [ ] Dönüşüm öncesinde sınır durumlarını ve mevcut Java çağrılarını testlerle sabitle.
- [ ] Public API, exception ve null davranışını koruyarak Kotlin'e dönüştür.
- [ ] Dönüşüm sırasında mantık değişikliği gerekiyorsa ayrı commit/PR'a ayır.
- [ ] Eski ve yeni uygulamanın aynı fixture setinde aynı sonuçları verdiğini doğrula.
- [ ] Pilotun gerçek kazancını değerlendir: okunabilirlik, test edilebilirlik, bağımlılık maliyeti ve inceleme zorluğu.
- [ ] Pilot başarısızsa dönüşümü geri al; çalışan Kotlin altyapısını tutup tutmamayı ayrı değerlendir.

**Çıkış ölçütü:** Java çağıranlar değişmeden çalışır, anlamlı regresyon testleri geçer ve sonraki dönüşümler için belgelenmiş bir örnek oluşur.

### Faz 3 — Companion ekranlarını kademeli dönüştürme

Önerilen sıra: `HelpFragment` / basit ekran → `HomeFragment` → `LoggingFragment` → `FeaturesFragment` → `MainActivity` koordinasyonu → tema ve konum Activity'leri. Gerçek bağımlılık analizi sırayı değiştirebilir.

- [ ] İlk ekranda mevcut View/XML görünümünü koruyarak Kotlin'e geç.
- [ ] View referanslarını görünüm yaşam döngüsüne bağla; gerekiyorsa View Binding kullan.
- [ ] Ağ/disk/IPC işlerini Fragment gövdesinden küçük servis veya repository arayüzlerine çıkar.
- [ ] Karmaşık ekran durumlarında ViewModel kullan; basit statik ekranlara gereksiz katman ekleme.
- [ ] Yükleniyor/boş/başarılı/hata/iptal durumlarını açıkça modelle.
- [ ] Rotation, arka plana geçiş, geri dönüş ve süreç yeniden oluşturma davranışını doğrula.
- [ ] Ayar değişikliklerinin staging/commit davranışını, yeniden girişte yüklenmesini ve iki taraftan yapılan güncellemeleri koru.
- [ ] Log ekranında sınırlı buffer ve kişisel veri temizliği korunur; ekrandan çıkınca gereksiz toplama durur.
- [ ] Türkçe, İngilizce ve RTL dilde taşma; yazı boyutu, TalkBack, karanlık tema ve erişilebilirlik kontrolü yap.
- [ ] Bütün kullanıcı metinlerini kaynak dosyalarında tut; mevcut çeviri anahtarlarını koru.

**Çıkış ölçütü:** Dönüştürülen ekran başına davranış ve yaşam döngüsü kontrolleri geçer; UI yenilemesi gerekiyorsa dil dönüşümünden ayrı incelenir. Instagram içindeki `DialogUtils` menüsü bu fazın companion dönüşümüne dahil değildir.

### Faz 4 — Companion arka plan işleri

Önerilen sıra: güncelleme kontrolü → ayar yedekleme/geri yükleme → indirme koordinasyonu. `PasscodeHasher` gibi çalışan güvenlik bileşenleri yalnızca Kotlin oranını artırmak için dönüştürülmez.

- [ ] Her iş için sahip, scope, dispatcher, zaman aşımı, iptal ve yeniden deneme politikasını tanımla.
- [ ] Güncelleme kontrolünde eşzamanlı yinelenen istekleri ve ekrandan ayrılma davranışını yönet.
- [ ] Yedekleme/geri yüklemeyi main thread dışına taşı; doğrulamadan mevcut veriyi değiştirme.
- [ ] `DownloadSaveService` için bildirim, FGS başlatma kısıtları, servis kapanışı ve Android 15+ timeout davranışını koru.
- [ ] İndirme paralelliği ve kuyruk boyutunu açıkça sınırla; sınırsız coroutine başlatma.
- [ ] Timeout, iptal ve yeniden denemelerde stream/bağlantı kapanışını ve yarım dosya temizliğini doğrula.
- [ ] Tekrar denemede aynı medyanın yanlışlıkla çoğaltılmasını önle; mevcut isimlendirme politikasını belgele.
- [ ] CDN HTTPS allowlist, redirect doğrulaması, boyut sınırı ve dosya adı temizliğini koru.
- [ ] SAF izni kaybı, dolu depolama, ağ kesintisi, servis sonlandırılması ve çoklu indirmeyi test et.
- [ ] Video/ses birleştirme, carousel ve farklı medya türlerinde çıktı bütünlüğünü doğrula.

**Çıkış ölçütü:** İptal ve hata yolları dahil cihaz testleri geçer; kayıp/bozuk medya, sınırsız iş birikimi veya servis sızıntısı yoktur. Instagram içinde çalışan indirme yolları companion dönüşümüyle otomatik olarak değiştirilmez.

### Faz 5 — Ayar ve IPC sözleşmelerini güçlendirme

Bu fazın amacı depolama teknolojisini değiştirmek değildir. Gerekirse Kotlin arayüzü mevcut Java uygulamasını sarar.

- [ ] Ayar anahtarları, türleri, varsayılanları ve hangi tarafın hangi alan için otorite olduğu belgelenir.
- [ ] String tabanlı dağınık erişim için Java uyumlu, tipli bir sözleşme değerlendirilir.
- [ ] `FeatureFlags` hızlı okumaları korunur; hook yoluna disk okuma veya Flow aboneliği eklenmez.
- [ ] Companion cache, Instagram tercihleri ve remote preferences arasında offline/yeniden başlatma senaryoları test edilir.
- [ ] Paket hedefleme, imza izni ve nonce eşleştirmesi bütün ilgili iletişim yollarında korunur.
- [ ] Eksik, yanlış tipli, fazla büyük veya yetkisiz verilerle negatif testler eklenir.
- [ ] Parola/hash/token gibi sırlar IPC ve loglara aktarılmaz.
- [ ] JSON/ayar şeması gerçekten değişecekse sürüm, yükseltme, bozuk veri kurtarma ve eski sürüm davranışı ayrıca tanımlanır.
- [ ] Yazma işlemleri atomik kalır; başarısız import mevcut ayarları silmez.
- [ ] Framework remote preference desteği olmayan kurulumlarda mevcut fallback yolu cihazda doğrulanır.

**Çıkış ölçütü:** Eski ayarlar ve yedekler okunur, süreçler arası eşitleme çalışır, yetkisiz istekler reddedilir ve dönüşüm sonrası veri kaybı görülmez.

### Faz 6 — Regresyon, test yayını ve kararlılaştırma

- [ ] Bölüm 8–10'daki matrisi hedef APK üzerinde tamamla.
- [ ] Baseline ile aynı koşullarda performans ve boyut karşılaştırması yap.
- [ ] P0/P1 sorunları kapat; çözülmeyen daha düşük öncelikli sorunları sürüm notunda belirt.
- [ ] Her değişikliği belirli bir commit'e bağlayan prerelease üret.
- [ ] Test kullanıcıları için cihaz/Android/Instagram/framework sürümü ve tekrar adımı içeren rapor şablonu hazırla.
- [ ] Her küçük dönüşüm grubundan sonra test sürümü yayımla; büyük toplu dönüşümün sonunu bekleme.
- [ ] Test süresini ve yeterli cihaz kapsamasını release öncesinde tanımla; yalnızca “şikâyet gelmedi” sonucuna dayanma.
- [ ] Mimari, katkı, değişiklik ve bilinen sorun belgelerini güncelle.
- [ ] Geri dönüş adımlarını gerçek ayar yedeği ve seçilmiş sürümlerle doğrula.

**Çıkış ölçütü:** Kaynak, artifact, imza, test raporu ve bilinen sınırlamalar birbiriyle eşleşir. Kararlı sürüm için gereken tüm kapılar kapanmıştır.

### Faz 7 — İsteğe bağlı mimari iyileştirmeler

Yalnızca önceki fazlar kararlı hale geldiğinde ve somut fayda gösterildiğinde:

- [ ] Gradle modül ayrımını döngüsel bağımlılık ve derleme süresi verilerine göre değerlendir.
- [ ] Bir companion ekranında Compose prototipi için ayrı karar kaydı hazırla; APK boyutu ve performans etkisini ölç.
- [ ] Hook kaydı için Kotlin DSL ancak Java API'sine göre bakım kazancı ve düşük runtime maliyeti gösterirse değerlendir.
- [ ] R8'i ayrı bir çalışma olarak ele al; reflection, JNI, serialization, metadata ve giriş sınıfları için keep kuralları/testleri oluştur.
- [ ] Depolama veya DI değişikliğini kendi migration ve geri dönüş planıyla ele al.

**Çıkış ölçütü:** Her öneri için kabul/ret ve gerekçe kaydı vardır. Bu fazın yapılmaması hibrit geçişin başarısızlığı sayılmaz.

## 7. Ayar, veri ve güvenlik değişmezleri

1. Uygulama kimliği ve mevcut kurulumun erişmesi gereken veri yolları dil dönüşümü nedeniyle değiştirilmez.
2. Ayar anahtarları ve varsayılanları açık bir ürün kararı olmadan değiştirilmez.
3. Hidden chats, unsent mesaj kayıtları, story cache ve DM lock verileri dönüşümden önce/sonra karşılaştırılır; hassas kayıtlar test raporuna kopyalanmaz.
4. Eski parola hash'inin yükseltilmesi, PBKDF2 doğrulaması ve kilitlenme davranışı korunur.
5. İmza izniyle korunan companion → Instagram iletişimi gevşetilmez.
6. Instagram sürecinin companion ile aynı UID/izinlere sahip olduğu varsayılmaz; ters yöndeki Activity/Service erişimi ayrıca doğrulanır.
7. Exported component girdileri ve URI izinleri güvenilir kabul edilmez.
8. Yeni bağımlılıklar ihtiyaç, bakım durumu, lisans ve APK/runtime etkisi açısından incelenir.
9. Release imzalama anahtarları, token'lar ve gerçek kullanıcı verileri depoya veya CI loglarına yazılmaz.
10. Veri şeması değişirse eski sürümün veriyi okuyacağı varsayılmaz; geri dönüş yolu ayrı test edilir.

## 8. Test stratejisi ve cihaz matrisi

### 8.1 Otomatik kontroller

Mevcut temel kontrol:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Kotlin altyapısı ve paketleme değiştiğinde ayrıca:

```bash
./gradlew assembleRelease
```

`assembleRelease` başarısı imzalı, dağıtıma hazır APK üretildiği anlamına gelmez; signing yapılandırması ayrıca doğrulanır. `clean` her çalıştırmada zorunlu değildir; Faz 1 ve sürüm kapısında temiz ortam derlemesi yapılır.

Test kapsamı:

- Hook interceptor zinciri: argüman değiştirme, erken sonuç/exception, after callback ve callback hata izolasyonu.
- Java–Kotlin imzaları, null davranışı, serialization ve sınır değerler.
- Ayar import/export uyumluluğu, atomik yazma ve bozuk veri.
- URL/redirect/dosya adı/boyut doğrulama politikaları.
- Coroutine iptali, tekrar giriş, timeout ve sınırlı paralellik; coroutine eklenirse uygun test scheduler kullanımı.
- Fragment/Activity yaşam döngüsü, permission ve exported component davranışları için gerekli instrumentation testleri.

JVM testleri gerçek libxposed yüklemesini, ClassLoader'ı veya Instagram davranışını doğrulamaz. Cihaz kontrolleri ayrıca gereklidir. Sadece satırları birebir tekrar eden testler yerine veri kaybı ve davranış gerilemesini yakalayan testler yazılır.

### 8.2 Asgari platform kapsamı

| Boyut | Kontrol |
| --- | --- |
| Minimum Android | API 28'de companion kurulumu/açılışı; hook desteği test edilen framework'e göre ayrıca belirtilir |
| Güncel hedef | Android 16 / API 36'da tam uçtan uca kontrol |
| Servis kısıtları | Android 14/15 veya eşdeğer kapsama sağlayan cihazlarda FGS/bildirim/timeout kontrolleri |
| Framework | API 101 destekleyen kullanılan framework'ün tam sürümü kaydedilir |
| LSPatch | Destek iddia edilecekse API 101 destekli gerçek kurulumda ayrı doğrulama yapılır |
| Instagram | Referans sürüm, yayın anında hedeflenen güncel sürüm ve desteklenen önceki sürüm kaydedilir |
| Paket varyantı | Resmi paket ve gerçekten destek iddiası olan varyantlar; paket allowlist'i test kanıtı sayılmaz |
| Donanım | Mümkünse düşük/orta bellekli cihaz ve en az bir fiziksel arm64 cihaz |
| Kurulum | Temiz kurulum, aynı anahtarla yükseltme, backup/restore ve süreç yeniden başlatma |
| Dil/erişilebilirlik | Türkçe, İngilizce, RTL, büyük font, TalkBack ve karanlık tema |

Her satır için cihaz modeli, ABI, Android build'i, Instagram sürümü, framework sürümü, APK commit'i, sonuç ve tarih tutulur. Erişilemeyen kombinasyon “test edildi” olarak sunulmaz.

### 8.3 Özellik regresyon matrisi

Her test, ilgili özellik açıkken ve kapalıyken normal Instagram davranışını da kapsar.

| Grup | Asgari senaryolar |
| --- | --- |
| Başlatma | Companion/Instagram cold ve warm start, modül etkin/pasif, desteklenmeyen süreç, Instagram güncellemesi sonrası ilk açılış |
| Ayarlar | Companion ve Instagram menüsünden değişiklik, uygulama kapalıyken değişiklik, yeniden başlatma ve eşitleme |
| Gizlilik | DM/story seen, typing, screenshot, view-once, kaybolan mesaj ve manuel mark-as-read akışları |
| Sohbet verileri | Gizli sohbetler, unsent kayıtları, DM lock doğru/yanlış parola, lockout ve geri yükleme |
| Feed/UI | Reklam/öneri filtreleri, reels/story kapatma, DM istisnası, Meta AI temizleme, zoom ve menüler |
| Medya | Fotoğraf, video, reel, story, carousel, profil fotoğrafı, ses birleştirme, adlandırma, SAF/default klasör |
| Yardımcı özellikler | Caption/comment kopyalama, mention/follow göstergeleri, autoplay ve çift dokunma tercihleri |
| Tema/konum | Tema, font, renkler, konum seçimi ve bunların Instagram tarafındaki etkileri |
| Geliştirici ayarları | JSON import/export, bozuk/uyumsuz dosya ve yetkisiz çağrı |
| Yeni özellikler | Faz 0'da kabul edilen following-only feed, harici bağlantı ve diğer ek özelliklerin ayrı senaryoları |

Bu tablo başlangıç kapsamıdır; kesin özellik listesi Faz 0 envanterindeki her anahtarı kapsamalıdır.

## 9. Performans ve boyut kabul ölçütleri

Aşağıdaki eşikler başlangıçta önerilen inceleme eşikleridir; mevcut ölçüm sonucu veya performans garantisi değildir. Faz 0'da cihaz gürültüsü ölçülür ve eşikler sabitlenir. Başarısız sonuçtan sonra gerekçesiz gevşetilmez.

| Ölçüm | Yöntem | Önerilen kapı |
| --- | --- | --- |
| Cold/warm başlangıç | Aynı cihaz/sürümlerde en az 10 tekrar; median, dağılım ve ham sonuç | Tekrarlanabilir median artışı %5'i aşarsa inceleme ve gerekçe olmadan ilerleme yok |
| Güncelleme sonrası ilk açılış | DexKit cache temizliği kontrollü, aynı senaryo | Yeni ANR yok; keşif süresi kaydedilir |
| Warm start DexKit | Log/izleme ile bridge açılış sayısı | Cache geçerliyken gereksiz bridge açılışı yok |
| Kaydırma | Sabit feed/reels senaryosu, frame süreleri ve jank ölçümü | Tekrarlanabilir gerileme açıklanıp giderilmeden kabul yok |
| Bellek | Companion ve Instagram için ayrı PSS/heap; aynı iş yükü | Sürekli büyüme veya lifecycle sızıntısı yok; kararlı PSS artışı %10'u aşarsa inceleme |
| Hook maliyeti | Sık callback örneklerinde süre/allocation profili | Özellik kapalıyken yeni I/O, keşif veya allocation yoğun yol yok |
| İndirme | Aynı medya ve ağ koşulları; süre, hata, iptal ve çıktı doğruluğu | Sınırsız concurrency, bozuk/eksik çıktı veya kaynak sızıntısı yok |
| APK/method sayısı | Aynı build türü ve imza koşulunda karşılaştırma | Her artış kaydedilir; APK artışı %10'u aşarsa bağımlılık incelemesi |
| Derleme süresi | Aynı makinede temiz ve artımlı derleme ayrı | Yeni darboğazlar kaydedilir; tekrarlanabilir %20 artış incelenir |

Küçük örneklemden güvenilir p95/p99 sonucu çıkarılmaz; tail latency kararı için daha fazla tekrar yapılır. Ağ içeriği, termal durum ve önbellek farkları karşılaştırmayı bozuyorsa ölçüm yeniden tasarlanır. Dil değişimi için hız kazanımı iddiası yalnızca bu ölçümlerle desteklenirse yazılır.

## 10. CI, sürümleme ve APK dağıtımı

### 10.1 CI işleri

- [ ] Mevcut workflow yalnızca `main`/`0.4` push'ları, `main` hedefli PR'lar ve manuel çalıştırmaları kapsıyor; migration dalının gerekli kontrolleri otomatik aldığını doğrula.
- [ ] JDK/Gradle/Kotlin sürümlerini sabitle; wrapper doğrulamasını koru.
- [ ] Java ve Kotlin testleri, lint ve debug derlemesini zorunlu kontrol yap.
- [ ] Paketleme değişikliklerinde release derlemesini ekle.
- [ ] Lint ve test raporlarını başarısızlıkta da artifact olarak sakla.
- [ ] Güvenilmeyen PR'lara yayın token'ı veya signing secret açma.
- [ ] Build işlerinde salt-okuma iznini koru; yayın için yazma iznini yalnızca ilgili işe ver.

### 10.2 Sürüm politikası

- Test sürümleri GitHub prerelease olarak işaretlenir; kararlı sürümün “latest” işaretini değiştirmez.
- Etiket, sürüm adı ve `versionCode` politikası Faz 0'da belirlenir. Sonraki dağıtılacak güncellemelerde `versionCode` artar.
- Mevcut `v0.7.0-test.1` APK'sının dahili sürümü `0.7.0` / `17`'dir; ileride test sürümünün uygulama içinde de ayırt edilmesi sağlanır.
- Aynı commit'ten üretilmiş APK, SHA-256, imza sertifikası bilgisi, test özeti ve bilinen sorunlar birlikte yayımlanır.
- Test ve kararlı sürümler için imza stratejisi belirlenir. Geçici CI debug anahtarlarının güncellemeyi bozabileceği dikkate alınır; anahtarlar güvenli biçimde saklanır.
- Farklı applicationId ile yan yana test kurulumu istenirse signature permission, IPC hedefleri, provider authority ve framework scope etkileri için ayrı tasarım yapılır.
- APK imzası `apksigner verify` ile kontrol edilir; asset yüklenmesi ve release'in prerelease/draft durumu yayın sonunda doğrulanır.

### 10.3 Sürüm notunda zorunlu bilgiler

- Kaynak commit'i, tag, uygulama sürümü ve build türü.
- Bu sürümde dönüştürülen alanlar ve kullanıcıya yansıyan değişiklikler.
- Minimum Android ve gereken libxposed API desteği.
- Gerçekte test edilen cihaz/Instagram/framework kombinasyonları.
- Bilinen sorunlar, test edilmeyen alanlar ve veri uyumluluğu.
- İmza uyumluluğu, yükseltme ve gerekiyorsa yedekleme adımları.
- Artifact checksum'u ve sorun bildirme yöntemi.

## 11. Geri dönüş ve sorun yönetimi

### 11.1 Geri dönüş tetikleyicileri

- Yeni crash/ANR veya modülün yüklenememesi.
- Ayar, mesaj kaydı veya medya kaybı/bozulması.
- Yetkisiz IPC erişimi ya da gizli verinin dışarı çıkması.
- Tekrarlanabilir ve kabul edilmemiş performans gerilemesi.
- Yaygın kurulum/imza hatası veya temel özelliğin çalışmaması.

### 11.2 Uygulama yöntemi

1. Sorunu kaynak commit'i ve cihaz matrisiyle eşleştir; yeni yayını durdur veya sorunlu sürümü açıkça işaretle.
2. İlgili dönüşüm PR'ını revert et; kullanıcıların eski APK'ya doğrudan düşebileceğini varsayma.
3. Tercihen önceki kararlı davranışı geri getiren, daha yüksek `versionCode` içeren aynı anahtarla imzalı düzeltme sürümü üret.
4. Şema değişmişse ters migration veya önceden doğrulanmış yedek geri yükleme yolunu kullan.
5. Yeniden kurulum gerekiyorsa veri kaybı ve imza koşullarını sürüm notunda açıkla.
6. Regresyon testi ekle ve geri dönüş APK'sını da temel kontrollerden geçir.

Kaynak kodunu revert etmek kullanıcı verisini kendiliğinden eski haline getirmez. Riskli veri şeması değişiklikleri bu nedenle dil dönüşümünden ayrı tutulur.

### 11.3 Önceliklendirme

| Öncelik | Örnek | Yayın kararı |
| --- | --- | --- |
| P0 | Veri kaybı, güvenlik açığı, yaygın açılış çökmesi | Yayını durdur; acil düzeltme/geri dönüş |
| P1 | Temel özellik çalışmıyor, tekrarlanabilir ANR, kurulum engeli | Kararlı yayını engeller |
| P2 | Sınırlı kombinasyonda bozulma, kullanılabilir workaround | Etki ve destek kapsamına göre karar, bilinen sorun kaydı |
| P3 | Kozmetik sorun, küçük belge/UX eksiği | Planlı bakım |

## 12. Risk kaydı

| Risk | Erken belirti | Önlem / geri dönüş |
| --- | --- | --- |
| Kotlin runtime/ClassLoader uyuşmazlığı | Modül yüklenirken sınıf/metot bulunamaması | Faz 1 cihaz kapısı, host bağımlılığına güvenmeme, altyapı revert'i |
| Java API imzasının değişmesi | Reflection veya Java çağrıları kırılır | İmza/sözleşme testleri, eski facade'ı koruma |
| Yaşam döngüsü hatası | Yinelenen istek, kapanmış ekrana callback | Scope sahipliği, iptal ve recreation testleri |
| Ayar uyuşmazlığı | Companion ile Instagram farklı değer gösterir | Alan bazında otorite ve offline sync testleri |
| Veri formatı bozulması | Eski yedek okunamıyor veya varsayılanlar değişiyor | Fixture testleri, sürümlü migration, atomik yazma |
| Güvenlik gerilemesi | İzin/nonce doğrulaması atlanıyor | Negatif IPC testleri, exported component incelemesi |
| Sıcak yol yükü | Kaydırma takılması, allocation artışı | Hook çekirdeğini koruma, profil ve cache kontrolleri |
| APK/derleme büyümesi | Runtime veya UI bağımlılıkları gereksiz artıyor | Minimal bağımlılık, Compose/DI'yi ayrı karar olarak tutma |
| Instagram güncellemesi | Obfuscated hedefler bulunamıyor | Sürüm matrisi, cache invalidation, özelliği güvenli devre dışı bırakma |
| Çok geniş dönüşüm | Sorunun hangi değişiklikten geldiği belirsiz | Küçük PR, tek sorumluluk, faz bazlı prerelease |
| İmza süreksizliği | Güncelleme kurulamaz | Sabit signing stratejisi ve gerçek yükseltme testi |

## 13. İlk iş listesi ve PR sırası

| Sıra | İş | Önkoşul | Somut çıktı |
| --- | --- | --- | --- |
| 1 | Başlangıç commit'i ve özellik envanteri | Mevcut çalışma ağacının kapsamı net | Baseline/test matrisi |
| 2 | Kotlin build desteği | Faz 0 tamam | Sürüm kataloğu, plugin, JVM hedefi, karma dil doğrulaması |
| 3 | Saf mantık pilotu | Faz 1 cihaz/CI kapısı | Küçük Kotlin bileşeni ve Java uyumluluk testleri |
| 4 | İlk companion ekranı | Pilot başarılı | XML korunmuş Kotlin ekranı ve yaşam döngüsü doğrulaması |
| 5 | Güncelleme kontrolü | Scope politikası belirlenmiş | Test edilmiş iptal/timeout ve ekran entegrasyonu |
| 6 | Diğer companion ekranları | Önceki ekran kararlı | Ekran başına küçük PR ve test kaydı |
| 7 | Backup/restore | Veri fixture'ları hazır | Uyumluluk ve bozuk veri testleri |
| 8 | İndirme koordinasyonu | Servis/FGS test cihazları hazır | Sınırlı concurrency, iptal, hata ve çıktı testleri |
| 9 | Ayar/IPC adaptörleri | Envanter ve sözleşmeler hazır | Tipli sınırlar ve uçtan uca sync doğrulaması |
| 10 | Kararlılaştırma | İlgili fazlar tamam | Ölçüm raporu, prerelease, kararlı yayın kararı |

Bir aşamanın tek PR olması gerekmez. Bir PR da birden fazla bağımsız aşamayı kapsamaz. Bağımlılık gerektirmeyen test hazırlığı erken yapılabilir; güvenlik düzeltmeleri geçiş takvimini beklemez.

## 14. Her dönüşüm PR'ı için tamamlanma ölçütleri

- [ ] Değişikliğin amacı ve Kotlin'e geçiş gerekçesi açık.
- [ ] Etkilenen süreç, thread ve ClassLoader sınırı belirlenmiş.
- [ ] Java çağrıları, reflection, manifest ve serialization sözleşmeleri incelenmiş.
- [ ] Ayar/veri/IPC davranışı korunmuş veya ayrı migration belgelenmiş.
- [ ] Gerekli regresyon testleri, lint ve derleme geçmiş.
- [ ] Riskle orantılı cihaz testi yapılmış; yapılmayanlar açıkça belirtilmiş.
- [ ] Yeni bağımlılıkların gerekçesi ve maliyeti kaydedilmiş.
- [ ] Performansı etkileyen değişiklikler baseline ile karşılaştırılmış.
- [ ] Geri dönüş yöntemi uygulanabilir ve veri uyumluluğu belli.
- [ ] Kullanıcıya yansıyan değişiklikler ve ilgili belgeler güncellenmiş.
- [ ] İlgisiz biçimlendirme, toplu yeniden adlandırma ve özellik değişiklikleri ayrılmış.

## 15. İlerleme takibi ve açık kararlar

Her fazın kaydı şu alanları içermelidir:

| Alan | Beklenen kayıt |
| --- | --- |
| Durum | Başlamadı / sürüyor / doğrulama bekliyor / tamamlandı |
| Sorumlu | Uygulama ve inceleme sorumlusu |
| Kaynak | Issue/PR ve commit bağlantısı |
| Kanıt | CI, cihaz testi, ölçüm ve artifact bağlantısı |
| Kalan işler | Açık hata, eksik cihaz ve kararlar |
| Geri dönüş | Revert/düzeltme sürümü ve veri uyumluluğu |

Uygulamadan önce çözülecek kararlar:

- [ ] Temiz başlangıç commit'i ve dahil edilecek mevcut özellikler.
- [ ] Mevcut toolchain ile doğrulanmış Kotlin sürümü.
- [ ] Pilot bileşen ve ilk ekran.
- [ ] Test cihazları ve destek iddiasının sınırları.
- [ ] Test/kararlı imza anahtarı ve `versionCode` stratejisi.
- [ ] Ölçüm eşikleri, release test süresi ve sorumlular.

Bu kararlar dışındaki olağan uygulama ayrıntıları mevcut mimari ve katkı kuralları doğrultusunda çözülür. Yeni mimari tercih doğduğunda gerekçesi, alternatifleri ve etkisi kısa bir karar kaydına eklenir.

## 16. Başvuru kaynakları

- [Android — Mevcut uygulamaya Kotlin ekleme](https://developer.android.com/kotlin/add-kotlin)
- [Kotlin — Java birlikte kullanımı](https://kotlinlang.org/docs/java-interop.html)
- [Morphe Patcher — çalışma modeli](https://github.com/MorpheApp/morphe-patcher)

Bu bağlantılar yaklaşımın dayanağıdır. Uygulama başladığında güncel sürüm uyumluluğu ayrıca doğrulanmalı; örnek bir dokümandaki sürüm numarası doğrudan projeye kopyalanmamalıdır.
