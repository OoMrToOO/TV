# Hobi TV — GitHub + Otomatik APK

## 1. GitHub repository oluştur
GitHub'da **New repository** seç.
Örneğin: `hobi-tv`

Repository'yi oluşturduktan sonra bu projenin tüm dosyalarını yükle.

## 2. TMDB token'ı GitHub'a ekle
Repository:
**Settings → Secrets and variables → Actions → New repository secret**

Name:
`TMDB_TOKEN`

Value:
TMDB token'ın.

Token'ı kodun içine veya GitHub dosyalarına yazma.

## 3. APK oluştur
GitHub'da:
**Actions → Build Hobi TV APK → Run workflow**

Build tamamlanınca workflow içindeki **Artifacts** bölümünden:
`HobiTV-debug`
dosyasını indir.

## 4. Android TV'ye kur
APK'yı TV'ye USB, Google Drive, ağ paylaşımı veya ADB ile aktarabilirsin.

ADB örneği:
`adb install HobiTV-debug.apk`

TV'de gerekirse bilinmeyen kaynaklardan uygulama yükleme iznini aç.

## Not
Debug APK geliştirme/test içindir. Mağaza dağıtımı için imzalı release keystore ve release workflow ayrıca eklenmelidir.
