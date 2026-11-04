# network-owl

In-app HTTP inspector for OkHttp-based Android apps, themed by the host app.

- `network-owl` records every call through an OkHttp interceptor and shows it in a Compose
  inspector (list, overview, request, response, share as text, copy as cURL).
- `network-owl-noop` has the same public API and does nothing, for builds that must not record.

## Install

```kotlin
// build.gradle.kts of the app
"stagingImplementation"("io.github.ajrnesto.networkowl:network-owl:0.2.0")
"productionImplementation"("io.github.ajrnesto.networkowl:network-owl-noop:0.2.0")
```

Without flavors, split on build type instead:

```kotlin
debugImplementation("io.github.ajrnesto.networkowl:network-owl:0.2.0")
releaseImplementation("io.github.ajrnesto.networkowl:network-owl-noop:0.2.0")
```

GitHub Packages requires a token even for public packages, so the consuming project needs the
repository and a token with `read:packages`:

```kotlin
// settings.gradle.kts
maven {
    url = uri("https://maven.pkg.github.com/ajrnesto/network-owl")
    credentials {
        username = providers.gradleProperty("gpr.user").get()
        password = providers.gradleProperty("gpr.key").get()
    }
}
```

## Use

```kotlin
// Application.onCreate
NetworkOwl.install(
    context = this,
    config = NetworkOwlConfig(
        theme = NetworkOwlTheme(
            colors = NetworkOwlColors(primary = BrandOrange, background = White /* … */),
            fonts = NetworkOwlFonts(text = Inter, code = DmMono),
        ),
        redactedHeaders = setOf("Authorization"),
    ),
)

// OkHttp, or Ktor's OkHttp engine
OkHttp.create { addInterceptor(NetworkOwl.interceptor) }
```

Open the inspector from the notification, the long-press shortcut on the app icon, or
`NetworkOwl.launchIntent(context)` from your own debug entry point. Records live in memory only
and are gone when the process dies.

The notification needs `POST_NOTIFICATIONS` granted on Android 13+; network-owl declares the
permission but never asks for it.

### Config

| Option | Default | Effect |
| --- | --- | --- |
| `theme` | blue on white | Colors and fonts of the inspector, so it matches the host app. |
| `redactedHeaders` | none | Header values shown as redacted in the inspector and exports. Case-insensitive. |
| `redactedStorageKeys` | none | Storage keys whose values are hidden and locked from editing. Case-sensitive. |
| `maxContentLength` | `250_000` | Bytes of each request and response body kept. Longer bodies are cut and marked truncated; the app still gets the full body. |
| `capacity` | `300` | Calls kept in memory. The oldest drop first. |
| `showNotification` | `true` | Post a notification summarizing recent calls. |
| `showShortcut` | `true` | Add the inspector to the app icon's long-press menu. |

### Storage

The Storage tab lists every SharedPreferences file of the app without any setup. Values can be
viewed, edited and removed, and edits reach the app's own listeners.

DataStore files are not discoverable, so register each instance the app uses:

```kotlin
NetworkOwl.register("settings", context.settingsDataStore)
```

`network-owl-noop` keeps `register`, `launchIntent` (returns `null`) and `isEnabled` (`false`), so
calls to them need no build-type checks.

## Develop against a host app

```kotlin
// host settings.gradle.kts
includeBuild("../network-owl")
```

## Publish

```sh
./gradlew publish -Pgpr.user=<github user> -Pgpr.key=<token with write:packages>
```

The public API (`NetworkOwl`, `NetworkOwlConfig` and its theme types) is declared in both modules.
Change both together.

## License

[Apache-2.0](LICENSE)
