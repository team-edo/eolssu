# frontend

> Kotlin Multiplatform + Compose Multiplatform으로 만드는 **얼쑤** 앱 (Android · iOS · Web)

화면과 로직은 대부분 `shared` 모듈에 **한 번만** 작성합니다. `androidApp` · `iosApp` · `webApp`은 각 플랫폼에서 `shared`의 `App()`을 띄워 주는 껍데기 역할만 합니다.

## 📌 기본 정보

| 항목 | 값 |
| --- | --- |
| 패키지 | `com.ssafy.eolssu` |
| Kotlin | 2.4.20 |
| Compose Multiplatform | 1.12.1 |
| Android Gradle Plugin | 9.1.1 |
| Gradle | 9.5.1 (Wrapper로 자동 설치) |
| Android | minSdk 24 (Android 7.0), targetSdk 37 |
| iOS | 18.2 이상 |
| Web | Kotlin/Wasm (운영 배포, [지원 브라우저](#웹-지원-브라우저)), Kotlin/JS (빌드·테스트만, 배포하지 않음) |

버전은 모두 [`gradle/libs.versions.toml`](gradle/libs.versions.toml)에서 관리합니다.

Gradle 프로젝트명은 `eolssu`, Android 앱 표시명은 `얼쑤`입니다. Android applicationId는 `com.ssafy.eolssu`이며, 이전 `com.ssafy.eolsu` 앱과 별개로 설치될 수 있습니다. iOS도 bundle identifier가 변경되므로 Mac에서 개발팀·서명 설정을 확인해야 합니다. 이름 변경으로 기존 앱 데이터가 자동 이전된다고 가정하지 않습니다.

## 🛠 필요한 도구

프론트엔드는 **Android Studio**로 개발합니다. Android · Web · 공통 코드는 Windows의 Android Studio에서, iOS는 Mac에서 작업합니다.

| 대상 | 도구 |
| --- | --- |
| 공통 | **Android Studio** 최신 안정 버전 (2026.2.1에서 확인) + `Kotlin Multiplatform` 플러그인 |
| 공통 | JDK 21 — 없으면 Gradle이 첫 빌드 때 자동으로 내려받습니다 ([`gradle/gradle-daemon-jvm.properties`](gradle/gradle-daemon-jvm.properties)) |
| Android | Android SDK API 37, 에뮬레이터 (Android Studio 설치 시 함께 설치) |
| Web | Chrome, Firefox, Safari, Edge 최신 버전 |
| iOS | **Mac + Xcode** + Mac용 Android Studio — Windows에서는 iOS 빌드·실행이 불가능합니다 |

## 🚀 처음 빌드하기 (Android Studio)

> ⚠️ **저장소 경로에 한글이 있으면 안 됩니다.** Windows에서는 경로에 영어가 아닌 글자가 있으면 Android Studio가 Gradle 동기화를 막습니다 (`Your project path contains non-ASCII characters`).
> 예: `C:\Users\SSAFY\Desktop\자율\S15P31E102` ❌ → `C:\Users\SSAFY\Desktop\jayul\S15P31E102` ✅

1. **Android Studio 설치**: [developer.android.com/studio](https://developer.android.com/studio)에서 최신 안정 버전을 받습니다. 처음 실행할 때 설정 마법사는 **Standard**로 진행하고, 라이선스는 항목마다 모두 **Accept**합니다. Android SDK와 에뮬레이터가 함께 설치됩니다.
2. **플러그인 설치**: **File → Settings → Plugins → Marketplace**에서 `Kotlin Multiplatform`(JetBrains)을 설치하고 재시작합니다.
3. **프로젝트 열기**: **File → Open**에서 **저장소 루트가 아니라 `frontend/` 폴더**를 고르고 **Trust**를 누릅니다.
   - 저장소 루트를 열면 Gradle 프로젝트로 인식되지 않아 실행 목록에 `androidApp`이 나오지 않습니다.
   - 왼쪽 Project 창은 처음에 **Android** 보기(모듈·파일 종류별로 묶음, `iosApp`은 안 보임)로 열립니다. 실제 폴더 구조로 보려면 창 맨 위 드롭다운에서 **Project**를 고릅니다.
4. **동기화(Sync) 기다리기**: 처음에는 Gradle, JDK, 라이브러리를 내려받아 **7~8분** 정도 걸립니다. 화면 아래 진행 표시줄이 끝날 때까지 기다립니다.
   - `local.properties`(내 PC의 Android SDK 경로)가 자동으로 생깁니다. 커밋하지 않습니다.
   - `Failed to find target android-37` 오류가 나면 **Tools → SDK Manager**에서 **Android API 37**을 설치하고 다시 동기화합니다.
5. **에뮬레이터 준비**: Android 에뮬레이터는 **Galaxy S10(S10) 하드웨어 프로필**을 사용합니다. 오른쪽 **Device Manager**에서 S10 가상 기기를 선택합니다. 새로 만들 때도 S10 프로필과 API 37 시스템 이미지를 사용합니다.
   - S10 프로필이 목록에 없으면 사용할 프로필의 설정을 확인한 뒤 등록합니다.
6. **실행**: 상단 실행 목록에서 `androidApp`과 **S10 에뮬레이터**를 고르고 ▶ **Run**(`Shift+F10`)을 누릅니다. 첫 빌드는 몇 분 걸립니다.
   - 에뮬레이터만 켜지고 앱이 뜨지 않으면 Run이 실행되지 않은 것입니다. 하단 **Build** 창에서 진행 상황과 오류를 확인합니다.
7. **성공 확인**: 신규 설치에서는 동의 화면이 먼저 열립니다. 실제 약관·인증을 거쳐 홈으로 이동합니다. 약관 API가 비어 있으면 오류와 재시도 화면이 보이며 로그인 성공으로 간주하지 않습니다.

## 🌐 Web 실행 (Android Studio)

실행 목록에는 처음에 `androidApp`만 있습니다. 웹은 아래 둘 중 하나로 실행합니다.

- **Gradle 창**: 오른쪽 **Gradle** 창 → `webApp` → `Tasks` → `kotlin browser` → `wasmJsBrowserDevelopmentRun`을 더블클릭합니다. 한 번 실행하면 실행 목록에 추가되어 다음부터 ▶로 실행할 수 있습니다.
- **Terminal 탭**: 아래 **Terminal**에서 실행합니다 (Android Studio 터미널 기본값은 PowerShell).

  ```powershell
  .\gradlew.bat :webApp:wasmJsBrowserDevelopmentRun
  ```

빌드가 끝나면 터미널에 `Loopback: http://localhost:8080/`이 나옵니다. 브라우저에서 이 주소를 열면 앱이 보입니다. 화면이 깨지거나 동작하지 않으면 브라우저 개발자 도구(F12)의 Console을 확인합니다. 멈출 때는 실행 창의 ■(Stop)이나 `Ctrl+C`를 누릅니다.

첫 화면(`webApp/src/webMain/resources/index.html`)은 앱을 불러오는 동안 로고와 로딩 표시를 보여 줍니다. 앱 파일을 받지 못하거나 시작 중 오류가 나면 실패 안내를, 20초가 지나면 지연 안내를, 브라우저가 Wasm GC를 지원하지 않으면 다른 브라우저 안내를 새로고침 버튼과 함께 띄웁니다. 안내가 뜬 뒤에도 앱이 화면을 붙이면 안내는 사라집니다.

탭 아이콘(`favicon.ico`)·홈 화면 아이콘(`apple-touch-icon.png`, `icon-*.png`)과 `manifest.webmanifest`도 같은 폴더에 있습니다. 홈 화면에 추가하면 주소창 없는 단독 창(`standalone`)으로 열립니다. 아이콘은 iOS 앱 아이콘(`iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/app-icon-1024.png`)에서 `frontend/`의 `python -P tools/web-icons/build_web_icons.py`(Pillow 13.0.0)로 다시 만듭니다.

### 웹 지원 브라우저

Kotlin/Wasm은 WebAssembly GC와 예외 처리(legacy) 제안을 지원하는 브라우저에서만 실행됩니다. 아래 버전은 Kotlin 공식 문서([Browser versions](https://kotlinlang.org/docs/wasm-configuration.html#browser-versions))에서 별도 설정 없이 동작한다고 밝힌 최소 버전입니다.

| 브라우저 | 최소 버전 |
| --- | --- |
| Chrome, Edge 등 Chromium 기반 | 119 이상 |
| Firefox | 120 이상 |
| Safari | 18.2 이상 (macOS 15.2, iOS·iPadOS 18.2) |
| iPhone·iPad의 모든 브라우저 | iOS·iPadOS 18.2 이상 (WebKit을 사용) |

이보다 낮은 버전에서는 첫 화면이 다른 브라우저 안내를 띄웁니다. 이 표는 공식 문서의 기준이며 각 버전을 실제 기기에서 확인한 결과가 아닙니다.

### 웹에서 아직 지원하지 않는 기능

2026-11-24 `develop`(`a318b3e10`) 기준으로 웹(`shared/src/webMain/.../platform/PlatformModule.kt`)에 등록되지 않아 앱과 다르게 동작하는 플랫폼 기능은 없습니다. 새로 생기면 이 절에 표로 적고, 작업이 병합되면 그 MR에서 해당 줄을 지웁니다. 운영 웹은 마지막으로 배포한 커밋 기준이므로 [웹 빌드·테스트·배포](#웹-빌드테스트배포)의 방법으로 배포 커밋을 확인합니다.

웹 녹음(연주 평가·한 음씩 음 판정·기초 배우기 듣기·구간 재평가)은 앱과 다음이 다릅니다([녹음 파일 규격](../docs/specs/recording-file.md#클라이언트-녹음), S15P31E102-1262).

- 16kHz 모노 WAV로 올리고 서버가 AAC m4a로 바꿔 저장합니다. 마이크는 HTTPS(또는 localhost) 페이지에서만 열립니다.
- 녹음은 탭 메모리에만 둡니다. 업로드가 실패한 뒤 새로 고치면 녹음이 사라져 다시 연주해야 합니다.
- 브라우저는 기기 볼륨을 읽지 못해, 평가 녹음 중 장단을 늘 약 1/10(-20dB)로 줄입니다.
- 브라우저가 막은 마이크 권한은 주소창의 사이트 설정에서만 풀 수 있어 앱은 거부 안내만 띄웁니다.

## 🍎 iOS 실행 (Mac)

Windows에서는 iOS 코드(`iosMain`)를 작성할 수만 있고, 컴파일·실행은 Mac에서 합니다.

1. Mac에 **Xcode**를 설치하고 **한 번 실행**합니다. Xcode를 업데이트한 뒤에도 한 번 실행합니다.
2. Mac에 **Android Studio**와 `Kotlin Multiplatform` 플러그인을 설치합니다.
3. 같은 저장소의 `frontend/` 폴더를 엽니다. 동기화가 끝나면 실행 목록에 iOS 실행 구성이 생깁니다.
4. iOS 시뮬레이터를 고르고 ▶ Run을 누릅니다. Android Studio가 내부에서 Xcode를 불러 빌드합니다.
5. Swift 파일이나 `Info.plist`(마이크 권한 문구 등)를 고칠 때는 `iosApp`을 Xcode로 엽니다.

실제 iPhone에서 개인 개발팀으로 실행할 때는 `frontend/iosApp/Configuration/Config.local.xcconfig`를 만들고 아래 값을 본인 설정으로 바꿉니다. 이 파일은 Git에서 제외하며 개인 Team ID를 공유 설정에 넣지 않습니다.

```xcconfig
TEAM_ID=본인_TEAM_ID
PRODUCT_BUNDLE_IDENTIFIER=com.ssafy.eolssu.본인개발용식별자
```

Apple 로그인(Sign in with Apple) 권한은 유료 Apple Developer 팀에서만 서명됩니다. 무료 Personal Team으로 빌드할 때는 같은 파일에 `CODE_SIGN_ENTITLEMENTS =`를 추가해 권한 파일을 빼고 빌드합니다. 이때 Apple 로그인 버튼은 보이지만 동작하지 않습니다.

Xcode에서 `frontend/iosApp/iosApp.xcodeproj`를 열고 `iosApp` scheme과 연결한 iPhone을 선택해 실행합니다. 기존 설치 앱의 데이터를 유지하려면 같은 개발용 Bundle ID를 사용합니다. 출시용 기본 Bundle ID는 `com.ssafy.eolssu`입니다.

Mac의 `frontend/`에서 네이티브 코드 컴파일과 시뮬레이터 테스트를 확인할 수 있습니다.

```sh
./gradlew :shared:compileKotlinIosArm64 :shared:compileTestKotlinIosSimulatorArm64
./gradlew :shared:iosSimulatorArm64Test
```

iOS 담당은 develop을 주기적으로 받아 iOS 빌드가 깨지지 않았는지 확인합니다. 공통 코드만 바뀌어도 iOS 빌드가 깨질 수 있습니다.

### 무료 계정의 iPhone 개발 서명

Xcode의 **Settings → Apple Accounts**에 로그인하고 Personal Team을 확인합니다. 브라우저의 Apple Developer 로그인과는 별도입니다. **Settings → Components**에서 iOS 플랫폼 설치도 완료해야 합니다.

위 로컬 설정 파일의 Team ID와 개발용 Bundle ID를 사용합니다. `Config.xcconfig`가 이 파일을 선택적으로 읽으며, 팀 공통 파일에는 개인 Team ID나 인증서를 넣지 않습니다.

`iosApp/iosApp.xcodeproj`를 Xcode에서 열고 `iosApp` 타깃의 **Signing & Capabilities**에서 자동 서명과 개발팀이 적용됐는지 확인합니다. 최초 기기 연결은 iPhone을 Mac에 케이블로 연결하고 신뢰·개발자 모드를 직접 허용해야 합니다. 페어링 후 네트워크 연결을 설정하면 무선 실행이 가능합니다. 원격 접속만으로 최초 기기 페어링을 마쳤다고 가정하지 않습니다.

서명 없이 컴파일만 확인하려면 `frontend/`에서 아래 명령을 사용합니다. 이 성공은 iPhone 설치·실행 또는 API 검증 통과를 뜻하지 않습니다.

```sh
./gradlew :shared:compileKotlinIosArm64
```

[Apple 기기 페어링 안내](https://help.apple.com/xcode/mac/current/en.lproj/devbc48d1bad.html) · [작업 환경·검증 기록](../docs/plans/S15P31E102-377.md)

## 🔨 명령어로 빌드·실행

`frontend/`에서 실행합니다. Git Bash는 `./gradlew`, PowerShell·cmd는 `.\gradlew.bat`을 씁니다.
Android Studio 밖에서 실행할 때는 `JAVA_HOME`이 JDK 17 이상을 가리켜야 합니다 (Gradle 9 요구 사항).

| 하려는 것 | 명령 | 결과 |
| --- | --- | --- |
| Android 디버그 APK 빌드 | `./gradlew :androidApp:assembleDebug` | `androidApp/build/outputs/apk/debug/androidApp-debug.apk` |
| 연결된 기기·에뮬레이터에 설치 | `./gradlew :androidApp:installDebug` | 설치만 합니다. 앱 아이콘을 눌러 실행합니다 |
| 출시와 같은 R8 빌드를 기기에서 확인 | `./gradlew :androidApp:installBenchmark` | debug 키로 서명한 `com.ssafy.eolssu.benchmark`를 설치합니다. Play 앱과 나란히 설치되며 간편 로그인은 되지 않습니다([R8 기록](../docs/knowledge/frontend/android-r8-release-build.md)) |
| Web 개발 서버 (Wasm, 권장) | `./gradlew :webApp:wasmJsBrowserDevelopmentRun` | `http://localhost:8080/`에 개발 서버를 띄웁니다 |
| Web 개발 서버 (JS) | `./gradlew :webApp:jsBrowserDevelopmentRun` | 로컬 개발 서버를 띄웁니다. 운영에는 배포하지 않습니다 |
| Web 배포용 빌드 (Wasm) | `./gradlew :webApp:wasmJsBrowserDistribution` | `webApp/build/dist/wasmJs/productionExecutable/` (압축 전 약 39MB: Wasm 두 개 약 20MB(gzip 약 6.5MB), `composeResources` 약 16MB 중 글꼴 약 8MB. 2026-11-24 측정) |
| iOS | Mac에서만 가능 — 위의 "iOS 실행 (Mac)" 참고 | — |

- Android Studio 없이 명령줄만으로 빌드하려면 [Android 명령줄 도구](https://developer.android.com/studio#command-line-tools-only)를 SDK의 `cmdline-tools/` 아래에 설치합니다. `frontend/local.properties`에는 `sdk.dir=C\:/Users/<사용자>/AppData/Local/Android/Sdk`처럼 **콜론을 이스케이프하고 슬래시(`/`) 경로**로 적습니다. Windows 경로를 그대로 쓰면 경로 해석 또는 lint `PropertyEscape` 검사에 실패합니다. 설치 명령은 해당 버전의 `--help`로 확인합니다(cmdline-tools 23은 `android sdk` 제공).
- 처음 하는 웹 빌드는 Node.js와 웹 의존성을 내려받아 오래 걸립니다 (확인한 PC에서 약 6분).
- 첫 웹 빌드가 `An input file was expected to be present but it doesn't exist` 오류로 실패한 적이 있습니다. 같은 명령을 다시 실행하면 성공했습니다.
- 웹을 처음 빌드하면 `kotlin-js-store/wasm/yarn.lock`이 생깁니다. 모두가 같은 웹 의존성 버전을 쓰도록 **커밋합니다**.

## ✅ 테스트

| 대상 | 명령 |
| --- | --- |
| Android | `./gradlew :shared:testAndroidHostTest` |
| Web (Wasm) | `./gradlew :shared:wasmJsTest` |
| Web (JS) | `./gradlew :shared:jsTest` |
| iOS (Mac에서만) | `./gradlew :shared:iosSimulatorArm64Test` |

## 📁 폴더 구조

```
frontend/
├── settings.gradle.kts         # 모듈 등록 (shared, androidApp, webApp)
├── build.gradle.kts            # 루트 빌드 설정 (플러그인 선언만)
├── gradle.properties           # 빌드 옵션 (메모리, 캐시)
├── gradle/
│   ├── libs.versions.toml      # 라이브러리·플러그인 버전 관리
│   ├── gradle-daemon-jvm.properties  # Gradle이 쓸 JDK 버전 (21)
│   └── wrapper/                # Gradle 버전 고정
├── gradlew, gradlew.bat        # Gradle 실행 스크립트 (Mac·Linux / Windows)
│
├── shared/                     # ★ 공통 UI·로직 — 코드 대부분이 여기
│   ├── build.gradle.kts        # 빌드 대상 플랫폼, 소스 세트별 라이브러리
│   └── src/
│       ├── commonMain/
│       │   ├── kotlin/com/ssafy/eolssu/   # 공통 코드. app / feature / data / core 패키지로 고정
│       │   └── composeResources/         # 공통 리소스 (drawable, font, values)
│       ├── androidMain/        # Android 전용 코드 (platform/audio·storage·permission, App(context): MainActivity가 부르는 입구)
│       ├── iosMain/            # iOS 전용 코드 (platform/…, MainViewController.kt: iOS에 화면을 넘겨주는 입구)
│       ├── jsMain/             # Web(JS) 전용 코드
│       ├── wasmJsMain/         # Web(Wasm) 전용 코드
│       ├── webMain/            # Web(JS·Wasm) 공통 코드 (platform/storage, App(onNavHostReady): main.kt가 부르는 입구)
│       ├── commonTest/         # 공통 테스트
│       ├── androidHostTest/    # Android 테스트 (PC에서 실행)
│       ├── iosTest/            # iOS 테스트
│       └── webTest/            # Web 테스트 (JS·Wasm 공용)
│
├── androidApp/                 # Android 껍데기
│   ├── build.gradle.kts        # 앱 ID, minSdk, 버전 번호
│   └── src/main/
│       ├── AndroidManifest.xml # 권한, 화면 방향, 앱 아이콘
│       ├── kotlin/.../MainActivity.kt  # App(context)를 띄움
│       └── res/                # 앱 아이콘, 앱 이름
│
├── iosApp/                     # iOS 껍데기 (Xcode 프로젝트, Mac 전용)
│   ├── iosApp.xcodeproj/
│   ├── Configuration/Config.xcconfig   # 번들 ID, 개발팀 ID
│   └── iosApp/
│       ├── iOSApp.swift        # 앱 시작점
│       ├── ContentView.swift   # MainViewController를 띄움
│       ├── Info.plist          # 권한 문구 등 앱 설정
│       └── Assets.xcassets/    # 앱 아이콘
│
└── webApp/                     # Web 껍데기
    ├── build.gradle.kts
    └── src/webMain/
        ├── kotlin/.../main.kt  # 브라우저에 App()을 띄움
        └── resources/
            ├── index.html      # 페이지 제목, 로딩 화면
            └── styles.css
```

공통 코드 안의 패키지 구조(`app / feature / data / core`), 각 영역의 역할, 화면 파일 이름 규칙은 [docs/architecture.md](docs/architecture.md)에, 두 개발자가 담당을 나누고 공동 파일을 조율하는 규칙은 [docs/collaboration.md](docs/collaboration.md)에 있습니다. 새 파일을 만들기 전에 두 문서를 먼저 봅니다.

## ✍️ 코드는 어디에 쓰나요?

| 하려는 것 | 위치 |
| --- | --- |
| 화면, 화면 상태(ViewModel), 비즈니스 로직 | `shared/src/commonMain/kotlin`의 `feature/<기능>`, `data/<기능>`, `core` — 세부 기준은 [docs/architecture.md](docs/architecture.md) |
| 플랫폼 API가 필요한 기능 (마이크, 저장소, 권한 등) | 인터페이스는 `commonMain`의 `core/audio`·`core/storage` 등, 구현은 `androidMain` · `iosMain` · `webMain`의 `platform/`, 등록은 각 소스 세트의 `platform/PlatformModule.kt` |
| JS·Wasm 둘 다 쓰는 웹 공통 코드 | `shared/src/webMain` |
| 이미지, 폰트, 문구 | `shared/src/commonMain/composeResources/` 아래 `drawable/`, `font/`, `values/` |
| 앱 권한, 아이콘, 앱 이름, 화면 방향 | `androidApp` / `iosApp` / `webApp` |

### 공통 코드와 플랫폼 코드 잇기

- **작은 기능**: `expect` / `actual`을 씁니다. 공통 코드에 `expect`로 선언하고 `androidMain` · `iosMain` · `jsMain` · `wasmJsMain`(또는 `webMain`)에 `actual` 구현을 둡니다.
- **큰 기능**(마이크 입력, 저장소 등): `commonMain`에 인터페이스를 두고, 플랫폼별 클래스가 구현합니다. 구현은 각 소스 세트의 `platform/PlatformModule.kt`(Koin 모듈)에 등록합니다. 테스트할 때 가짜 구현으로 바꾸기 쉽습니다. 연결 방법은 [docs/architecture.md](docs/architecture.md) 8절을 봅니다.

### 리소스 사용

`composeResources/`에 파일을 넣고 빌드하면 접근 코드가 자동으로 생깁니다. 모든 플랫폼에서 같은 방식으로 씁니다.

```kotlin
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.home_brand

Text(stringResource(Res.string.home_brand))   // composeResources/values/strings.xml의 문구
```

### 테마와 글꼴

색·글꼴·모서리는 `core/theme`의 `EolssuTheme`에 있습니다. 최신 연두 디자인의 primary `#ADBE31`, 바탕 `#F9FAF9`, 먹색 `#27221E`를 사용합니다. 밝은 연두 위에는 먹색, 먹색·재생·녹음 위에는 별도 밝은 전경 토큰을 사용합니다. `EolssuColorsContrastTest`는 실제 주요 텍스트 색 조합 4.5:1과 선택 경계 3:1을 검사하며 모든 화면의 접근성 검증을 대신하지 않습니다.

Web은 기기 글꼴로 한글·한자를 그리지 않기 때문에 글꼴 파일을 `composeResources/font/`에 넣었습니다.

| 파일 | 용도 | 글자 범위 |
| --- | --- | --- |
| `eolssu_sans_kr_regular·semibold·bold.otf` | 제목·본문·버튼·사용자가 입력한 글자 | IBM Plex Sans KR 1.001(Google Fonts `ofl/ibmplexsanskr`)의 모든 글자를 CFF로 바꿔 굵기마다 약 2.8MB → 2.0MB로 줄인 수정본. OFL 예약 글꼴 이름 때문에 글꼴 이름을 `Eolssu Sans KR`로 바꿨고 라이선스 원문은 `files/licenses/ibm_plex_sans_kr_ofl.txt` |
| `noto_serif_kr_semibold·bold.otf` | 율명·곡 제목 | Noto Serif CJK KR 서브셋, 한글 2,350자·옥타브 -2~2 율명 한자·영숫자 |
| `eolssu_yul_supplement.ttf` | 하배이·배유·중청대 | Jigmo2 20250912에서 실제 세 글자만 남긴 보조 폰트, 3,808바이트(CC0 1.0) |
| `seal_brush.ttf` | 연습곡 표지의 붉은 '얼쑤' 도장 | 나눔손글씨 붓(Google Fonts `ofl/nanumbrushscript`)에서 `얼쑤` 2자만 남긴 서브셋(약 4.5KB). OFL 예약 글꼴 이름 때문에 글꼴 이름을 `Eolssu Seal Brush`로 바꿨고 라이선스 원문은 `files/licenses/nanum_brush_script_ofl.txt` |

- 세리프 서브셋에는 한글이 2,350자만 있으므로 **사용자가 입력한 글자는 본문 글꼴로 씁니다.**
- 기본 율명 폰트는 [`tools/fonts/build_fonts.py`](tools/fonts/build_fonts.py)의 `SERIF_YUL`(정간보 원본 표기 옥타브 -2~2와 운지표 글자)을 담습니다. 앱의 `SerifYulHanja`(`core/model/fingering/YulNotation.kt`)와 같게 유지하고, `YulFontCoverageTest`가 실제 글꼴 파일에 글리프가 있는지 검사합니다. 하배이·배유·중청대는 보조 폰트의 실제 한자로 표시합니다.
- Noto Serif KR(SubsetOTF)에는 탁성·하배·중청 율명이 없어 S15P31E102-923부터 같은 디자인의 Noto Serif CJK KR(OTF/Korean)에서 만듭니다. 그래서 대금의 배·하배 율명도 단소·소금과 같은 세리프 글리프로 그립니다. 변(氵·亻) + 율명 조합(`ComposedGlyph`)은 글꼴 어디에도 없는 글자의 예비 경로로만 남습니다. 두 글꼴 모두 `♭`가 없어 계이름의 내림표는 그림으로 넣습니다.
- 기존 글꼴은 SIL Open Font License 1.1, 율명 보조 폰트는 CC0 1.0입니다. 원문은 [`tools/fonts/licenses/`](tools/fonts/licenses/), 앱에 포함하는 원문은 `shared/src/commonMain/composeResources/files/licenses/`에 있습니다.

본문 글꼴을 다시 만들 때(`frontend/`에서):

```bash
python -m pip install --user fonttools==4.60.1
SRC="${TMPDIR:-/tmp}/eolssu-body-font-src"; mkdir -p "$SRC"
for w in Regular SemiBold Bold; do curl -fL -o "$SRC"/IBMPlexSansKR-$w.ttf "https://raw.githubusercontent.com/google/fonts/main/ofl/ibmplexsanskr/IBMPlexSansKR-$w.ttf"; done
PYTHONUTF8=1 python -P tools/fonts/build_body_font.py "$SRC"
```

글자는 빼지 않고 TrueType 윤곽(이차 곡선)을 CFF 윤곽(삼차 곡선)으로 합쳐(허용 오차 1/1000em) 줄입니다. 글자 범위·폭·행간은 원본과 같습니다. [변환·검증 기록](../docs/knowledge/frontend/web-korean-fonts.md#본문-글꼴-cff-변환-s15p31e102-1267)을 참고합니다.

Noto 세리프 서브셋을 다시 만들 때(`frontend/`에서, Python 3.11 이상):

```bash
python -m pip install --user fonttools==4.60.1
SRC="${TMPDIR:-/tmp}/eolssu-fonts-src"; mkdir -p "$SRC"
for w in SemiBold Bold; do curl -fL -o "$SRC"/NotoSerifCJKkr-$w.otf "https://raw.githubusercontent.com/notofonts/noto-cjk/main/Serif/OTF/Korean/NotoSerifCJKkr-$w.otf"; done
PYTHONUTF8=1 python -P tools/fonts/build_fonts.py "$SRC"
```

두 스크립트는 원본 파일의 SHA-256을 확인한 뒤 만들고, 같은 원본이면 항상 같은 파일을 만듭니다. 원본 폴더(`$SRC`)는 저장소 밖 임시 폴더라 커밋되지 않습니다. 원본이 바뀌어 해시가 다르면 스크립트가 멈추니, 새 원본을 확인한 뒤 해시를 갱신합니다.

하배이·배유·중청대 보조 폰트는 [Jigmo 공식 배포](https://kamichikoichi.github.io/jigmo/)의 `Jigmo-20250912.zip`에서 `Jigmo2.ttf`를 꺼내 `python -P tools/fonts/build_supplementary_yul.py <Jigmo2.ttf>`로 만듭니다(fonttools 4.60.1). 원본 해시·세 코드포인트·실제 윤곽을 검사하고 이름을 `Eolssu Yul Supplement`로 바꿉니다. CC0 원문은 `tools/fonts/licenses/Jigmo-CC0.txt`와 앱의 `files/licenses/jigmo_cc0.txt`에 보존합니다. [출처·검증 기록](../docs/knowledge/frontend/web-korean-fonts.md#보충-평면-율명-세-글자-s15p31e102-813)을 참고합니다.

### 시김새 소리

시김새 창(처음 배우는 시김새 11개)의 단소 소리는 `composeResources/files/sigimsae/*.mp3`(모노 44.1kHz 96kbps, 각 2.56초)에 미리 넣어 두고, 모든 플랫폼이 `core/audio`의 `SoundPlayer`로 그대로 틉니다. 녹음이 아니라 와이어프레임의 Web Audio 합성(`playSig`: 단소 녹음에서 잰 배음·숨 세기, 붓끝 높이를 따라가는 음높이)을 브라우저 없이 다시 계산해 만든 파일입니다. 파일 처음이 붓질 처음(0초)이고 붓질은 2.16초입니다.

`feature/guide/Sigimsae.kt`의 그림(`BasicSigimsae`)을 바꾸면 [`tools/sigimsae-sound/build_sigimsae_sounds.mjs`](tools/sigimsae-sound/build_sigimsae_sounds.mjs)의 `SIGIMSAE` 값도 고치고 다시 만듭니다(`frontend/`에서, Node 18 이상과 libmp3lame이 든 ffmpeg 필요).

```bash
node tools/sigimsae-sound/build_sigimsae_sounds.mjs
```

잡음은 고정된 씨앗을 써서 같은 입력이면 같은 소리가 납니다. MP3 바이트는 ffmpeg 판에 따라 조금 다를 수 있습니다(2026-10-29 ffmpeg 9.0.1로 만듦).

### 라이브러리 추가

1. `gradle/libs.versions.toml`에 버전과 라이브러리를 등록합니다.
2. `shared/build.gradle.kts`의 해당 소스 세트(`commonMain.dependencies` 등)에 추가합니다.
3. `commonMain`에 넣는 라이브러리는 **Android · iOS · JS · Wasm을 모두 지원**해야 빌드됩니다. 지원 여부는 [klibs.io](https://klibs.io)에서 확인합니다.
4. `libs.versions.toml`은 모두가 건드리는 파일이므로, 라이브러리를 추가하기 전에 팀에 공유합니다.

## ⚠️ 주의할 점

- **Windows에서는 iOS 코드(`iosMain`, `iosApp`)를 컴파일할 수 없습니다.** iOS 쪽이 깨져도 Windows에서는 알 수 없으니, Mac 사용자가 주기적으로 빌드해서 확인합니다.
- **`gradlew` 실행 권한**: Windows에서 커밋하면 실행 권한이 빠질 수 있습니다. `gradlew`를 새로 추가하거나 교체할 때는 `git update-index --chmod=+x frontend/gradlew`를 실행한 뒤 커밋합니다.
- **커밋하지 않는 파일**: `local.properties`, `build/`, `.gradle/`, `.kotlin/`, `.idea/`, `xcuserdata/` ([`.gitignore`](.gitignore)에 등록됨)
- **커밋하는 파일**: 웹을 처음 빌드하면 생기는 `kotlin-js-store/`(웹 의존성 lock 파일)는 커밋합니다.
- **iOS 번들 ID**: Android `applicationId`와 같은 `com.ssafy.eolssu`입니다. App Store에 등록한 뒤에는 바꿀 수 없습니다. Mac에서 빌드하려면 `iosApp/Configuration/Config.xcconfig`의 `TEAM_ID`에 Apple 개발팀 ID를 넣습니다(개인 값이면 커밋하지 않음).
- **iOS 버전·언어**: 버전·빌드 번호는 `iosApp/Configuration/Config.xcconfig`의 `MARKETING_VERSION`·`CURRENT_PROJECT_VERSION`입니다. App Store에 올릴 때마다 빌드 번호를 마지막 업로드보다 크게 올립니다. 개발 언어는 한국어(`ko`)이고, `iosApp/iosApp/ko.lproj`가 있어야 App Store에 한국어로 표시됩니다([S15P31E102-814](../docs/plans/S15P31E102-814.md)).
- **스토어 업데이트 안내**: 구버전 앱을 켤 때마다(백그라운드에서 다시 앞으로 가져올 때 포함) Play 스토어·App Store에 설치 버전보다 새 버전이 있으면 '업데이트하기'·'나중에' 안내를 띄웁니다(`core/update`, `platform/update`). '업데이트하기'는 Android에서 Play 즉시 업데이트, iOS에서 앱 안 App Store 시트를 엽니다. 스토어에서 설치한 앱에서만 동작하므로 QA APK·Ad Hoc IPA·TestFlight에서는 뜨지 않습니다. Android는 Play 내부 앱 공유나 테스트 트랙으로, iOS는 App Store 설치본으로 확인합니다([결정 문서](../docs/decisions/frontend-in-app-update.md)).
- **새 버전 푸시**: 앱은 설치된 버전의 FCM 토픽(`update-android-<versionCode>`, `update-ios-<버전>`)을 구독합니다. Firebase 설정은 `androidApp/src/main/res/values/firebase.xml`과 `iosApp/iosApp/GoogleService-Info.plist`에 있고, 출시 뒤 Firebase 콘솔에서 구버전 토픽으로 보냅니다(발송 절차는 [결정 문서](../docs/decisions/frontend-in-app-update.md)).
- **iOS 출시 설정**: 마이크 권한 설명·표시 이름은 `iosApp/iosApp/Info.plist`, 개인정보 매니페스트는 `iosApp/iosApp/PrivacyInfo.xcprivacy`에 있습니다. 매니페스트의 수집 항목은 App Store Connect의 개인정보 라벨과 같게 맞춥니다([S15P31E102-373 계획](../docs/plans/S15P31E102-373.md)).

## Android release 검증

곡 목록·상세는 S15P31E102-256의 `/api/songs` 계약을 사용합니다. `score` 원문을 공통 악보로 해석하며 원본 ID·tick·버전을 보존합니다. 활성 악기는 `/api/instruments`의 ID/code로 연결합니다. 조회 실패는 오류·재시도로 표시하며 샘플 곡으로 대체하지 않습니다. [계약·변환·검증 기록](../docs/knowledge/frontend/song-catalog-adapter.md)을 참고하세요.

격리된 로컬 API를 검증할 때만 아래 설정을 사용합니다. `CatalogLocalServerTest`는 환경 변수가 없으면 명시적으로 skip됩니다. 실제 서버 통합 통과를 기록하려면 V6 공개 곡 4개를 갖춘 로컬 서버를 기동하고 환경 변수를 지정해야 합니다.

```powershell
$env:EOLSSU_CATALOG_TEST_URL = 'http://127.0.0.1:18081'
./gradlew.bat :shared:testAndroidHostTest :androidApp:assembleDebug '-Peolssu.debugApiBaseUrl=http://127.0.0.1:18081' --max-workers=2
adb reverse tcp:18081 tcp:18081
```

V21의 장단10개를 가진 폐기용 로컬 서버에서 구음·혼합 묶음·4분/점4분음표 단위가 Repository와 읽기 화면 상태에 도달하는지는 별도로 검사합니다. `EOLSSU_JANGDAN_TEST_URL` 미지정 시 해당 검사는 명시적으로 skipped입니다. 실기기 화면·청취 검증과 구분합니다.

```powershell
$env:EOLSSU_JANGDAN_TEST_URL = 'http://127.0.0.1:18081'
./gradlew.bat :shared:testAndroidHostTest --tests '*JangdanLocalServerTest'
```

debug API 변경은 loopback 주소만 허용합니다. release는 이 속성과 관계없이 운영 HTTPS 주소를 사용하며 debug 전용 cleartext 예외를 포함하지 않습니다. 공개 곡 조회 검사는 로그인·녹음·분석 전체 흐름의 증거가 아닙니다.

현재 설정은 `com.ssafy.eolssu`, minSdk 24, target/compileSdk 37입니다. 아래 PowerShell 스크립트는 실제 Gradle task로 단위 검사·debug/release lint·debug APK·release APK/AAB를 실행합니다. 버전은 Console의 최고 사용 버전을 확인한 뒤 지정해야 하며 기본값 2/1.0.0은 제출 승인을 의미하지 않습니다.

```powershell
# 승인된 키의 경로·암호·별칭을 비밀 저장소에서 환경 변수로 주입한 뒤 실행합니다.
# EOLSSU_STORE_FILE, EOLSSU_STORE_PASSWORD, EOLSSU_KEY_ALIAS, EOLSSU_KEY_PASSWORD
./tools/build-android-release.ps1 -VersionCode 2 -VersionName '1.0.0'
python tools/inspect_android_package.py androidApp/build/outputs/apk/release/androidApp-release.apk androidApp/build/outputs/bundle/release/androidApp-release.aab
```

release 키가 없으면 이 스크립트는 중단하며 debug 키로 대체하지 않습니다. 키·비밀번호를 저장소나 명령 이력에 적지 않습니다. 서명 값이 Gradle configuration cache에 저장되지 않도록 스크립트는 `--no-configuration-cache`를 사용합니다. Compose preview 실행 도구는 Android 앱의 `debugImplementation`에만 둡니다.

최종 APK는 `apksigner verify --verbose --print-certs`, AAB는 `jarsigner -verify`와 `bundletool validate`로 검사합니다. APK의 ZIP 정렬은 `zipalign -c -P 16 -v 4`로, ELF와 패키지 안의 네이티브 라이브러리는 위 Python 도구로 검사합니다. AAB에서 `bundletool build-apks`로 만든 패키지도 설치·실행해야 합니다. CLI 플래그는 설치한 버전의 도움말로 확인하고 암호는 비밀 파일/입력 방식으로 전달합니다.

필수 완료조건은 실제 인증→곡/악보→녹음→업로드→분석→결과/이력, 실패 복구, 폰/태블릿·큰 글꼴·IME/insets·TalkBack, 실제 마이크·오디오 경합 검사입니다. 단위 검사나 APK 설치만으로 제출 준비를 완료하지 않습니다. 정책 조건과 PC/에뮬레이터·실기기 결과는 [Android release 준비 기록](../docs/knowledge/frontend/android-play-release-readiness.md)에서 분리합니다.

## 📚 참고 자료

- [Kotlin Multiplatform 시작하기](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)
- [Compose Multiplatform](https://kotlinlang.org/compose-multiplatform/)
- [Kotlin/Wasm](https://kotl.in/wasm/)


## 웹 빌드·테스트·배포

운영 웹(`https://eolssu.site`)은 develop에 병합해도 바뀌지 않습니다. 담당자가 Jenkins `eolssu-frontend-web-수동배포용` 잡의 **Build with Parameters**에서, 성공한 `eolssu-backend` 빌드의 develop 전체 커밋 SHA를 `REVISION`에 넣어 실행해야 바뀝니다. 잡은 그 SHA가 develop 이력에 있는지 확인한 뒤 `:shared:wasmJsTest`, `:shared:jsTest`, production Wasm 최적화와 `:webApp:wasmJsBrowserDistribution`을 실행합니다. 후보 자산 검증을 통과하면 프론트 컨테이너만 교체하고, 배포 검사가 실패하면 이전 웹으로 자동 복구합니다. 백엔드·DB는 건드리지 않으며 교체할 때 몇 초 연결이 끊길 수 있습니다. Linux·JDK 21·Google Chrome(`CHROME_BIN=/usr/bin/google-chrome`) 환경이고 Android·iOS 패키지는 포함하지 않습니다.

- **배포 주기:** 웹에 영향 있는 MR 묶음이 병합되면 그날 안에 배포합니다. 그런 MR이 없어도 develop이 운영 웹보다 앞서 있으면 평일 1회 배포합니다. 담당은 우동균입니다(2026-11-24 결정). 반복 부담이 커지면 하루 1회 예약 배포를 별도 작업으로 검토합니다.
- **걸리는 시간:** 잡에는 20분 제한이 있습니다. Gradle 캐시가 데워져 있으면 약 8분, 캐시가 비어 있으면 18분 가까이 걸립니다(2026-11-24 #24, 1,061초). 제한 시간으로 ABORTED되면 운영은 바뀌지 않으므로 같은 SHA로 한 번 더 실행합니다.
- **배포 커밋 확인:** `curl -fsS https://eolssu.site/__eolssu_release.json`의 `sha`가 `REVISION`과 같아야 합니다.
- **develop 웹 테스트:** `eolssu-web-tests` 잡([web-tests.Jenkinsfile](../infra/jenkins/web-tests.Jenkinsfile))은 웹 빌드 입력이 바뀐 develop 커밋마다 `eolssu-backend`가 예약하며, `:shared:wasmJsTest`·`:shared:jsTest`만 돌리고 배포하지 않습니다. 배포 잡과 같은 `edo1-web` 노드를 써서 두 잡이 겹치면 나중 잡이 기다립니다. 정의는 develop에 있고, 운영 Jenkins 적용은 [S15P31E102-1275](https://ssafy.atlassian.net/browse/S15P31E102-1275)에서 [적용 절차](../infra/jenkins/controller/README.md#웹-테스트-잡)대로 합니다.

설치·실패 복구·이전 릴리스로 되돌리기는 [운영 문서](../infra/runtime/README.md#프론트-웹-수동-배포), 배포 순서 규칙은 [Jenkins 배포 순서](../docs/specs/jenkins-deploy-order.md#프론트-웹-수동-배포-s15p31e102-520)를 따릅니다. 잡의 테스트 통과가 업무 기능 전체를 검증한다는 뜻은 아닙니다.

### 악보 작성 지원음

편집기의 율명 입력·오선 이동·수정 후보는 `ReferenceCatalogRepository.authoringCapabilities`의 동일한 선택 목록을 사용합니다. 운지 미등록을 연주 불가로 표시하지 않으며, 최종 저장 오류의 위치를 보이고 초안은 보존합니다. 악기 변경과 음원 악기 재선택 시 다시 검증합니다. 실제 연습 음원 다운로드가 지원음 오류로 거절되면 반복 생성 대신 악보 편집을 안내합니다. [작성 일관성 스펙](../docs/specs/score-authoring-consistency.md)을 따릅니다.


### 격리 지휘통제 화면과 실제 앱 이벤트 연결

S15P31E102-283의 실제 앱 시연에서는 Android debug를 다음과 같이 빌드합니다.

```bash
./gradlew :shared:testAndroidHostTest :androidApp:assembleDebug \
  -Peolssu.qaLive=true -Peolssu.debugApiBaseUrl=http://127.0.0.1:18180
```

이 빌드는 `com.ssafy.eolssu.qa`로 설치됩니다. 기기의 루프백 18180을 실행 중인 격리 중계에 연결한 뒤 앱을 조작하면 `adb logcat -v raw -s EolssuLive:I`로 JSON 이벤트를 확인할 수 있습니다. 중계와 기기 연결은 QA 실행 담당자가 준비합니다.

`request_started`와 `request_finished`는 실제 HTTP 시도마다 같은 `request_id`를 사용하며, 중계에는 `X-QA-Request-Id`로 전달합니다. 로그인 토큰 갱신과 재전송은 서로 다른 요청 ID를 사용합니다. 평가 흐름은 기존 `evaluation_timing_id`로 이어집니다. 로그에는 허용한 대상 이름·단계·시간·상태·난수 ID만 넣습니다.

`milestone/song_detail_drawn`, `milestone/practice_drawn`, 결과 화면의 `milestone/score_drawn`은 Compose의 해당 콘텐츠 그리기 이후 발생합니다. HTTP 응답과 화면 표시를 각각 확인할 수 있습니다. `eolssu.qaLive`는 기본적으로 꺼져 있고, 켜면 API 주소가 위 격리 루프백 주소여야 합니다. release에는 이 계측을 켜지 않습니다.


QA 입력 파일을 사용하는 시연에서는 실행 담당자가 앱의 `files/qa-live-input.pcm`을 녹음 시작 전에 준비합니다. 형식은 16kHz 모노 signed16 little-endian이며 최대 60초입니다. `.qa` 디버그 앱과 QA_LIVE 조건에서만 파일을 읽고, 기존 44.1kHz 녹음 파이프라인으로 리샘플링해 실제 시간에 맞춰 전달합니다. AAC 인코딩·레벨·음높이 추정·기존 입력 검사를 거칩니다. 파일이 열리면 `synthetic_input` 이벤트가 발생하며, 파일 변경·삭제는 다음 녹음부터 적용합니다. 파일이 없으면 마이크 입력을 사용합니다.
