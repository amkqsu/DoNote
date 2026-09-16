# DoNote 4.00

DoNote, tamamen yerel çalışan Android not uygulamasıdır.

## Özellikler

- Kotlin + Jetpack Compose arayüz.
- Room tabanlı yerel veritabanı.
- Metin, yapılacak, başlık, madde, alıntı ve resim blokları.
- Kategori renkleri ve geniş ikon kütüphanesi.
- Özel renk seçici.
- Not içinde arama ve panodan hızlı yapıştırma.
- Android paylaş menüsünden DoNote'a metin gönderme.
- JSON içe/dışa aktarma.
- Hatırlatmalar.
- Tek veya çoklu not gösterebilen yeniden yapılandırılabilir widget.
- Widget üzerinden yapılacak maddelerini işaretleme.
- PIN ile not kilitleme ve isteğe bağlı ortak varsayılan PIN.
- Yüksek yenileme hızlı ekranlarda cihazın desteklediği en yüksek aynı çözünürlüklü görüntü modunu isteme.
- Hafif geçiş animasyonları ve ayarlanabilir animasyon hızı.

## Kilit sistemi

Kilitli not içeriği AES-256-GCM ile şifrelenir. 6–12 rakamlı PIN, PBKDF2-HMAC-SHA256 ile not anahtarını korur. Ayarlardaki varsayılan PIN Android Keystore ile cihaz üzerinde şifreli biçimde saklanır. Her not için varsayılan PIN veya ayrı bir PIN seçilebilir.

PIN unutulursa ayrı PIN ile kilitlenmiş notun içeriği kurtarılamaz. Kilitli içerik kart, arama, widget ve bildirim önizlemelerinde gösterilmez.

## Derleme

JDK 17 gerekir. Release imzası için GitHub Actions secrets:

- `DONOTE_KEYSTORE_BASE64`
- `DONOTE_SIGN_PASSWORD`

Uygulama kimliği: `fun.dogon.note`
