# GoNative Android Template – Architectural Overview

This repository is a GoNative/Median Android shell app that wraps a web application (here, `https://app.sesame.com`) and provides a configurable native container around it. Most behavior is driven by JSON configuration (`appConfig.json`) and the `median_core` libraries, with this repo supplying the Android entry points, wiring, and extension points.

---

## 1. Build & Module Structure

### 1.1 Gradle layout

**Root project (`GoNative`)**

- **`settings.gradle`**
  - Sets `rootProject.name = 'GoNative'`.
  - Forces `user.dir` to the project root (important for plugin discovery scripts that use `System.getProperty("user.dir")`).
  - Applies `plugins.gradle` and calls `applyModulesSettingsGradle(settings)` to dynamically include local plugin modules under `./plugins/*`.
  - Includes a single app module: `:app`.

- **Root `build.gradle`**
  - Declares shared versions:
    - `kotlin_version = '2.3.0'`
    - `coreVersion = '2.7.27'`
    - `iconsVersion = '1.3.1'`
  - Uses repositories: `google()`, `mavenCentral()`, `gradlePluginPortal()`, and a Median private Maven repo (`https://maven.median.co`) plus a local `maven` dir.
  - Configures Gradle plugins:
    - `com.android.tools.build:gradle:9.0.0`
    - Kotlin Gradle plugin
    - Commented-out Google Services and Firebase Crashlytics classpaths that are toggled by the GoNative builder.
  - Global resolution strategy:
    - **Dependency substitution**: any dependency on `com.github.gonativeio:gonative-android-core` is replaced with `co.median.android:core:$coreVersion`.
    - Forces `co.median.android:core:$coreVersion` in all configurations.

- **`gradle.properties`**
  - JVM & Android options:
    - `org.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=512m`
    - `android.enableJetifier=true`
    - `android.useAndroidX=true`
    - `enableLogsInRelease=false` (used from `app/build.gradle` to control `debuggable` in release variants).
    - `android.nonTransitiveRClass=false`, `android.nonFinalResIds=false` (traditional R-class behavior).

- **`dependencies.json`** (GoNative-specific)
  - Example:
    ```json
    {
      "core": "2.7.28",
      "plugins": {},
      "engineVersion": "1.0.99"
    }
    ```
  - `core` pins the Median core library version.
  - `plugins` is a map of plugin name → version for compiled plugin AARs.
  - Parsed in `plugins.gradle` to add plugin dependencies dynamically.

- **Wrapper & tooling**
  - Standard Gradle wrapper (`gradlew`, `gradlew.bat`, `gradle/wrapper/`), currently configured for **Gradle 9.1.0** (see `gradle/wrapper/gradle-wrapper.properties`).
  - Utility scripts:
    - `generate-theme.js`, `generate-app-icons.sh`, `generate-header-images.sh`, `generate-tinted-icons.sh` – used by the GoNative build pipeline to pre-generate images and themes from configuration.

### 1.3 CI workflow

- **Workflow file**: `.github/workflows/android-ci-build.yml`.
- **Triggers**:
  - `push` on `master`, `main`, `dependabot/**`, and `cosine/**` branches.
  - `pull_request` targeting `master` or `main`.
- **Steps (high level)**:
  - Check out the repository.
  - Set up JDK 17 via `actions/setup-java`.
  - Cache Gradle wrapper and dependency caches.
  - Run `./gradlew assembleDebug`.
  - Upload the normal-flavor debug APK from `app/build/outputs/apk/normal/debug/app-normal-debug.apk` as the CI artifact.

### 1.2 App module (`app/`)

- Applies `com.android.application` and `kotlin-android`.
- Reads configuration from `app/src/main/assets/appConfig.json` at build time (`parseAppConfig` task) to populate Gradle `ext` properties used for manifest placeholders (Facebook, OneSignal, AdMob, Auth0, Branch, Snapchat, etc.).
- Integrates with the plugins system by applying `../plugins.gradle` and invoking `applyNativeModulesAppBuildGradle(project)`.

Key characteristics in `app/build.gradle`:

- **Android configuration**
  - `compileSdk 36`, `targetSdkVersion 36`, `minSdkVersion 23`.
  - `applicationId` is fixed to `co.median.android.xlrdknk` (also present in `appConfig.json`).
  - `multiDexEnabled true` and `vectorDrawables.useSupportLibrary = true`.
  - `namespace 'co.median.android'`, `testNamespace '${applicationId}.test'`.
  - `viewBinding { enabled = true }`.

