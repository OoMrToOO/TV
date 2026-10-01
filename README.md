# Hobi TV — Android TV

Hobi TV is a premium Android TV streaming client foundation with TMDB catalog integration and an authorized IPTV/VOD provider layer.

## Included
- Netflix-style dark/cinematic TV interface
- Android TV focus/remote navigation
- TMDB trending, popular movies, popular TV and multi-search
- Turkish metadata (`tr-TR`)
- TMDB poster/backdrop loading
- Movie/TV detail pages and YouTube trailer opening
- Favorites stored locally
- Continue Watching / recent history stored locally
- Media3/ExoPlayer playback with HLS/DASH support
- M3U provider import
- Xtream provider integration for live/VOD/series catalogs
- Provider settings screen
- Live TV, Movies, Series and Favorites sections

## TMDB token
TMDB supports API Read Access Tokens as Bearer authentication. Do not commit credentials to source control.

1. Copy `local.properties.example` to `local.properties`.
2. Put your own token in `TMDB_TOKEN=...`.
3. Build the app locally.

The token is intentionally NOT included in this ZIP.

For production distribution, move TMDB requests behind your own backend so credentials are not bundled in the APK.

## Provider
Open **Settings → Yayın Kaynağı**.

### M3U
Select M3U and enter the provider's authorized M3U URL. The parser classifies entries into Live / Movie / Series based on `group-title` and creates playable stream URLs.

### Xtream
Select Xtream and enter the authorized server URL, username and password. The client loads live streams, VOD streams and series metadata from the provider API.

Only use sources you are authorized to access and redistribute.

## TMDB attribution
If you distribute Hobi TV using TMDB data/images, review the current TMDB API terms and attribution requirements for your use case. TMDB states that its API is available for non-commercial use subject to its terms and that applications using the API must provide attribution.

## Build note
This package is source code. An Android SDK/Gradle build environment is required to produce an APK. The current workspace does not contain a Gradle executable, so an APK was not claimed as built or tested here.

## Stalker / Ministra Portal

Hobi TV ayrıca Stalker/Ministra portal kaynağını destekler. Uygulamada Ayarlar > Yayın Kaynağı > Stalker seçin ve sağlayıcınızın verdiği portal URL'sini ve kayıtlı MAC adresini girin. Serial Number, Device ID ve Device ID 2 alanları yalnızca sağlayıcınız bunları verdiyse kullanılmalıdır.

Stalker oturumunda tipik olarak handshake, STB profile/auth ve ardından canlı/VOD/series listeleri çağrılır; oynatma için portalın `create_link` akışı kullanılır. Bu akış portal sürümüne göre farklılık gösterebilir. Hobi TV, portalın döndürdüğü oynatma URL'sini Media3 oynatıcıya aktarır.

Portal ve MAC bilgileri cihazın DataStore alanında tutulur; proje ZIP'ine gerçek kullanıcı bilgileri dahil edilmez.
