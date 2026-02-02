# Changelog

## 2026-02-02

- Build: bump Android Gradle Plugin to 9.0.0 and Gradle wrapper to 9.1.0.
- Dependencies: bump Kotlin to 2.3.0 and `androidx.webkit:webkit` to 1.15.0.
- Push: integrate OneSignal V5 as a runtime dependency (BuildConfig field in `app/build.gradle` and initialization in `GoNativeApplication`).
- Plugins: refine local plugin discovery to scan `plugins/*/src/main/resources/META-INF/plugin-metadata.json` and merge local and compiled plugins with local precedence.
- CI: update the Android CI workflow to trigger on `main`, `dependabot/**`, and `cosine/**` branches and publish `app/build/outputs/apk/normal/debug/app-normal-debug.apk` as the build artifact.

## 2014-01-04

- Fix a crash on reload with no page loaded.

## 2015-01-02

- Update to latest gradle and build tools versions, making the project compatible with Android Studio 1.0.
- Fix bugs related to syncing of tabs with sidebar menu.

## 2014-12-23

- Allow setting of viewport while preserving ability to zoom.
- Allow dynamic config of navigation title image URLs.
- Various bug fixes involving javascript after page load, and tab coloring, tab animations, and a crash on application resume.

## 2014-12-22

- Fix various threading bugs where UI methods were called from non-UI threads.

## 2014-12-05

- Support showing the navigation title image on specific URLs.

## 2014-12-03

- Support customizing user agent per URL.
- Add color styling options for tabs.

## 2014-11-30

- New tabs with better material design and animations.
- Fix some automatic icon generation scripts.

## 2014-11-26

- Fix a crash involving webview pools.

## 2014-11-25

- Add support for custom actions in action bar.
