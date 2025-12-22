# UI/UX Redesign Design Document

**Date:** 2025-12-22
**Status:** Approved

## Overview

Redesign the Android Auto Headunit app UI to be touch-friendly and suitable for permanent in-car installation. The new design draws inspiration from Headunit Reloaded while applying Material Dark theming with a Teal/Cyan accent.

## Requirements

- **Primary use:** Personal car installation on dedicated Android device
- **Orientation:** Both landscape and portrait support
- **Connection:** USB only (primary workflow)
- **Theme:** Material Dark with Teal/Cyan (#00BCD4) accent

## Screen Flow

```
APP LAUNCH
    │
    ▼
First Launch Check
    │           │
(first run)  (returning)
    │           │
    ▼           ▼
ONBOARDING   HOME DASHBOARD
(3 screens)     │
    │           │
    └─────┬─────┘
          ▼
    HOME DASHBOARD
    ┌─────┬─────┬─────┐
    │ USB │SETT │EXIT │
    └─────┴─────┴─────┘
          │           │
          ▼           ▼
    PROJECTION    SETTINGS
    ACTIVITY      CATEGORIES
                    → Detail screens
```

### Activities

| Activity | Purpose |
|----------|---------|
| `OnboardingActivity` | 3-step permission wizard (new) |
| `MainActivity` | Home dashboard with 3 tiles (refactored) |
| `SettingsActivity` | Settings category grid + detail fragments (new) |
| `AapProjectionActivity` | Android Auto display (unchanged) |

## Home Dashboard

Three large touch-friendly tiles in a responsive grid.

### Layout Behavior

**Landscape (3 columns):**
```
┌─────────┬─────────┬─────────┐
│   USB   │ SETTINGS│  EXIT   │
│ CONNECT │    ⚙    │    ⏻   │
└─────────┴─────────┴─────────┘
```

**Portrait (1 column):**
```
┌─────────────────┐
│      USB        │
│    CONNECT      │
├─────────────────┤
│    SETTINGS     │
├─────────────────┤
│      EXIT       │
└─────────────────┘
```

### Tile Specifications

- Background: `#1E1E1E` with 12dp rounded corners
- Icons: Teal (`#00BCD4`) vector drawables
- Labels: White (`#FFFFFF`), 16sp, medium weight
- Minimum touch target: 88dp × 88dp
- Ripple effect on touch
- 16dp spacing between tiles

### USB Connect Tile

- Shows connection status: "Ready" / "Connecting..." / "Connected"
- Icon animates when actively connecting

## Onboarding Flow

A 3-step permission wizard shown on first launch.

### Screen Structure

```
┌─────────────────────────────────────┐
│         [Title text]                │
│         centered, 24sp              │
│                                     │
│            ┌───────┐                │
│            │ ICON  │                │
│            │ 120dp │                │
│            └───────┘                │
│                                     │
│     [Explanation text]              │
│     centered, 14sp, gray            │
├─────────────────────────────────────┤
│  ● ○ ○          [NEXT →]            │
└─────────────────────────────────────┘
```

### Permission Screens

| Step | Icon | Title | Explanation |
|------|------|-------|-------------|
| 1 | Location pin | "Location Access Required" | "Location data is needed for navigation. No data is stored or shared." |
| 2 | Microphone | "Microphone Access Required" | "Microphone is used for Google Assistant. No audio is stored by this app." |
| 3 | Phone | "Phone Access Required" | "Needed to detect calls and switch screens automatically in self mode." |

### Behavior

- ViewPager2 for swipe navigation
- NEXT button requests permission then advances
- Progress dots update as user progresses
- Final step button says "FINISH" → navigates to Home
- Skipping sets flag so onboarding doesn't show again

## Settings Architecture

Two-level navigation: category grid → detail screens.

### Settings Category Grid (2×3)

```
┌──────────┬──────────┐
│ GRAPHICS │  AUDIO   │
├──────────┼──────────┤
│   GPS    │  INPUT   │
├──────────┼──────────┤
│  OTHER   │  ← BACK  │
└──────────┴──────────┘
```

### Category Mapping

| Category | Settings |
|----------|----------|
| **Graphics** | Resolution, Night mode, Day/night calculation method |
| **Audio** | Mic sample rate, Audio routing options |
| **GPS** | GPS for navigation toggle, Location settings |
| **Input** | Keymap configuration, Touch sensitivity |
| **Other** | Bluetooth address, Debug options, About |

### Detail Screen Layout

```
┌─────────────────────────────────────┐
│ ←  Graphics Settings                │  ← Teal header bar
├─────────────────────────────────────┤
│  Setting Title                 [⬤]  │  ← Toggle switch
│  Description text in gray           │
├─────────────────────────────────────┤
│  Another Setting               [▼]  │  ← Dropdown
│  Current value shown                │
└─────────────────────────────────────┘
```

Uses PreferenceFragmentCompat styled to match the dark theme.

## Theming & Styling

### Color Palette

| Name | Hex | Usage |
|------|-----|-------|
| Background | `#121212` | Screen background |
| Surface | `#1E1E1E` | Tiles, cards |
| Surface Elevated | `#2D2D2D` | Dialogs, menus |
| Primary (Teal) | `#00BCD4` | Buttons, accents |
| Primary Dark | `#0097A7` | Pressed states |
| On Primary | `#000000` | Text on teal |
| Text Primary | `#FFFFFF` | Main text |
| Text Secondary | `#B3B3B3` | Descriptions |
| Divider | `#3D3D3D` | Separators |
| Error | `#CF6679` | Error states |

### Typography

| Element | Size | Weight | Color |
|---------|------|--------|-------|
| Screen title | 20sp | Medium | White |
| Tile label | 16sp | Medium | White |
| Setting title | 16sp | Regular | White |
| Setting description | 14sp | Regular | Secondary |
| Button text | 14sp | Medium | On Primary |
| Status text | 12sp | Regular | Secondary |

### Drawable Assets

| Asset | Size | Purpose |
|-------|------|---------|
| `ic_usb.xml` | 24dp | USB connector icon |
| `ic_settings.xml` | 24dp | Gear icon |
| `ic_exit.xml` | 24dp | Power icon |
| `ic_location.xml` | 48dp | Onboarding location |
| `ic_mic.xml` | 48dp | Onboarding microphone |
| `ic_phone.xml` | 48dp | Onboarding phone |
| `ic_graphics.xml` | 24dp | Settings category |
| `ic_audio.xml` | 24dp | Settings category |
| `ic_gps.xml` | 24dp | Settings category |
| `ic_input.xml` | 24dp | Settings category |
| `ic_other.xml` | 24dp | Settings category |
| `ic_back.xml` | 24dp | Back arrow |
| `tile_background.xml` | - | Ripple + rounded rect |
| `button_primary.xml` | - | Teal button drawable |

All icons as vector drawables, tinted programmatically.

## File Structure

### New Files

```
app/src/main/
├── java/.../
│   ├── ui/
│   │   ├── onboarding/
│   │   │   ├── OnboardingActivity.kt
│   │   │   ├── OnboardingFragment.kt
│   │   │   └── OnboardingPagerAdapter.kt
│   │   ├── home/
│   │   │   └── HomeFragment.kt
│   │   ├── settings/
│   │   │   ├── SettingsActivity.kt
│   │   │   ├── SettingsCategoryFragment.kt
│   │   │   ├── GraphicsSettingsFragment.kt
│   │   │   ├── AudioSettingsFragment.kt
│   │   │   ├── GpsSettingsFragment.kt
│   │   │   ├── InputSettingsFragment.kt
│   │   │   └── OtherSettingsFragment.kt
│   │   └── common/
│   │       └── TileView.kt
│   └── util/
│       └── PreferenceManager.kt
│
├── res/
│   ├── layout/
│   │   ├── activity_onboarding.xml
│   │   ├── fragment_onboarding_page.xml
│   │   ├── fragment_home.xml
│   │   ├── activity_settings.xml
│   │   ├── fragment_settings_category.xml
│   │   └── item_tile.xml
│   ├── drawable/
│   │   ├── ic_*.xml (icons listed above)
│   │   ├── tile_background.xml
│   │   └── button_primary.xml
│   ├── values/
│   │   ├── colors.xml (theme colors)
│   │   ├── styles.xml (Material Dark theme)
│   │   └── dimens.xml (tile sizes, spacing)
│   └── xml/
│       ├── preferences_graphics.xml
│       ├── preferences_audio.xml
│       ├── preferences_gps.xml
│       ├── preferences_input.xml
│       └── preferences_other.xml
```

### Files to Modify

| File | Changes |
|------|---------|
| `MainActivity.kt` | Load HomeFragment instead of current UI |
| `activity_main.xml` | Simplify to fragment container |
| `AndroidManifest.xml` | Add OnboardingActivity, SettingsActivity |
| `styles.xml` | Apply dark theme as app default |

## Technical Constraints

- **minSdkVersion:** 18 (Android 4.3)
- **Kotlin:** 1.3.50
- No Jetpack Compose (requires minSdk 21+)
- Use native XML layouts with custom styles
- ViewPager2 for onboarding (backport available)
- PreferenceFragmentCompat for settings screens