- **Signing configs**
  - `release` and `upload` signing configs point to keystores outside the repo (`../../release.keystore`, `../../upload.keystore`), with placeholder passwords (`"password"`). These are expected to be replaced by CI or environment-specific keystores.

- **Build types**
  - `debug`, `release`, and `upload`.
  - `release`/`upload` use ProGuard (`proguard-android.txt` + `proguard-project.txt`).
  - `debuggable` for `release`/`upload` is controlled by `enableLogsInRelease` from `gradle.properties`.
  - All build types add a `BuildConfig` field:
    ```groovy
    buildTypes.each {
        it.buildConfigField 'boolean', 'GOOGLE_SERVICE_INVALID', googleServiceInvalid
    }
    ```
    where `googleServiceInvalid` is computed by a custom Gradle task (`checkGoogleService`).

- **Flavors**
  - Single flavor dimension `"webview"` with one flavor: `normal`.

- **Dependencies**
  - AndroidX & Google Play Services (`core-ktx`, `appcompat`, `material`, `browser`, `webkit`, `splashscreen`, etc.).
  - GoNative/Median libraries:
    - `implementation "co.median.android:icons:$iconsVersion"`
    - `implementation "co.median.android:core:$coreVersion"`
  - Local `.jar` and `.aar` via `fileTree` in `app/libs`.
  - A custom configuration:
    ```groovy
    configurations {
        medianPlugin.extendsFrom(implementation)
    }
    ```
    Used by the plugins system to resolve plugin AARs.

- **Custom Gradle tasks**
  - `parseAppConfig` – parses `appConfig.json` and assigns `ext` values that feed into manifest placeholders.
  - `checkGoogleService` – validates `google-services.json` against the currently selected variant's `applicationId` and sets `googleServiceInvalid` appropriately (or throws a descriptive `GradleException` on mismatch).
  - `tasks.matching { it.name.endsWith("GoogleServices") }.configureEach { dependsOn(checkGoogleService) }` ensures any Google Services task runs validation first.

**Quirk**: `parseAppConfig` runs during configuration/build, and assumes `appConfig.json` exists and is syntactically valid. If you alter the config structure, you may need to adjust this task.

---

## 2. Plugins & Dynamic Modules (`plugins.gradle`)

The `plugins.gradle` script implements a flexible plugin system that supports both:

- **Local plugins** in `./plugins/<pluginName>`.
- **Compiled plugin AARs** published to the `co.median.android.plugins` group and referenced via `dependencies.json`.

### 2.1 Generated `PackageList`

`plugins.gradle` defines templating for a generated Java file:

- `generatedFileName = "PackageList.java"`
- `generatedFilePackage = "co.median.android"`

This class:

- Resides in `build/generated/gncli/src/main/java/co/median/android/PackageList.java`.
- Exposes a `getPackages(): ArrayList<BridgeModule>` method that returns the list of plugin `BridgeModule` instances.
- Imports `BuildConfig` and `R` as well as each plugin's class via metadata.

`GoNativeApplication` uses this class:

```java
public final Bridge mBridge = new Bridge(this) {
    @Override
    protected List<BridgeModule> getPlugins() {
        if (GoNativeApplication.this.plugins == null) {
            GoNativeApplication.this.plugins = new PackageList(GoNativeApplication.this).getPackages();
        }
        return  GoNativeApplication.this.plugins;
    }
};
```

### 2.2 `GoNativeModules` class (in `plugins.gradle`)

Defined in Groovy within the Gradle script; responsible for:

1. **Scanning local plugins**
   - Uses `FileNameFinder` to locate `plugins/*/src/main/resources/META-INF/plugin-metadata.json`.
   - Parses each JSON and augments it with:
     - `sourceDir` – `plugins/<pluginName>`.
     - `isLocal = true`.
   - Populates `localPluginsMetadata` and logs them.

2. **Extracting compiled plugins**
   - Looks in the resolved `medianPlugin` configuration for artifacts with group `co.median.android.plugins` of type `aar`.
   - Treats each AAR as a ZIP, opens inner `classes.jar`, then reads `META-INF/plugin-metadata.json`.
   - Populates `compiledPluginsMetadata` with `isLocal = false`.
   - Gracefully logs and continues on errors (no hard failure if no compiled plugins are present).

3. **Merging plugin sources**
   - `mergePluginSources()` merges `localPluginsMetadata` and `compiledPluginsMetadata`:
     - Local plugins **take precedence** by plugin name.
     - Compiled plugins that collide with a local same-name plugin are skipped with a log message.

