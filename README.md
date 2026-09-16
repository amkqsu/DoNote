# DoNote 4.00

Türkçe native Android not uygulaması. Paket: `fun.dogon.note`. Android 8.0 ve sonrası. Kotlin, Jetpack Compose ve Room. DoFit tema kaynaklarındaki renk, yazı tipi ve kart biçimleri kullanılır. Uygulama internet izni istemez.

## Özellikler

- Arama, yatay kategori sekmeleri, her kartta kopyalama ikonu ve sabit ekleme düğmesi.
- Kalın, italik, altı/üstü çizili metin; yapılacaklar, resimler, bağlantılar, başlıklar, maddeler ve alıntılar.
- Otomatik kayıt, düzenleyicide geri al/yinele, basılı tutma menüsü, sürükleyerek sıralama, sabitleme ve silmede geri alma.
- Düzenlenebilir kategoriler, renkler, öne çıkan kategori ve isteğe bağlı kullanım sırası.
- Hazır 24 ikon, renk seçimi ve ikonsuz notlar. İkon widget ve bildirimlerde de görünür.
- Not başına PIN, uygun cihazlarda güçlü biyometri ve kurtarma PIN'i.
- Yalnızca Ayarlar'dan erişilen arşiv.
- 24 saat biçiminde tek seferlik, saatlik, günlük, haftalık, aylık, yıllık ve seçili günlerde hatırlatma.
- Seçilebilir ve yeniden boyutlandırılabilir Android widget'ı.
- Not, gömülü resim, kategori ve tercihleri içeren JSON yedeği. Mevcut notlar korunur; aynı kimlikli notlar atlanır.
- İsteğe bağlı sayaç, hızlı filtreler, animasyonlar ve vurgu rengi.

Metni biçimlendirmek için önce metni seçin, ardından alttaki yatay araç çubuğunu kullanın. Kartın sağındaki tutamacı basılı tutup sürükleyerek sırayı değiştirin. Sabitlenmiş notlar kendi grubunda sıralanır. Kopyalama, başlık ve içeriği düz metin olarak aktarır; resimler `[Resim]` olarak gösterilir.

## Gizlilik ve sınırlar

Kilitli notların başlığı, içeriği ve resimleri AES-256-GCM ile şifrelenir. 6–12 rakamlı PIN, 210.000 turlu PBKDF2-HMAC-SHA256 ile not anahtarını korur. Biyometrik anahtar Android Keystore'dadır. PIN unutulursa içerik kurtarılamaz. Beş hatalı girişten sonra 30 saniye bekleme uygulanır.

Kilitli içerik arama, kart, widget ve bildirim önizlemesinde gösterilmez. Uygulama arka plana geçince kilit yeniden devreye girer; uygulamanın başlattığı resim seçimi sırasında düzenleme oturumu korunur. Bulut yedeği kapalıdır. Uygulamayı kaldırmak yerel verileri siler.

JSON'da kilitli notlar şifreli, diğerleri okunabilir biçimdedir. Başka cihazda biyometri yeniden etkinleştirilir; PIN korunur. Hazır ikon kütüphanesi aktarılmaz, yalnızca seçilen ikon kimliği korunur. Yedek en fazla 64 MB, 5000 not ve 200 kategori olabilir. Not başına 500 bölüm, 12 resim ve resimli içerikte 24 MB sınırı vardır. Resimler en fazla 1600 piksel boyutunda JPEG'e çevrilir, kamera yönü düzeltilir; resim başına 3 MB sınırı vardır.

Bildirim izni ve Android 12+ tam alarm erişimi kullanıcı tarafından verilmelidir. Tam alarm izni yoksa Android bildirimi geciktirebilir. Aylık eksik günler ayın sonuna uyarlanır; sonraki ay asıl güne dönülür. Arşivlenen notlarda alarmlar duraklar. Zorla durdurulan uygulamanın hatırlatmaları yeniden açılana kadar çalışmaz; cihaz kapalıyken bildirim üretilemez.

## Derleme

JDK 17, Android SDK Platform 35 ve Build Tools 35.0.0 gereklidir. Gradle 8.13, AGP 8.9.1 ve Kotlin 2.0.21 sabitlenmiştir. Projeyi Android Studio'da açın veya `ANDROID_HOME` tanımlayın. Yerel SDK yolu arşive konulmaz.

```bash
./gradlew assembleDebug testDebugUnitTest
```

Release için `DONOTE_SIGN_PASSWORD` ortam değişkenini `signing-password.txt` içindeki değere ayarlayın:

```bash
./gradlew assembleRelease lintRelease
```

Bu özel arşiv `donote-release.jks` ve imzalama parolasını içerir. Güncellemelerin aynı uygulama üzerine kurulabilmesi için saklayın; herkese açık repoya yüklemeyin. `.gitignore` bu özel dosyaları dışlar.

Cihaz testleri `app/src/androidTest` içindedir; test kayıtları oluşturup siler, yalnızca test cihazında çalıştırılmalıdır. Doğrulamanın kapsamı `verification/validation.txt` dosyasındadır.
