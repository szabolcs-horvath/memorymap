# Running the UI tests locally

The UI tests (`app/src/androidTest`) run on one dedicated emulator, `MemoryMap_UI_Tests`, which is set up once
and then kept. `scripts/ui-tests.sh` runs them on that emulator and on nothing else.

## Why a dedicated emulator

Google Play services ships without Google's latest map renderer. It downloads it the first time an app asks for it,
but only on a phone or emulator that is set up with Google Play: signed in to the Play Store, with the "set up your
device" prompt finished ([Google's answer](https://issuetracker.google.com/issues/327248427)). Without it the map
falls back to the legacy renderer, which doesn't support the app's markers (pin configs) or its map ID, and sometimes
draws nothing at all. Every real phone has the latest renderer.

A fresh emulator (Gradle managed devices, CI) is never set up like that, so the map tests fail there for reasons
that have nothing to do with the app. That's why the UI test workflow in CI runs only when started by hand.

`map/MapRendererTest` checks the renderer: if it fails, the emulator isn't set up (anymore) and the map tests can't
be trusted.

## One-time setup

1. **Install the system image.** In Android Studio: *Tools > SDK Manager > SDK Platforms*, tick *Show Package
   Details*, and under the targetSdk (API 37) install the **Google Play** x86_64 system image (not "Google APIs").
   The Device Manager also offers to download it in the next step.
2. **Create the emulator.** *Tools > Device Manager > + > Create Virtual Device*:
   - Phone: **Pixel 6**. Keep a 1080 x 2400 screen at 420 dpi: some tests tap positions measured on it.
   - System image: **API 37** with **Google Play** (the one with the Play Store icon).
   - Name: **`MemoryMap_UI_Tests`**, exactly. The script finds the emulator by this name.
   - Under the advanced settings: RAM **4 GB**, internal storage **8 GB**.

   Check the name with `%LOCALAPPDATA%\Android\Sdk\emulator\emulator.exe -list-avds`.

   Without Android Studio's Device Manager, the emulator is two files, which can be written by hand
   (replace `<you>` with your Windows user name):

   `%USERPROFILE%\.android\avd\MemoryMap_UI_Tests.ini`
   ```ini
   avd.ini.encoding=UTF-8
   path=C:\Users\<you>\.android\avd\MemoryMap_UI_Tests.avd
   path.rel=avd\MemoryMap_UI_Tests.avd
   target=android-37.0
   ```
   `%USERPROFILE%\.android\avd\MemoryMap_UI_Tests.avd\config.ini` (the important lines; the emulator fills in the rest)
   ```ini
   AvdId=MemoryMap_UI_Tests
   PlayStore.enabled=true
   abi.type=x86_64
   avd.ini.displayname=Memory Map UI Tests (API 37)
   disk.dataPartition.size=8G
   hw.cpu.arch=x86_64
   hw.device.manufacturer=Google
   hw.device.name=pixel_6
   hw.gpu.enabled=yes
   hw.gpu.mode=auto
   hw.keyboard=yes
   hw.lcd.density=420
   hw.lcd.height=2400
   hw.lcd.width=1080
   hw.ramSize=4096
   image.sysdir.1=system-images\android-37.0\google_apis_playstore\x86_64\
   tag.id=google_apis_playstore
   tag.ids=google_apis_playstore
   target=android-37.0
   ```
3. **Set up Google Play on it.** Start the emulator (from the Device Manager, or
   `emulator.exe -avd MemoryMap_UI_Tests`), then:
   - open the **Play Store** and sign in with a Google account (a separate test account is fine: the tests don't
     use it);
   - if a notification asks to **finish setting up the device** or mentions **Play Protect**, tap it and go through
     it.
4. **Let it download the renderer.** Leave the emulator running for a few minutes (Google says it can take longer).
   Then check:
   ```
   scripts/ui-tests.sh MapRendererTest
   ```
   It passes once the latest renderer is there.

The emulator keeps the sign-in and the renderer from then on. Close it with its window's close button so it saves
its state.

## Running the tests

From the project folder, in bash (macOS and Linux terminals; on Windows **Git Bash**, which comes with Git for
Windows; Android Studio's terminal can use it: *Settings > Tools > Terminal > Shell path*
`C:\Program Files\Git\bin\bash.exe`):

```
scripts/ui-tests.sh                           the whole suite
scripts/ui-tests.sh "MapTest|TimelineTest"    only the test classes whose names match
```

On Windows, a plain `bash` typed in PowerShell or cmd may start WSL's bash instead of Git Bash; that one doesn't see
the Windows SDK, so use Git Bash.

The script:
- finds the running `MemoryMap_UI_Tests` emulator by name, or starts it and waits until it has booted;
- runs `connectedDebugAndroidTest` with `ANDROID_SERIAL` set to it, so **no other phone or emulator is touched**
  (AGP's own `--serial` option fails in AGP 9.4.1 with an `UnsupportedOperationException`);
- prints a table of the results (failed tests, tests without a result, skipped tests).

`UI_TEST_AVD=<name> scripts/ui-tests.sh` runs on another emulator instead (it must be set up the same way).

The HTML report is in `app/build/reports/androidTests/connected/debug/`, and every test's logcat in
`app/build/outputs/androidTest-results/connected/`. When a test fails, its screenshot and element tree
(`<Class>.<test>.png` and `.xml`) are in `app/build/outputs/connected_android_test_additional_output/`.

⚠️ Every test clears the debug app's data first (Test Orchestrator), and some add and remove photos and a video in the
gallery. Keep your own memories on another emulator or phone: never point `ANDROID_SERIAL` at those.

## Troubleshooting

- **`MapRendererTest` fails, or markers don't show:** the emulator has only the legacy renderer. Check that you are
  still signed in to the Play Store, finish any setup notification, wait a few minutes and run the check again.
  A *Wipe Data* in the Device Manager undoes the setup: do step 3 again.
- **"There is no emulator named MemoryMap_UI_Tests":** the emulator is missing or has another name; see step 2.
- **The emulator window closes at once:** start it from a terminal (`emulator.exe -avd MemoryMap_UI_Tests`) to see
  why, for example a wrong `image.sysdir.1` or a missing system image.
- **"No Java found":** set `JAVA_HOME`, for example to Android Studio's `jbr` folder.
