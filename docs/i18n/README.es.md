<div align="center">

<img src="../assets/logo.svg" width="96" alt="Logotipo de Monologue">

# Monologue

**Un reproductor de música para Android, cálido y con estilo de vinilo.**<br>
Tu propia música —del teléfono y de Google Drive— con letras sincronizadas, romaji y traducciones.

[![Release](https://img.shields.io/github/v/release/HKmario852/monologue?style=flat-square&color=A74932)](https://github.com/HKmario852/monologue/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/HKmario852/monologue/total?style=flat-square&color=2F5D50)](https://github.com/HKmario852/monologue/releases)
![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?style=flat-square&logo=kotlin&logoColor=white)
[![License](https://img.shields.io/github/license/HKmario852/monologue?style=flat-square&color=55606B)](../../LICENSE)

[English](../../README.md) | [繁體中文](README.zh-TW.md) | [简体中文](README.zh-CN.md) | [日本語](README.ja.md) | [한국어](README.ko.md) | **Español**

<img src="../screenshots/player.png" width="200" alt="Reproduciendo">&nbsp;
<img src="../screenshots/lyrics.png" width="200" alt="Letra con traducción">&nbsp;
<img src="../screenshots/library.png" width="200" alt="Biblioteca">

</div>

## ✨ Funciones

- **🎵 Tu biblioteca** — canciones, artistas, álbumes y carpetas del teléfono, además de listas, favoritos y cola de reproducción.
- **☁️ Google Drive** — explora tu música en Drive, escúchala en streaming o descárgala para escucharla sin conexión.
- **📝 Letras** — letras sincronizadas que avanzan con la canción; romaji generado en el teléfono para letras en japonés; traducciones al chino hechas por personas, buscadas automáticamente, con traducción automática en el teléfono como alternativa; letras a pantalla completa.
- **🔎 Buscar y descubrir** — un solo buscador para tu biblioteca, Drive y música en línea, además de categorías de YouTube Music.
- **📊 Resumen de escucha** — tus canciones más escuchadas por semana, mes o de siempre, y scrobbling opcional a ListenBrainz.
- **🎚️ Reproducción** — ecualizador, temporizador, silencio entre canciones y reanudación tras una llamada o un video de otra app.
- **🎨 Diseño** — temas papel cálido y oscuro, un vinilo que gira y una animación de inicio de 3 segundos (se puede desactivar).
- **⬆️ Actualizaciones en la app** — nuevas versiones desde GitHub Releases, verificadas con SHA-256 y la firma de la app.

> [!NOTE]
> Por ahora, la interfaz de la app solo está en chino tradicional.

## 📥 Descarga

1. Descarga `monologue-x.y.z.apk` desde **[Releases](https://github.com/HKmario852/monologue/releases/latest)** e instálalo (Android 8.0 o posterior).
2. Las versiones siguientes se instalan desde la app: **設定 (Ajustes) › App 更新 (Actualizaciones)**.

<details>
<summary><b>Más capturas</b></summary>
<br>
<p align="center">
<img src="../screenshots/search.png" width="200" alt="Buscar">&nbsp;
<img src="../screenshots/settings.png" width="200" alt="Ajustes">&nbsp;
<img src="../screenshots/dark.png" width="200" alt="Tema oscuro">
</p>
<p align="center"><sub>Todas las capturas usan canciones y letras de demostración.</sub></p>
</details>

## 📝 Fuentes de letras

Las letras en línea están desactivadas hasta que las actives. Elige y ordena las fuentes en **設定 › 歌詞** (Ajustes › Letras).

| Fuente | Qué ofrece | Por defecto |
|---|---|---|
| [LRCLIB](https://lrclib.net) | Letras sincronizadas (biblioteca pública) | Activada |
| [VocaDB](https://vocadb.net) | Vocaloid y música doujin; romaji, traducciones (API pública) | Desactivada |
| [THBWiki](https://thwiki.cc) | Música doujin de Touhou; letras sincronizadas y traducciones al chino (CC BY-NC-SA) | Desactivada |
| NetEase Cloud Music | Letras sincronizadas, traducciones al chino, romaji (no oficial) | Desactivada |
| Bahamut, Kanogoma | Traducciones al chino hechas por fans (no oficial, lee páginas web) | Desactivada |
| J-Lyric, UtaTen | Letras en texto, romaji (no oficial, lee páginas web) | Desactivada |

El romaji se genera en el teléfono con [Kuromoji](https://github.com/atilika/kuromoji) y la traducción automática se hace en el teléfono con [ML Kit](https://developers.google.com/ml-kit/language/translation).

## 🛠️ Compilar desde el código

Necesitas JDK 17 y el Android SDK (platform 35).

```bash
./gradlew :app:assembleDebug        # versión de depuración
./gradlew :app:assembleRelease      # la versión que se publica en Releases
./gradlew :app:testDebugUnitTest    # pruebas unitarias
```

En Windows usa `gradlew.bat`. Los APK quedan en `app/build/outputs/apk/`.

**Google Drive** requiere tu propio proyecto de Google Cloud (API de Drive activada y un cliente OAuth de Android para `io.hkmario.monologue` con el SHA-1 de tu clave de firma). El inicio de sesión con Google de la versión publicada sigue en pruebas, así que solo pueden conectarse las cuentas añadidas como usuarios de prueba. Configuración completa y notas de arquitectura: [docs/DEVELOPMENT.md](../DEVELOPMENT.md) (en chino).

## 🧱 Hecho con

[Kotlin](https://kotlinlang.org) · [Jetpack Compose](https://developer.android.com/compose) (Material 3) · [Media3 / ExoPlayer](https://developer.android.com/media/media3) · [Room](https://developer.android.com/jetpack/androidx/releases/room) · [WorkManager](https://developer.android.com/jetpack/androidx/releases/work) · [OkHttp](https://square.github.io/okhttp/) · [Coil](https://coil-kt.github.io/coil/) · [NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor) · [Kuromoji](https://github.com/atilika/kuromoji) · [ML Kit](https://developers.google.com/ml-kit)

## 🔒 Privacidad

Sin anuncios y sin registro. Las funciones en línea (Drive, letras, ListenBrainz) solo envían datos cuando las activas. Detalles: [docs/PRIVACY.md](../PRIVACY.md) (en chino).

## ⚠️ Aviso

Monologue es un proyecto personal y sin fines comerciales, sin relación con Google, YouTube ni ningún sitio de letras. El audio en línea proviene de YouTube a través de NewPipe Extractor, y las fuentes de letras no oficiales leen páginas web públicas; pueden dejar de funcionar en cualquier momento y es posible que no cumplan los términos de esos servicios. Las letras y traducciones pertenecen a sus autores y traductores.

## 📄 Licencia

[GPL-3.0-or-later](../../LICENSE). Licencias de terceros: [THIRD_PARTY_NOTICES.md](../../THIRD_PARTY_NOTICES.md) y [licenses/](../../licenses/).

Gracias a [LRCLIB](https://lrclib.net), [VocaDB](https://vocadb.net), [THBWiki](https://thwiki.cc), [MusicBrainz](https://musicbrainz.org), [ListenBrainz](https://listenbrainz.org), [NewPipe](https://newpipe.net) y a cada traductor cuyo trabajo aparece en las letras.