4. **Wiring into Gradle lifecycle**

   - **Settings-level inclusion**
     - `applyModulesSettingsGradle(DefaultSettings defaultSettings)`:
       - Calls `scanLocalPlugins()`.
       - Includes each plugin as a subproject `:<pluginName>` and sets `projectDir` to `./plugins/<pluginName>`.
       - Optionally includes `:<localLibrary>` modules nested under the plugin directory.

   - **Dependency wiring in `app/build.gradle`**
     - `applyNativeModulesAppBuildGradle(Project project)` is invoked at the bottom of `app/build.gradle`:
       - Computes `generatedSrcDir` and `generatedCodeDir`.
       - Calls `addPluginDependenciesFromJson(project)` to add compiled plugin dependencies from `dependencies.json` (e.g., `implementation "co.median.android.plugins:<name>:<version>"`).
       - Calls `scanLocalPlugins()` and `addLocalModuleDependencies(project)` to add `implementation project(":<pluginName>")` for each local plugin.
       - Registers a `generatePackageList` task that:
         - Re-scans local plugins.
         - Calls `extractCompiledPlugins` and `mergePluginSources`.
         - Writes `PackageList.java` to `generatedCodeDir` using `generatePackagesFile`.
       - Wires `preBuild.dependsOn generatePackageList`.
       - Adds `generatedSrcDir` as a `sourceSet` for `main.java`.
       - Excludes `META-INF/plugin-metadata.json` from APK packaging to avoid conflicts.

**Key quirk**: All plugin metadata is driven by `plugin-metadata.json`. A missing or malformed file will cause the plugin to be invisible to the app, but the build may still succeed. Debugging plugin load order and precedence often involves inspecting logs produced by `GoNativeModules`.

---

## 3. Configuration-Driven Behavior (`AppConfig` & `appConfig.json`)

The runtime behavior is largely governed by `app/src/main/assets/appConfig.json`, which the `median_core.AppConfig` class parses. This file is both:

- **Used at build time** to set Gradle `ext` values (for manifest placeholders, flavoring, etc.).
- **Used at runtime** by various classes (`MainActivity`, `UrlNavigation`, `WebViewPool`, `GoNativeApplication`, etc.).

Key sections in the provided `appConfig.json`:

- **`general`**
  - `initialUrl`: `https://app.sesame.com` – the root page loaded for the app.
  - `androidPackageName`: `co.median.android.xlrdknk` – must match `applicationId` in `app/build.gradle`.
  - `injectMedianJS`: `false` – disables automatic injection of the GoNative JS library.
  - `nativeBridgeUrls`, `forceUserAgent` fields are unused/empty here but supported by the code.

- **`navigation`**
  - `regexInternalExternal` describes routing mode per regex. For example:
    - `.*sesame.com.*` → `internal`.
    - Social networks and most other URLs → `appbrowser` (custom tabs).
    - Non-HTTP(s) links → `external`.
  - `androidConnectionOfflineTime`: `10` seconds – used by `UrlNavigation` to decide when to display the offline page.
  - Deep link domains, sidebar menus, tab navigation, max windows, toolbar navigation, etc.

- **`styling`**
  - Theme colors and icons for light and dark modes.
  - Status bar, navigation bar, tab bar colors.
  - Splash screen images per density and theme.
  - Several flags like `androidHideTitleInActionBar`, `showNavigationBar`, `showActionBar` (here both false → fully chromeless presentation).

- **`contextMenu`**
  - `enabled`: `false` – context menu on links is disabled by default for this app.

- **`permissions`**
  - WebRTC camera/audio toggles, background audio flags, etc.

Because `AppConfig` is central, many classes register listeners (`ConfigListenerManager.AppConfigListener`) to react to dynamic config changes at runtime (e.g., when the config is updated from the network via `ConfigUpdater`).

**Tricky point**: `AppConfig` fields like `initialHost`, `regexRulesManager`, `webviewPools`, `loginDetectionUrl`, etc. are used widely. When you extend or change `appConfig.json`, you must understand how those fields are consumed (often via `AppConfig` getters) before modifying them.

---

## 4. Application Lifecycle & Global State

### 4.1 `GoNativeApplication`

`GoNativeApplication` extends `MultiDexApplication` and serves as the global container for

