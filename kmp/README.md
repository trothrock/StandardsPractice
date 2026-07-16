Kotlin Multiplatform port of the Jazz Standards Practice Tracker (see `../web/`),
targeting Android, iOS, Web (JS + Wasm), and Desktop (JVM).

* [/composeApp](./composeApp/src) — Compose Multiplatform UI: one screen composable per tab
  (Today, Library, Repertoire, Voicings, Review Queue, Progress, Activity, plus song detail
  and the onboarding ranker), each paired with a ViewModel. Platform entry points live in the
  per-target source sets (`androidMain`, `iosMain`, `jvmMain`, `webMain`/`jsMain`/`wasmJsMain`).

* [/data](./data/src) — the data layer, behind the `JazzRepository` interface. Holds the
  serializable models, seed data (generated from `../web/jazz_data.js`), spaced-repetition
  and scheduling logic, and per-platform `BlobStore` persistence. The stored JSON schema is
  compatible with the web app's localStorage blob / exported backups (key `jazz_sr_v3`).

* [/iosApp](./iosApp/iosApp) contains the iOS application wrapper. Even if you’re sharing your
  UI with Compose Multiplatform, you need this entry point for your iOS app.

### Run Unit Tests

All data-layer and ViewModel business logic is covered by common tests, run on JVM:

```shell
./gradlew :data:jvmTest :composeApp:jvmTest
```

### Build and Run Android Application

To build and run the development version of the Android app, use the run configuration from the run widget
in your IDE’s toolbar or build it directly from the terminal:
- on macOS/Linux
  ```shell
  ./gradlew :composeApp:assembleDebug
  ```
- on Windows
  ```shell
  .\gradlew.bat :composeApp:assembleDebug
  ```

### Build and Run Desktop (JVM) Application

To build and run the development version of the desktop app, use the run configuration from the run widget
in your IDE’s toolbar or run it directly from the terminal:
- on macOS/Linux
  ```shell
  ./gradlew :composeApp:run
  ```
- on Windows
  ```shell
  .\gradlew.bat :composeApp:run
  ```

### Build and Run Web Application

To build and run the development version of the web app, use the run configuration from the run widget
in your IDE's toolbar or run it directly from the terminal:
- for the Wasm target (faster, modern browsers):
  - on macOS/Linux
    ```shell
    ./gradlew :composeApp:wasmJsBrowserDevelopmentRun
    ```
  - on Windows
    ```shell
    .\gradlew.bat :composeApp:wasmJsBrowserDevelopmentRun
    ```
- for the JS target (slower, supports older browsers):
  - on macOS/Linux
    ```shell
    ./gradlew :composeApp:jsBrowserDevelopmentRun
    ```
  - on Windows
    ```shell
    .\gradlew.bat :composeApp:jsBrowserDevelopmentRun
    ```

### Build and Run iOS Application

To build and run the development version of the iOS app, use the run configuration from the run widget
in your IDE’s toolbar or open the [/iosApp](./iosApp) directory in Xcode and run it from there.

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html),
[Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform/#compose-multiplatform),
[Kotlin/Wasm](https://kotl.in/wasm/)…

We would appreciate your feedback on Compose/Web and Kotlin/Wasm in the public Slack channel [#compose-web](https://slack-chats.kotlinlang.org/c/compose-web).
If you face any issues, please report them on [YouTrack](https://youtrack.jetbrains.com/newIssue?project=CMP).