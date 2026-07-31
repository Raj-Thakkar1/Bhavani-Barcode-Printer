# Bhavani Barcode Printer — Android

Android port of `bhavani_barcode_printer.py`, built for a **TSC TE244 over USB-OTG**.
Same TSPL label output, same purple/navy theme, same form layout.

## What you need on your side
- A USB-OTG adapter/cable (USB-C or Micro-USB → USB-A female) to plug the TE244 into your phone
- **Android Studio** (easiest path), or just a JDK 17 + Android SDK if you prefer the command line
- Your phone connected via USB debugging, or just install the built APK directly

## Easiest no-install option: GitHub Actions cloud build
This project includes `.github/workflows/build-apk.yml`, which builds the APK on GitHub's own
servers (Android SDK preinstalled) — no local install needed at all.

1. Create a new repo on GitHub (private is fine) and push this folder to it:
   ```bash
   cd BhavaniBarcodePrinter
   git init
   git add .
   git commit -m "Initial commit"
   git branch -M main
   git remote add origin https://github.com/<your-username>/bhavani-barcode-printer-android.git
   git push -u origin main
   ```
2. Go to the repo on GitHub → **Actions** tab. The "Build Debug APK" workflow runs automatically
   on push (takes a few minutes).
3. Once it's green, click into the run → **Artifacts** section at the bottom →
   download `BhavaniBarcodePrinter-debug-apk` → unzip it → that's your `app-debug.apk`.
4. Copy it to your phone and install (allow "install from this source" once, since it's unsigned/debug).

You can also re-trigger a build anytime without a new push via the **Run workflow** button on the
Actions tab (the `workflow_dispatch` trigger in the yml enables this).

## Fastest way to get the APK: Android Studio
1. Open Android Studio → **Open** → select the `BhavaniBarcodePrinter` folder.
2. Let it sync (first sync downloads Gradle + the Android SDK bits it needs — takes a few minutes).
3. **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
4. APK lands in `app/build/outputs/apk/debug/app-debug.apk`. Copy it to your phone and install
   (you'll need to allow "install from this source" once).

## Or from the terminal (if you have Android SDK set up already)
```bash
cd BhavaniBarcodePrinter
./gradlew assembleDebug
```
Output: `app/build/outputs/apk/debug/app-debug.apk`

If Android Studio isn't installed and you want the SDK via command line only, the quickest route
is to install Android Studio once just to get the SDK components (cmdline-tools + platform-tools +
platform 34 + build-tools), then you can drive everything from `./gradlew` afterwards without
opening the IDE again.

## First run on your phone
1. Plug the TE244 into your phone via the OTG adapter (USB cable to printer, power to printer's own AC adapter as usual).
2. Open the app — it scans for USB devices automatically.
3. If the printer shows up in the "Output Device" dropdown, tap **Print Labels** — Android will
   show a one-time **"Allow app to access USB device?"** dialog. Tap Allow.
4. Label prints. That dialog won't reappear for this printer on this app afterward.

## If the printer doesn't show up in the dropdown
- Confirm your phone actually supports USB Host mode (most do) — try a USB flash drive via the
  same OTG adapter as a quick sanity check.
- Try a different OTG adapter/cable — some cheap ones are power-only, no data.
- Tap the refresh icon next to the device dropdown after plugging in.

## Notes on what changed vs. the desktop version
- No CUPS/serial fallback — the TE244 is USB-only, so the Android version only needs the direct
  USB path (`UsbPrinter.kt`, equivalent to `send_via_lp()` on Linux).
- Barcode preview uses ZXing to draw a Code128 bitmap on screen. The actual print job still sends
  raw TSPL and lets the printer render its own barcode — identical output to the desktop app.
- `TsplBuilder.kt` is a direct line-for-line port of `PRN_TEMPLATE` / `build_prn()` — same
  coordinates, same font sizes, same commands.