- Login and registration managers
- The WebView pool
- Application-level JS/CSS customization
- The `Bridge` instance (core plugin bridge)
- A window manager for coordinating multiple `MainActivity` instances
- Push notifications (via OneSignal)

Key responsibilities:

- **Theme setup for Android 12+**
  - If `Build.VERSION.SDK_INT >= S` (API 31), calls `setupAppTheme()`:
    - Reads `configAppTheme` from `ThemeUtils.getConfigAppTheme(this)` (driven by `AppConfig`/styling).
    - Uses `ThemeUtils.setAppThemeApi31AndAbove()` to set a persistent night mode via `UiModeManager`.
    - Ensures this is done only once per app install via `ThemeUtils.isInitialAppThemeSet()`.

- **Bridge initialization**
  - `mBridge.onApplicationCreate(this)` is called from `onCreate` to allow plugins and core to initialize.

- **Configuration validation**
  - Instantiates `AppConfig`. If `configError` is non-null, it shows a Toast and logs via `GNLog`.

- **Managers**
  - `LoginManager` and `RegistrationManager` are created and configured.
  - `WebViewSetup.setupWebviewGlobals(this)` applies global WebView/static settings.
  - `WebViewPool` is instantiated and later initialized in `MainActivity`.
  - `GoNativeWindowManager` is created to track open windows and `urlLevel` information.

- **Push notifications (OneSignal)**
  - `onCreate()` calls `initOneSignal()`.
  - `initOneSignal()` reads the App ID from `BuildConfig.ONESIGNAL_APP_ID` (set in `app/build.gradle` via `buildConfigField`).
  - If the App ID is non-empty, it initializes OneSignal with `OneSignal.initWithContext(this, oneSignalAppId)`.

- **Custom CSS & JS injection**
  - Reads custom files from assets when `AppConfig.hasCustomCSS/hasAndroidCustomCSS/hasCustomJS/hasAndroidCustomJS` are true.
  - Concatenates file contents and Base64-encodes them into `customCss` and `customJs`. These are later injected using JS in `UrlNavigation`.
  - For this template, `customJS.js` also contains a small WebRTC instrumentation wrapper around `navigator.mediaDevices.getUserMedia` that listens for audio track `ended` events and sends JSBridge messages consumed by `MainActivity`.

- **First-launch detection**
  - Uses `SharedPreferences` key `hasLaunched` to set `isFirstLaunch`.

**Tricky behavior**: Custom CSS/JS are not injected directly from within `GoNativeApplication`; instead, they are stored as Base64 strings accessed by `UrlNavigation` which decides when (and on which pages) to inject them.

### 4.2 `GoNativeWindowManager`

This is a lightweight window registry used by all `MainActivity` instances to coordinate multi-window behavior.

- Tracks windows by an auto-generated `activityId` string.
- For each `ActivityWindow` stores:
  - `id`
  - `isRoot` – whether this is the logical root window.
  - `urlLevel` and `parentUrlLevel` – used to implement structured navigation (e.g., hierarchical navigation levels based on URL patterns).
  - `ignoreInterceptMaxWindows` – flag consumed by `UrlNavigation` to avoid triggering max-window logic on initial loads.

Notable operations:

- `addNewWindow`, `removeWindow` – manages the pool. `removeWindow` notifies `ExcessWindowsClosedListener` when only one window is left.
- `setAsNewRoot(activityId)` – remaps which window is considered the "root" (used when `maxWindows.autoClose` is enabled and a new root is chosen).
- `getExcessWindow()` – returns the first non-root window, used to decide which window to close when `maxWindows` is exceeded.

`MainActivity` uses this manager heavily to implement the **max windows** feature and hierarchical navigation.

---

## 5. MainActivity, WebView & Navigation Stack

### 5.1 `MainActivity`

`MainActivity` is the core UI and lifecycle host. It:

- Implements `GoNativeActivity` (from `median_core`) and `Observer`.
- Manages:
  - The primary WebView (`GoNativeWebviewInterface` in a `WebViewContainerView`).
  - Pull-to-refresh (`GoNativeSwipeRefreshLayout`).
  - Swipe history navigation (`SwipeHistoryNavigationLayout`).
  - Bottom tab bar (`TabManager`).
  - Action bar items (`ActionManager`).
  - Side navigation drawer (`SideNavManager`).
  - Downloading, file sharing, location services, keyboard interactions, etc.
  - A small `WebRtcMicManager` helper that coordinates an Android foreground service of type `microphone` used to keep WebRTC audio capture active while there are live audio tracks in the WebView.

