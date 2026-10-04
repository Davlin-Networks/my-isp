# My ISP

An Android app for the customers of internet providers on [Sparo](https://sparo.run). Customers can:

- check their plan, expiry and connection;
- see their data usage;
- renew with M-Pesa;
- see their payments;
- ask their provider for help.

The release APK is about 1.5 MB. It's written in Kotlin with Jetpack Compose and has no web view.

## How it works

The app holds no secret. It knows Sparo's address, and in a branded build the provider's code. Nothing else is built in: no API key, no provider credentials, no M-Pesa keys.

1. **The customer picks their provider.** They scan the provider's QR code with the phone camera (`https://app.sparo.run/isp/{code}`) or type the provider's code.
2. **The app confirms it:** *"Is this your internet provider?"*, with the provider's name and logo. This appears before any password is asked for, so a fake QR sticker can't skip it.
3. **The customer signs in** with the username and portal password they use on the provider's web portal.
4. **Sparo returns a token for that one account.** It can't see any other customer. It's stored encrypted with a key from the phone's Android Keystore.

One provider and one account at a time. Switching provider signs out of the old one, both on the server and in the app, and clears everything the app had stored about it.

The provider must turn the app on first: **Settings → PPPoE Portal → Customer app** in Sparo.

## Build

You need JDK 17+ and the Android SDK (platform 37). Android Studio has both.

```bash
./gradlew assembleDebug        # app/build/outputs/apk/debug/
./gradlew checkApkSize         # release build; fails if it is over 5 MB
```

To run against a local Sparo (Laravel Sail) from a phone on the same network:

```bash
./gradlew assembleDebug -PbaseUrl=http://192.168.1.20
```

Only debug builds allow plain `http://`. Release builds are HTTPS only.

## A branded build for one provider

Pass the provider's code, the app's name, and a suffix for the app ID:

```bash
./gradlew assembleRelease \
  -PtenantSlug=kilimani-fibre \
  -PappName="Kilimani Fibre" \
  -PappIdSuffix=.kilimani
```

A branded build opens straight onto that provider, and has no provider picker or switcher. Its colours and logo come from the provider's Sparo settings, so the provider can change them without a new build. To change the launcher icon, replace `app/src/main/res/mipmap-*`.

**Publishing:** sign the build with your own key, and publish it under the provider's own Play Console account. Many near-identical apps from one developer account run into Play's repetitive-content policy.

## Signing a release

The repository holds no key. The build reads a properties file outside it:

```properties
# ~/.config/sparo/myisp-signing.properties  (or -PsigningProps=/path/to/file)
storeFile=/home/you/keys/myisp-release.jks
keyAlias=myisp
storePassword=…
keyPassword=…
```

With the file present, `./gradlew assembleRelease` gives a signed `app-release.apk`. Without it, the release build is unsigned and everything else still works.

Keep the key and its password safe. An update installs only over an app signed with the same key, so a lost key means customers must uninstall and reinstall.

To create one:

```bash
keytool -genkeypair -keystore myisp-release.jks -alias myisp -keyalg RSA -keysize 4096 -validity 10000
```

If links should open your build directly, its certificate fingerprint must be listed in your Sparo server's `/.well-known/assetlinks.json`. Read the fingerprint with `apksigner verify --print-certs app-release.apk`.

## Settings

| Gradle property | Default | |
|---|---|---|
| `baseUrl` | `https://app.sparo.run` | The Sparo server |
| `linkHost` | `app.sparo.run` | The only host whose links the app accepts |
| `tenantSlug` | *(empty)* | Empty asks the customer which provider; set for a branded build |
| `appName` | `My ISP` | The name under the icon |
| `appIdSuffix` | *(empty)* | Appended to `run.sparo.myisp` |

## Layout

```text
app/src/main/java/run/sparo/myisp/
  MyIspApp.kt        the shared HTTP client, stores and settings (no DI framework)
  MainActivity.kt    which screen, and incoming QR links
  data/              API client and models, encrypted token store, stored snapshot
  ui/                app state (provider, sign-in), theme, formatting
  ui/screens/        sign-in, home, usage, payments, renew, support
  ui/components/     usage ring, daily bars, logo, cards
```

The API is Sparo's subscriber API: `/api/v1/portal/{slug}` and `/api/v1/my/...`.

### Keeping it small

- **Networking:** OkHttp and kotlinx.serialization. No Retrofit or Gson, which would mean reflection for R8 to work around.
- **Icons:** `material-icons-core` only. Never `material-icons-extended`, which is several MB.
- **Charts and images:** drawn on a `Canvas` and decoded with `BitmapFactory`. No chart or image library.
- **Storage:** `SharedPreferences`, with no DataStore. Dates use `java.text`, so no desugaring is needed.

`./gradlew checkApkSize` fails the build if the release APK goes over 5 MB.

## Licence

Apache-2.0.
