# Boop 2.0 (Pure Kotlin StandBy Clock)

An ultra-lightweight, battery-efficient StandBy clock for Android inspired by iOS StandBy mode and the Nothing OS dot-matrix aesthetic.

## Features

- **Dot-Matrix Clock Face**: 5×7 circular dot-matrix layout rendered directly onto Android hardware-accelerated Canvas.
- **Nothing Typography**: Authentic Nothing font (`NothingFont5x7`) for date header, battery %, and AM/PM.
- **Custom Charging Bolt**: 27-circle dot-matrix lightning bolt extracted directly from custom vector assets.
- **Ultra-Small APK Size**: **1.5 MB** release APK (down from 131 MB).
- **Auto-Activation on Charging**:
  - Activates automatically when the device is plugged in to charge **AND** the phone is idle (screen off or locked) **AND** positioned horizontally (landscape stand/dock or flat).
  - Also integrates with Android's native **Screen Saver (DreamService)** so you can select Boop in `Settings → Display → Screen Saver`.
- **Distraction Free**: Automatically engages Android Do Not Disturb mode when active and restores previous filter on exit.
- **Always On**: Screen stays awake (`FLAG_KEEP_SCREEN_ON`) while running.
- **OLED Burn-in Protection**: Subtle pixel shifting every 60 seconds to protect AMOLED displays.
- **Low-Power Mode**: Dims display to minimal brightness with reduced tick intervals.
- **Interactive Settings**: Tap or long press anywhere on the screen to switch themes, toggle 12h/24h, DND, and auto-activation.

## Color Themes

1. **Nothing Classic (Theme 1)**: `#1A1D1C` background, `#FCFAFE` digits & meta, `#D71921` 2-dot colon & charging bolt.
2. **OLED Pure Black**: `#000000` deep black, `#FCFAFE` digits, `#D71921` colon.
3. **StandBy Red**: `#000000` background, pure `#D71921` red digits and colon.
4. **Monochrome**: `#000000` background, `#FCFAFE` digits, colon, and meta.
5. **Warm Amber**: `#000000` background, `#FFA726` warm amber digits, `#FF7043` colon.
6. **Matrix Green**: `#000000` background, `#4ADE80` digits, `#22C55E` accent.

## Building

```bash
cd android
./gradlew assembleRelease
```

The APK will be generated at `android/app/build/outputs/apk/release/app-release.apk`.