#### 5.1.1 Lifecycle setup

- **Splash screen**
  - Uses `SplashScreen.installSplashScreen` for Android 12+.
  - Integrates with `mBridge.animatedSplashScreen()` to let plugins perform custom animations.
  - Uses a `SplashScreenViewProvider` and a `shouldRemoveSplash` flag to coordinate removal once content is ready (or after a 7s fallback timeout).

- **Theme configuration**
  - For API ≤ 30, `ThemeUtils.setAppThemeApi30AndBelow(appTheme)` is invoked (unless this resume is caused by theme setup itself, in which case a guard flag prevents recursion).
  - Relies on `AppConfig` / `ThemeUtils.getConfigAppTheme()` to decide theme.

- **WebView & view hierarchy**
  - `setContentView(R.layout.activity_median)`.
  - Initializes `swipeRefreshLayout`, `swipeNavLayout`, and their color schemes from styling.
  - Sets up `MedianProgressView` progress indicator, either using a plugin-provided custom view or a default one.
  - Delegates global WebView configuration to `WebViewSetup` and the container's `setupWebview()`.
  - Integrates `SystemBarManager` to apply edge-to-edge layout and manage window insets.

- **URL loading**
  - Creates a `UrlLoader` instance with `usingNpmPackage = !appConfig.injectMedianJS`.
  - Determines the initial URL from:
    1. `getUrlFromIntent()` – for push notifications or deep links.
    2. `appConfig.getInitialUrl()` – if `isRoot` and no explicit URL.
    3. `intent.getStringExtra("url")` for navigations launched from other activities.
  - Augments URL with initial query parameters from `mBridge.getInitialUrlQueryItems()`.

- **Window / navigation integration**
  - Calls `windowManager.addNewWindow(activityId, isRoot)` and then `windowManager.setUrlLevels(activityId, urlLevel, parentUrlLevel)`.
  - Registers a `MaxWindowsListener` to auto-close windows when `maxWindows` constraints are hit.

- **Context menu**
  - If enabled via `AppConfig.contextMenuConfig`, registers the WebView container for context menus, and implements actions like "Copy link" and "Open in browser".

- **Back handling**
  - Uses `OnBackPressedDispatcher` with a callback that first tries `onConsumeBackPress()` (custom logic) before finishing the activity.

#### 5.1.2 Interaction with `UrlLoader` and `UrlNavigation`

`MainActivity` owns both:

- `UrlLoader` – a high-level loader that:
  - Normalizes URL loads (including `median_logout`/`gonative_logout` pseudo-URLs).
  - Coordinates with a JS callback-based navigation mode when using the NPM package.
  - For SPA-like apps, provides a way to inject "post-load javascript" and track history.

- `UrlNavigation` – the WebViewClient-like component responsible for:
  - Deciding whether to handle a URL **internally**, via the browser, via app browser (Chrome Custom Tabs), or via another `MainActivity` instance.
  - Enforcing `maxWindows` limits.
  - Implementing window pools (WebView pooling).
  - Injecting custom CSS/JS and the GoNative JS bridge.
  - Managing offline behavior and HTML interception.

They are connected as follows:

- `UrlNavigation` constructor sets itself on `mainActivity.getUrlLoader()`.
- `UrlLoader.load()` delegates to `mWebView.loadUrl()` or, when in NPM mode, uses `urlNavigation.shouldOverrideUrlLoadingNoIntercept()` to trigger override logic without duplicating WebViewClient behavior.
- `UrlLoader.onHistoryUpdated()` ensures SPA navigations still trigger `UrlNavigation.onPageStarted()` when `onPageStarted` is not called normally by WebView.

---

## 6. URL Routing, Offline Handling & WebView Pooling

### 6.1 `UrlNavigation`

`UrlNavigation` is a complex component that effectively encapsulates the logic traditionally belonging to a custom `WebViewClient` and a `WebChromeClient`.

Key concerns:

1. **Internal vs external routing**
   - `isInternalUri(Uri)` uses `AppConfig.regexRulesManager` if available; otherwise, falls back to comparing hostnames with `AppConfig.initialHost`.
   - `regexRulesManager` is configured by the `regexInternalExternal` section of `appConfig.json` (e.g., internal vs `appbrowser` vs external modes).

2. **Native bridge URLs**
   - Special schemes:
     - `median://` and `gonative://` – JSON-based commands consumed by `mBridge.handleJSBridgeFunctions()`.
     - `gonative-bridge://` – batched bridge commands (parsed as a JSON array); supports commands such as `pop` (close non-root window) and `clearPools` (clear WebView pools).
   - Before executing a bridge command, checks `LeanUtils.checkNativeBridgeUrls(currentWebviewUrl, mainActivity)` for security.

