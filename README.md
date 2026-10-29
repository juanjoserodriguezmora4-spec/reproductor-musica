# BismoPower

Reproductor de música local para Android con el diseño de vidrio esmerilado de
[Bruma](https://github.com/juanjoserodriguezmora4-spec/Bruma).

- Biblioteca: canciones, álbumes, artistas y carpetas
- Reproductor con carátula grande y colores que se adaptan a cada álbum
- Música en segundo plano con controles en la notificación y la pantalla de bloqueo
- Tema claro y oscuro

Próximamente: listas de reproducción y ecualizador paramétrico.

## Descargar

Desde el celular, abre la última versión en
[Releases](https://github.com/juanjoserodriguezmora4-spec/reproductor-musica/releases/latest)
y toca el archivo `.apk`. Cada push a `main` publica una nueva.

Los pushes a otras ramas solo dejan el APK como artefacto en **Actions**.

## Compilar

```bash
./gradlew assembleDebug
```

Requiere Android 8.0 o superior.