3. **Redirects**
   - Uses `AppConfig.getRedirects()` (built from the `navigation.redirects` list in config) to redirect URLs (including wildcard `*`).

4. **Max windows and structured navigation**
   - Interacts with `GoNativeWindowManager` and `AppConfig.maxWindowsEnabled/numWindows/autoClose`:
     - If new windows would exceed the limit, calls `MainActivity.onMaxWindowsReached(url)`.
     - `onMaxWindowsReached` may:
       - Re-parent the calling activity as the root.
       - Signal other windows to close.
       - Reload the current activity with the new URL once excess windows are gone.
   - Structured navigation based on `urlLevel` and `parentUrlLevel` ensures "pop" behavior across activities.

5. **Offline handling**
   - `connectionOfflineTime` (from `androidConnectionOfflineTime`) determines how long to wait after `shouldOverrideUrlLoading()` to show the offline page.
   - Uses a handler (`startLoadTimeout`) to schedule a fallback to `OFFLINE_PAGE_URL`.
   - On `onPageStarted` / `onPageFinished`, cancels or updates this timeout.

6. **Custom CSS/JS and bridge injection**
   - On `onPageFinished`, it:
     - Base64 injects `customCss` and `customJs` (if set by `GoNativeApplication`).
     - Updates the CSS theme attributes (via `MainActivity.setupCssTheme()`).
     - Calls `injectJSBridgeLibrary()` to load `GoNativeJSBridgeLibrary.js` from assets and run plugin-provided JS libraries.
     - Executes post-load JavaScript from `AppConfig` and `MainActivity.postLoadJavascript`.

7. **Device info & events**
   - After page load, if native bridge is allowed for the current URL, sends device info callbacks `median_device_info` and `gonative_device_info`.
   - Calls `mBridge.onPageFinish(mainActivity, doNativeBridge)` to notify plugins.

8. **File upload**
   - Handles file chooser flows using `FileUploadContract` and an `ActivityResultLauncher` from `MainActivity`.
   - Deals with camera/gallery permissions and optional resizing of captured images via `MediaFileHelper.resizeJpgUriTo480p`.

9. **Client certificates**
   - Implements `onReceivedClientCertRequest()` to forward client certificate selection to Android's `KeyChain` APIs.

10. **Tracking pixel detection**
    - Implements `isTrackingPixelData(dataUri)` to detect 1×1 pixel data URIs for GIF/PNG/JPEG, allowing them to load while handling them differently if needed.

**Subtlety**: `UrlNavigation` carefully coordinates with `WebViewPool` and `GoNativeWindowManager` to avoid double-loading pages, to reuse pooled WebViews when possible, and to prevent unnecessary offline page triggers during redirects.

### 6.2 `WebViewPool`

`WebViewPool` provides background preloading and pooling of WebViews for specified sets of URLs.

- Reads `AppConfig.webviewPools` JSON to configure pools:
  - Each entry contains a list of `urls`; each URL can be a string or an object with `url` and `disown` policy (`"reload"`, `"never"`, `"always"`).
  - All URLs within a single pool set are considered related; loading one can warm up the others.

- Internal state:
  - `urlToWebview`: map URL → pooled WebView.
  - `urlToDisownPolicy`: map URL → `WebViewPoolDisownPolicy`.
  - `urlSets`: sets of related URLs.
  - `urlsToLoad`: pending URLs requiring a WebView.
  - `currentLoadingWebview`/`currentLoadingUrl` and `isLoading` to manage sequential background loads.
  - `isMainActivityLoading` to pause/resume background loading during main WebView loads.

- Key operations:
  - `init(Activity activity)` – attaches a `ConfigListener` so changes to `AppConfig.webviewPools` reprocess the pools.
  - `onStartedLoading()` / `onFinishedLoading()` – called by `MainActivity`/`UrlNavigation` to pause/resume background loads.
  - `webviewForUrl(String url)` –
    - Updates `urlsToLoad` with new URLs from the relevant set.
    - Returns a `(webview, policy)` pair if a preloaded webview exists.
  - `disownWebview(GoNativeWebviewInterface webview)` – unbinds a pooled webview from the pool and schedules its URLs for background reloading.
  - `flushAll()` – clears state and stops any in-progress loads.

Integration with `UrlNavigation`:

- Before loading a URL, `UrlNavigation` asks `webViewPool.webviewForUrl(url)`.
- Based on the returned `WebViewPoolDisownPolicy`, it may:
  - Switch to the pooled WebView and `disown` it (for `Always`).
  - Use it for repeated visits without disowning (for `Never`).
  - Reuse it only when path matches (`Reload`).

This provides snappy navigation for known heavy pages at the cost of greater memory usage. Care must be taken when tuning pools in `appConfig.json` to avoid over-allocating WebViews.

---

## 7. Assets, Offline & Customization

### 7.1 Assets

`app/src/main/assets` contains:

- `appConfig.json` – main configuration (see above).
- `GoNativeJSBridgeLibrary.js` – base JS bridge library injected into pages when `injectMedianJS` is true.
- `BlobDownloader.js` – helper script for blob downloads.
- `customCSS.css` / `androidCustomCSS.css` – general and Android-specific CSS overrides.
- `customJS.js` / `androidCustomJS.js` – general and Android-specific JavaScript overrides.
- `custom-icons.json` – mapping for custom icon sets.
- `offline.html` – offline fallback page shown when connectivity is lost or timeouts occur.
- `fonts/` – any bundled fonts referenced via CSS.

`GoNativeApplication` base64-encodes custom CSS/JS files; `UrlNavigation` injects them into pages.

### 7.2 Offline mode

- Triggered when a page load takes longer than `androidConnectionOfflineTime` seconds.
- Implemented in `UrlNavigation` and `WebViewPool` through timeout handlers and `HtmlIntercept`.
- Offline page is `file:///android_asset/offline.html`.

**Important**: Offline behavior is tightly coupled to the timing of `onPageStarted`/`onPageFinished` and to `LeanWebView.shouldReloadPage()` logic, making this a more complex area to modify.

---

## 8. Android Manifest & Permissions

`AndroidManifest.xml` is intentionally permissive for a template and annotated with comments for toggling features:

- Core permissions:
  - `INTERNET`, `ACCESS_NETWORK_STATE`, `READ_PHONE_STATE`, `VIBRATE`.
  - Camera (`CAMERA`), microphone (`RECORD_AUDIO`, `MODIFY_AUDIO_SETTINGS`).
  - GCM / FCM push messaging (`com.google.android.c2dm.permission.RECEIVE` and app-specific C2D message permission).
- Many permissions are commented out (e.g., location, Bluetooth, storage, call blocking) and can be enabled selectively.

Application setup:

- `android:name=".GoNativeApplication"` – binds the custom `Application` subclass.
- `android:theme="@style/Theme.Median"`, `android:enableOnBackInvokedCallback="true"` (required for target SDK 36+).
- `android:networkSecurityConfig="@xml/network_security_config"` – allows configuration of certificate pinning/cleartext rules.

Activities:

- `LaunchActivity` – launcher/splash entry point.
- `MainActivity` – main content host.
- `AppLinksActivity` – for handling Android App Links / deep links.

Other components:

- `FileProvider` configured with `android:authorities="${applicationId}.fileprovider"` to allow secure file sharing.
- `DownloadService` for background downloads.
- `AppUpgradeReceiver` (`MY_PACKAGE_REPLACED`) to handle app upgrades.

Meta-data keys for Facebook SDK (`com.facebook.sdk.ApplicationId`, `ClientToken`) are filled via manifest placeholders from `parseAppConfig`.

**Tricky aspect**: Changing `applicationId` or `androidPackageName` requires coordination across:

- `app/build.gradle` (`applicationId` and `testNamespace`)
- `appConfig.json` (`androidPackageName`)
- Firebase `google-services.json` (validated by `checkGoogleService`)
- Any push-related configuration (OneSignal, etc.)

---

## 9. Notable Quirks & Gotchas

This section collects behaviors that are subtle or easy to misconfigure.

1. **`injectMedianJS` and NPM mode**
   - When `injectMedianJS` is `false` (as in this template), `UrlLoader` runs in a mode that prefers communicating URL changes back to JS callbacks (NPM package integration) instead of immediately navigating in the WebView.
   - If you toggle `injectMedianJS` to `true`, you must ensure the JS-side integration (median library) is present and compatible.

2. **Google Services validation**
   - The `checkGoogleService` Gradle task will:
     - Fail the build with a human-friendly error if `applicationId` is missing or not present among the package names in `google-services.json`.
     - Set a build-time constant `BuildConfig.GOOGLE_SERVICE_INVALID` that can be used in runtime flows.
   - If `google-services.json` is missing or empty, it simply sets `googleServiceInvalid = "true"`.

3. **Max windows & auto-close logic**
   - Controlled by `navigation.maxWindows` in `appConfig.json`.
   - If enabled and `autoClose` is true, hitting the max window count can cause:
     - Re-rooting of the current activity and reloading initial URLs.
     - Silent closing of other windows.
   - Debugging multi-window behavior usually requires inspecting `GoNativeWindowManager` state and `UrlNavigation` logs.

4. **Offline timeout + SPA behavior**
   - For SPAs where `onPageStarted` might not fire on navigation, `UrlLoader.onHistoryUpdated()` explicitly calls `UrlNavigation.onPageStarted()` to keep offline/loading logic in sync.
   - Any custom WebViewClient behavior must preserve these semantics or offline handling will break.

5. **Custom CSS/JS injection**
   - Uses Base64-encoded assets and JS injection on `onPageFinished` / `onPageCommitVisible`.
   - If page CSP or JS execution is severely restricted, injection may fail silently.
   - `isCustomCSSInjected` avoids repeatedly injecting CSS on the same page; ensure your overrides are idempotent.

6. **Tracking pixel detection**
   - `UrlNavigation.isTrackingPixelData()` inspects data URIs byte-by-byte for GIF/PNG/JPEG 1×1 images.
   - Any changes to its logic should be done carefully to avoid mis-detecting legitimate content or introducing performance issues.

7. **Config coupling**
   - Many fields in `appConfig.json` are referenced indirectly via `AppConfig`. For example:
     - `loginDetectionUrl`, `loginUrl`, `signupUrl` – used to update menus when login state changes.
     - `deepLinkDomains` – interplay with `MainActivity.getLaunchSource()` and `isAppLink(Uri)`.
     - `webviewPools` – consumed solely by `WebViewPool`.
   - It is easy to create a configuration that parses but has no effect if you misspell keys or misalign them with `AppConfig` expectations.

8. **App theming across API levels**
   - There is a clear split between theme handling for API ≤ 30 and ≥ 31.
   - `ThemeUtils` and `AppConfig` must be updated in tandem; otherwise you may see theme flicker or inconsistent dark mode behavior.

9. **First launch behavior**
   - `GoNativeApplication.isFirstLaunch()` is passed into `UrlNavigation` and can be used to implement special-case behavior on first run (e.g., intros, onboarding). This template sets the flag but does not show custom behavior out of the box.

---

## 10. How to Extend Safely

When making changes or adding new functionality, keep these design constraints in mind:

1. **Prefer configuration over code changes**
   - Many behaviors (routing, navigation levels, theming, max windows, offline behavior) are designed to be changed in `appConfig.json` rather than by editing Java/Kotlin.

2. **Follow the plugin architecture**
   - If you need native capabilities beyond what this repo exposes, consider writing a plugin:
     - Create `plugins/<pluginName>/` with its own `build.gradle` and `plugin-metadata.json`.
     - Use the `co.median.median_core.BridgeModule` pattern as in other plugins.
     - Allow `plugins.gradle` to detect and include the plugin.

3. **Respect WebView lifecycle hooks**
   - If you customize `UrlNavigation`, `LeanWebView`, or related classes, maintain the contract around:
     - `onPageStarted` / `onPageFinished` → offline and ready-status checks.
     - `shouldOverrideUrlLoading` → must call `UrlNavigation.shouldOverrideUrlLoading*` or replicate its logic.

4. **Test changes in combination**
   - Because many behaviors are interdependent (e.g., `maxWindows` + `autoClose` + window pools), any change should be tested with:
     - Multiple windows.
     - Navigation between internal and external URLs.
     - Offline scenarios.
     - SPAs with client-side routing.

5. **Use logs from `GNLog` and Gradle**
   - Logging is already integrated in critical areas (plugin discovery, URL routing, client cert retrieval, external intent handling). Use/extend these logs rather than introducing ad-hoc logging frameworks.

---

This overview covers the main architectural elements and their interactions. For deeper changes, the primary classes to study are:

- `GoNativeApplication`
- `MainActivity`
- `UrlNavigation`
- `UrlLoader`
- `WebViewPool`
- `GoNativeWindowManager`
- `plugins.gradle` and any plugin modules under `./plugins`

Together, these form the core of the GoNative Android shell that wraps the Sesame web application.
