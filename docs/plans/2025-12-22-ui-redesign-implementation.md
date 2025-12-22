# UI/UX Redesign Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Transform the Android Auto Headunit app from a basic button list to a modern, touch-friendly automotive UI with Material Dark theme, onboarding flow, and categorized settings.

**Architecture:** Native XML layouts with custom styles and drawables. Three main screens: Onboarding (ViewPager2), Home Dashboard (GridLayout), Settings (category grid + PreferenceFragments). Single Activity pattern with Fragments for navigation.

**Tech Stack:** Kotlin 1.3.50, Android SDK 28, ViewPager2, PreferenceFragmentCompat, Vector Drawables

---

## Phase 1: Theme Foundation

### Task 1: Define Color Palette

**Files:**
- Modify: `app/src/main/res/values/colors.xml`

**Step 1: Add Material Dark theme colors**

Add these colors to `colors.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- Existing colors (keep) -->
    <color name="colorPrimary">#3F51B5</color>
    <color name="colorPrimaryDark">#303F9F</color>
    <color name="colorAccent">#FF4081</color>

    <!-- Material Dark Theme Colors -->
    <color name="background">#121212</color>
    <color name="surface">#1E1E1E</color>
    <color name="surfaceElevated">#2D2D2D</color>
    <color name="primary">#00BCD4</color>
    <color name="primaryDark">#0097A7</color>
    <color name="primaryLight">#4DD0E1</color>
    <color name="onPrimary">#000000</color>
    <color name="textPrimary">#FFFFFF</color>
    <color name="textSecondary">#B3B3B3</color>
    <color name="divider">#3D3D3D</color>
    <color name="error">#CF6679</color>
    <color name="ripple">#33FFFFFF</color>
</resources>
```

**Step 2: Commit**

```bash
git add app/src/main/res/values/colors.xml
git commit -m "feat: add Material Dark theme color palette"
```

---

### Task 2: Define Dimensions

**Files:**
- Create: `app/src/main/res/values/dimens.xml`

**Step 1: Create dimensions file**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- Tile dimensions -->
    <dimen name="tile_min_size">88dp</dimen>
    <dimen name="tile_corner_radius">12dp</dimen>
    <dimen name="tile_spacing">16dp</dimen>
    <dimen name="tile_padding">16dp</dimen>
    <dimen name="tile_icon_size">48dp</dimen>
    <dimen name="tile_icon_size_large">64dp</dimen>

    <!-- Onboarding dimensions -->
    <dimen name="onboarding_icon_size">120dp</dimen>
    <dimen name="onboarding_button_height">56dp</dimen>
    <dimen name="onboarding_dot_size">8dp</dimen>
    <dimen name="onboarding_dot_spacing">8dp</dimen>

    <!-- Typography -->
    <dimen name="text_title">20sp</dimen>
    <dimen name="text_tile_label">16sp</dimen>
    <dimen name="text_body">14sp</dimen>
    <dimen name="text_caption">12sp</dimen>

    <!-- Settings -->
    <dimen name="settings_header_height">56dp</dimen>
    <dimen name="settings_item_padding">16dp</dimen>

    <!-- General spacing -->
    <dimen name="spacing_small">8dp</dimen>
    <dimen name="spacing_medium">16dp</dimen>
    <dimen name="spacing_large">24dp</dimen>
</resources>
```

**Step 2: Commit**

```bash
git add app/src/main/res/values/dimens.xml
git commit -m "feat: add dimension resources for UI components"
```

---

### Task 3: Update App Theme

**Files:**
- Modify: `app/src/main/res/values/styles.xml`

**Step 1: Read current styles.xml**

Read the file first to understand existing styles.

**Step 2: Update styles with Material Dark theme**

Replace/update `styles.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- Base application theme -->
    <style name="AppTheme" parent="Theme.AppCompat.NoActionBar">
        <item name="colorPrimary">@color/primary</item>
        <item name="colorPrimaryDark">@color/primaryDark</item>
        <item name="colorAccent">@color/primary</item>
        <item name="android:windowBackground">@color/background</item>
        <item name="android:textColorPrimary">@color/textPrimary</item>
        <item name="android:textColorSecondary">@color/textSecondary</item>
        <item name="android:statusBarColor">@color/background</item>
        <item name="android:navigationBarColor">@color/background</item>
    </style>

    <!-- Tile style -->
    <style name="TileStyle">
        <item name="android:background">@drawable/tile_background</item>
        <item name="android:padding">@dimen/tile_padding</item>
        <item name="android:gravity">center</item>
        <item name="android:clickable">true</item>
        <item name="android:focusable">true</item>
    </style>

    <!-- Tile label text -->
    <style name="TileLabelStyle">
        <item name="android:textColor">@color/textPrimary</item>
        <item name="android:textSize">@dimen/text_tile_label</item>
        <item name="android:fontFamily">sans-serif-medium</item>
        <item name="android:gravity">center</item>
    </style>

    <!-- Primary button (teal) -->
    <style name="PrimaryButtonStyle">
        <item name="android:background">@drawable/button_primary</item>
        <item name="android:textColor">@color/onPrimary</item>
        <item name="android:textSize">@dimen/text_body</item>
        <item name="android:fontFamily">sans-serif-medium</item>
        <item name="android:textAllCaps">true</item>
        <item name="android:paddingLeft">@dimen/spacing_large</item>
        <item name="android:paddingRight">@dimen/spacing_large</item>
        <item name="android:minHeight">@dimen/onboarding_button_height</item>
    </style>

    <!-- Settings header -->
    <style name="SettingsHeaderStyle">
        <item name="android:background">@color/primary</item>
        <item name="android:textColor">@color/onPrimary</item>
        <item name="android:textSize">@dimen/text_title</item>
        <item name="android:fontFamily">sans-serif-medium</item>
        <item name="android:gravity">center_vertical</item>
        <item name="android:paddingLeft">@dimen/spacing_medium</item>
        <item name="android:minHeight">@dimen/settings_header_height</item>
    </style>

    <!-- Onboarding title -->
    <style name="OnboardingTitleStyle">
        <item name="android:textColor">@color/textPrimary</item>
        <item name="android:textSize">24sp</item>
        <item name="android:fontFamily">sans-serif-medium</item>
        <item name="android:gravity">center</item>
    </style>

    <!-- Onboarding description -->
    <style name="OnboardingDescriptionStyle">
        <item name="android:textColor">@color/textSecondary</item>
        <item name="android:textSize">@dimen/text_body</item>
        <item name="android:gravity">center</item>
        <item name="android:lineSpacingMultiplier">1.3</item>
    </style>

    <!-- Settings preference theme -->
    <style name="PreferenceTheme" parent="PreferenceThemeOverlay">
        <item name="android:textColorPrimary">@color/textPrimary</item>
        <item name="android:textColorSecondary">@color/textSecondary</item>
    </style>
</resources>
```

**Step 3: Commit**

```bash
git add app/src/main/res/values/styles.xml
git commit -m "feat: add Material Dark theme styles"
```

---

### Task 4: Create Tile Background Drawable

**Files:**
- Create: `app/src/main/res/drawable/tile_background.xml`

**Step 1: Create ripple drawable with rounded corners**

```xml
<?xml version="1.0" encoding="utf-8"?>
<ripple xmlns:android="http://schemas.android.com/apk/res/android"
    android:color="@color/ripple">
    <item android:id="@android:id/mask">
        <shape android:shape="rectangle">
            <corners android:radius="@dimen/tile_corner_radius" />
            <solid android:color="@android:color/white" />
        </shape>
    </item>
    <item>
        <shape android:shape="rectangle">
            <corners android:radius="@dimen/tile_corner_radius" />
            <solid android:color="@color/surface" />
        </shape>
    </item>
</ripple>
```

**Step 2: Commit**

```bash
git add app/src/main/res/drawable/tile_background.xml
git commit -m "feat: add tile background drawable with ripple"
```

---

### Task 5: Create Primary Button Drawable

**Files:**
- Create: `app/src/main/res/drawable/button_primary.xml`

**Step 1: Create teal button with ripple**

```xml
<?xml version="1.0" encoding="utf-8"?>
<ripple xmlns:android="http://schemas.android.com/apk/res/android"
    android:color="@color/primaryDark">
    <item>
        <shape android:shape="rectangle">
            <corners android:radius="4dp" />
            <solid android:color="@color/primary" />
        </shape>
    </item>
</ripple>
```

**Step 2: Commit**

```bash
git add app/src/main/res/drawable/button_primary.xml
git commit -m "feat: add primary button drawable"
```

---

## Phase 2: Vector Icon Assets

### Task 6: Create Home Screen Icons

**Files:**
- Create: `app/src/main/res/drawable/ic_usb.xml`
- Create: `app/src/main/res/drawable/ic_settings.xml`
- Create: `app/src/main/res/drawable/ic_exit.xml`

**Step 1: Create USB icon**

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="@color/primary">
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M15,7V11H16V13H13V17.17C13.95,17.58 14.61,18.52 14.61,19.61C14.61,21.05 13.44,22.22 12,22.22C10.56,22.22 9.39,21.05 9.39,19.61C9.39,18.52 10.05,17.58 11,17.17V13H8V11H9V7H7V2H11V7H9V11H11V13H13V11H15V7H13V2H17V7H15Z"/>
</vector>
```

**Step 2: Create Settings icon**

Create `ic_settings.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="@color/primary">
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M19.14,12.94C19.18,12.64 19.2,12.33 19.2,12C19.2,11.68 19.18,11.36 19.13,11.06L21.16,9.48C21.34,9.34 21.39,9.07 21.28,8.87L19.36,5.55C19.24,5.33 18.99,5.26 18.77,5.33L16.38,6.29C15.88,5.91 15.35,5.59 14.76,5.35L14.4,2.81C14.36,2.57 14.16,2.4 13.92,2.4H10.08C9.84,2.4 9.65,2.57 9.61,2.81L9.25,5.35C8.66,5.59 8.12,5.92 7.63,6.29L5.24,5.33C5.02,5.25 4.77,5.33 4.65,5.55L2.74,8.87C2.62,9.08 2.66,9.34 2.86,9.48L4.89,11.06C4.84,11.36 4.8,11.69 4.8,12C4.8,12.31 4.82,12.64 4.87,12.94L2.84,14.52C2.66,14.66 2.61,14.93 2.72,15.13L4.64,18.45C4.76,18.67 5.01,18.74 5.23,18.67L7.62,17.71C8.12,18.09 8.65,18.41 9.24,18.65L9.6,21.19C9.65,21.43 9.84,21.6 10.08,21.6H13.92C14.16,21.6 14.36,21.43 14.39,21.19L14.75,18.65C15.34,18.41 15.88,18.09 16.37,17.71L18.76,18.67C18.98,18.75 19.23,18.67 19.35,18.45L21.27,15.13C21.39,14.91 21.34,14.66 21.15,14.52L19.14,12.94ZM12,15.6C10.02,15.6 8.4,13.98 8.4,12C8.4,10.02 10.02,8.4 12,8.4C13.98,8.4 15.6,10.02 15.6,12C15.6,13.98 13.98,15.6 12,15.6Z"/>
</vector>
```

**Step 3: Create Exit/Power icon**

Create `ic_exit.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="@color/primary">
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M13,3H11V13H13V3ZM17.83,5.17L16.41,6.59C18.05,7.91 19,9.9 19,12C19,15.87 15.87,19 12,19C8.13,19 5,15.87 5,12C5,9.9 5.95,7.91 7.59,6.59L6.17,5.17C4.23,6.82 3,9.26 3,12C3,16.97 7.03,21 12,21C16.97,21 21,16.97 21,12C21,9.26 19.77,6.82 17.83,5.17Z"/>
</vector>
```

**Step 4: Commit**

```bash
git add app/src/main/res/drawable/ic_usb.xml app/src/main/res/drawable/ic_settings.xml app/src/main/res/drawable/ic_exit.xml
git commit -m "feat: add home screen icons (USB, Settings, Exit)"
```

---

### Task 7: Create Onboarding Icons

**Files:**
- Create: `app/src/main/res/drawable/ic_location_large.xml`
- Create: `app/src/main/res/drawable/ic_mic_large.xml`
- Create: `app/src/main/res/drawable/ic_phone_large.xml`

**Step 1: Create Location icon (large)**

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="120dp"
    android:height="120dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="@color/textSecondary">
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M12,2C8.13,2 5,5.13 5,9C5,14.25 12,22 12,22C12,22 19,14.25 19,9C19,5.13 15.87,2 12,2ZM12,11.5C10.62,11.5 9.5,10.38 9.5,9C9.5,7.62 10.62,6.5 12,6.5C13.38,6.5 14.5,7.62 14.5,9C14.5,10.38 13.38,11.5 12,11.5Z"/>
</vector>
```

**Step 2: Create Microphone icon (large)**

Create `ic_mic_large.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="120dp"
    android:height="120dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="@color/textSecondary">
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M12,14C13.66,14 14.99,12.66 14.99,11L15,5C15,3.34 13.66,2 12,2C10.34,2 9,3.34 9,5V11C9,12.66 10.34,14 12,14ZM17.3,11C17.3,14 14.76,16.1 12,16.1C9.24,16.1 6.7,14 6.7,11H5C5,14.41 7.72,17.23 11,17.72V21H13V17.72C16.28,17.23 19,14.41 19,11H17.3Z"/>
</vector>
```

**Step 3: Create Phone icon (large)**

Create `ic_phone_large.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="120dp"
    android:height="120dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="@color/textSecondary">
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M6.62,10.79C8.06,13.62 10.38,15.94 13.21,17.38L15.41,15.18C15.69,14.9 16.08,14.82 16.43,14.93C17.55,15.3 18.75,15.5 20,15.5C20.55,15.5 21,15.95 21,16.5V20C21,20.55 20.55,21 20,21C10.61,21 3,13.39 3,4C3,3.45 3.45,3 4,3H7.5C8.05,3 8.5,3.45 8.5,4C8.5,5.25 8.7,6.45 9.07,7.57C9.18,7.92 9.1,8.31 8.82,8.59L6.62,10.79Z"/>
</vector>
```

**Step 4: Commit**

```bash
git add app/src/main/res/drawable/ic_location_large.xml app/src/main/res/drawable/ic_mic_large.xml app/src/main/res/drawable/ic_phone_large.xml
git commit -m "feat: add onboarding icons (Location, Mic, Phone)"
```

---

### Task 8: Create Settings Category Icons

**Files:**
- Create: `app/src/main/res/drawable/ic_graphics.xml`
- Create: `app/src/main/res/drawable/ic_audio.xml`
- Create: `app/src/main/res/drawable/ic_gps.xml`
- Create: `app/src/main/res/drawable/ic_input.xml`
- Create: `app/src/main/res/drawable/ic_other.xml`
- Create: `app/src/main/res/drawable/ic_back.xml`

**Step 1: Create Graphics icon (eye)**

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="@color/primary">
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M12,4.5C7,4.5 2.73,7.61 1,12C2.73,16.39 7,19.5 12,19.5C17,19.5 21.27,16.39 23,12C21.27,7.61 17,4.5 12,4.5ZM12,17C9.24,17 7,14.76 7,12C7,9.24 9.24,7 12,7C14.76,7 17,9.24 17,12C17,14.76 14.76,17 12,17ZM12,9C10.34,9 9,10.34 9,12C9,13.66 10.34,15 12,15C13.66,15 15,13.66 15,12C15,10.34 13.66,9 12,9Z"/>
</vector>
```

**Step 2: Create Audio icon (speaker)**

Create `ic_audio.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="@color/primary">
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M3,9V15H7L12,20V4L7,9H3ZM16.5,12C16.5,10.23 15.48,8.71 14,7.97V16.02C15.48,15.29 16.5,13.77 16.5,12ZM14,3.23V5.29C16.89,6.15 19,8.83 19,12C19,15.17 16.89,17.85 14,18.71V20.77C18.01,19.86 21,16.28 21,12C21,7.72 18.01,4.14 14,3.23Z"/>
</vector>
```

**Step 3: Create GPS icon (location)**

Create `ic_gps.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="@color/primary">
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M12,8C9.79,8 8,9.79 8,12C8,14.21 9.79,16 12,16C14.21,16 16,14.21 16,12C16,9.79 14.21,8 12,8ZM20.94,11C20.48,6.83 17.17,3.52 13,3.06V1H11V3.06C6.83,3.52 3.52,6.83 3.06,11H1V13H3.06C3.52,17.17 6.83,20.48 11,20.94V23H13V20.94C17.17,20.48 20.48,17.17 20.94,13H23V11H20.94ZM12,19C8.13,19 5,15.87 5,12C5,8.13 8.13,5 12,5C15.87,5 19,8.13 19,12C19,15.87 15.87,19 12,19Z"/>
</vector>
```

**Step 4: Create Input icon (touch)**

Create `ic_input.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="@color/primary">
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M9,11.24V7.5C9,6.12 10.12,5 11.5,5C12.88,5 14,6.12 14,7.5V10.24C14.64,10.71 15,11.47 15,12.31V16.5C15,18.43 13.43,20 11.5,20C9.57,20 8,18.43 8,16.5V12.31C8,11.47 8.36,10.71 9,10.24V11.24ZM11.5,3C9.02,3 7,5.02 7,7.5V9.68C5.79,10.55 5,11.92 5,13.5V17.5C5,20.54 7.46,23 10.5,23H12.5C15.54,23 18,20.54 18,17.5V13.5C18,11.92 17.21,10.55 16,9.68V7.5C16,5.02 13.98,3 11.5,3Z"/>
</vector>
```

**Step 5: Create Other icon (menu)**

Create `ic_other.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="@color/primary">
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M3,18H16V16H3V18ZM3,13H13V11H3V13ZM3,6V8H16V6H3ZM21,15.59L17.42,12L21,8.41L19.59,7L14.59,12L19.59,17L21,15.59Z"/>
</vector>
```

**Step 6: Create Back arrow icon**

Create `ic_back.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="@color/textPrimary">
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M20,11H7.83L13.42,5.41L12,4L4,12L12,20L13.41,18.59L7.83,13H20V11Z"/>
</vector>
```

**Step 7: Commit**

```bash
git add app/src/main/res/drawable/ic_graphics.xml app/src/main/res/drawable/ic_audio.xml app/src/main/res/drawable/ic_gps.xml app/src/main/res/drawable/ic_input.xml app/src/main/res/drawable/ic_other.xml app/src/main/res/drawable/ic_back.xml
git commit -m "feat: add settings category icons and back arrow"
```

---

## Phase 3: Onboarding Flow

### Task 9: Create Onboarding Layout

**Files:**
- Create: `app/src/main/res/layout/activity_onboarding.xml`

**Step 1: Create the onboarding activity layout**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:background="@color/background">

    <androidx.viewpager2.widget.ViewPager2
        android:id="@+id/viewPager"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1" />

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="center_vertical"
        android:background="@color/primary"
        android:paddingLeft="@dimen/spacing_medium"
        android:paddingRight="@dimen/spacing_medium"
        android:minHeight="@dimen/onboarding_button_height">

        <LinearLayout
            android:id="@+id/dotsContainer"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:orientation="horizontal"
            android:gravity="center_vertical" />

        <Button
            android:id="@+id/nextButton"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/next"
            android:textColor="@color/onPrimary"
            android:background="?attr/selectableItemBackground"
            android:textSize="@dimen/text_body"
            android:fontFamily="sans-serif-medium"
            android:drawableRight="@drawable/ic_chevron_right"
            android:drawablePadding="@dimen/spacing_small" />

    </LinearLayout>

</LinearLayout>
```

**Step 2: Create chevron right icon for button**

Create `app/src/main/res/drawable/ic_chevron_right.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="@color/onPrimary">
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M10,6L8.59,7.41L13.17,12L8.59,16.59L10,18L16,12Z"/>
</vector>
```

**Step 3: Commit**

```bash
git add app/src/main/res/layout/activity_onboarding.xml app/src/main/res/drawable/ic_chevron_right.xml
git commit -m "feat: add onboarding activity layout"
```

---

### Task 10: Create Onboarding Page Fragment Layout

**Files:**
- Create: `app/src/main/res/layout/fragment_onboarding_page.xml`

**Step 1: Create the page fragment layout**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:gravity="center"
    android:padding="@dimen/spacing_large"
    android:background="@color/background">

    <TextView
        android:id="@+id/titleText"
        style="@style/OnboardingTitleStyle"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginBottom="@dimen/spacing_large" />

    <ImageView
        android:id="@+id/iconImage"
        android:layout_width="@dimen/onboarding_icon_size"
        android:layout_height="@dimen/onboarding_icon_size"
        android:layout_marginTop="@dimen/spacing_large"
        android:layout_marginBottom="@dimen/spacing_large" />

    <TextView
        android:id="@+id/descriptionText"
        style="@style/OnboardingDescriptionStyle"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="@dimen/spacing_large"
        android:paddingLeft="@dimen/spacing_large"
        android:paddingRight="@dimen/spacing_large" />

</LinearLayout>
```

**Step 2: Commit**

```bash
git add app/src/main/res/layout/fragment_onboarding_page.xml
git commit -m "feat: add onboarding page fragment layout"
```

---

### Task 11: Add String Resources for Onboarding

**Files:**
- Modify: `app/src/main/res/values/strings.xml`

**Step 1: Read current strings.xml**

Read the file first.

**Step 2: Add onboarding strings**

Add these strings:

```xml
<!-- Onboarding -->
<string name="next">NEXT</string>
<string name="finish">FINISH</string>
<string name="onboarding_location_title">Location Access Required</string>
<string name="onboarding_location_desc">Location data is needed for navigation. No data is stored or shared.</string>
<string name="onboarding_mic_title">Microphone Access Required</string>
<string name="onboarding_mic_desc">Microphone is used for Google Assistant. No audio is stored by this app.</string>
<string name="onboarding_phone_title">Phone Access Required</string>
<string name="onboarding_phone_desc">Needed to detect calls and switch screens automatically in self mode.</string>

<!-- Home -->
<string name="usb_connect">USB</string>
<string name="usb_status_ready">Ready</string>
<string name="usb_status_connecting">Connecting…</string>
<string name="usb_status_connected">Connected</string>
<string name="settings">Settings</string>
<string name="exit">Exit</string>

<!-- Settings Categories -->
<string name="settings_graphics">Graphics</string>
<string name="settings_audio">Audio</string>
<string name="settings_gps">GPS</string>
<string name="settings_input">Input</string>
<string name="settings_other">Other</string>
<string name="back">Back</string>
```

**Step 3: Commit**

```bash
git add app/src/main/res/values/strings.xml
git commit -m "feat: add string resources for onboarding and home"
```

---

### Task 12: Create OnboardingActivity

**Files:**
- Create: `app/src/main/java/ca/yyx/hu/ui/onboarding/OnboardingActivity.kt`

**Step 1: Create the onboarding directory and activity**

```kotlin
package ca.yyx.hu.ui.onboarding

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.viewpager2.widget.ViewPager2
import ca.yyx.hu.R
import ca.yyx.hu.main.MainActivity

class OnboardingActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var dotsContainer: LinearLayout
    private lateinit var nextButton: Button
    private lateinit var dots: Array<ImageView>

    private val pages = listOf(
        OnboardingPage(
            R.string.onboarding_location_title,
            R.string.onboarding_location_desc,
            R.drawable.ic_location_large,
            Manifest.permission.ACCESS_FINE_LOCATION
        ),
        OnboardingPage(
            R.string.onboarding_mic_title,
            R.string.onboarding_mic_desc,
            R.drawable.ic_mic_large,
            Manifest.permission.RECORD_AUDIO
        ),
        OnboardingPage(
            R.string.onboarding_phone_title,
            R.string.onboarding_phone_desc,
            R.drawable.ic_phone_large,
            Manifest.permission.READ_PHONE_STATE
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        viewPager = findViewById(R.id.viewPager)
        dotsContainer = findViewById(R.id.dotsContainer)
        nextButton = findViewById(R.id.nextButton)

        setupViewPager()
        setupDots()
        setupNextButton()
    }

    private fun setupViewPager() {
        viewPager.adapter = OnboardingPagerAdapter(this, pages)
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateDots(position)
                updateNextButton(position)
            }
        })
    }

    private fun setupDots() {
        dots = Array(pages.size) { ImageView(this) }
        val params = LinearLayout.LayoutParams(
            resources.getDimensionPixelSize(R.dimen.onboarding_dot_size),
            resources.getDimensionPixelSize(R.dimen.onboarding_dot_size)
        )
        params.setMargins(
            resources.getDimensionPixelSize(R.dimen.onboarding_dot_spacing),
            0,
            resources.getDimensionPixelSize(R.dimen.onboarding_dot_spacing),
            0
        )

        dots.forEachIndexed { index, dot ->
            dot.setImageResource(R.drawable.dot_indicator)
            dot.layoutParams = params
            dot.alpha = if (index == 0) 1f else 0.5f
            dotsContainer.addView(dot)
        }
    }

    private fun updateDots(position: Int) {
        dots.forEachIndexed { index, dot ->
            dot.alpha = if (index == position) 1f else 0.5f
        }
    }

    private fun updateNextButton(position: Int) {
        if (position == pages.size - 1) {
            nextButton.text = getString(R.string.finish)
        } else {
            nextButton.text = getString(R.string.next)
        }
    }

    private fun setupNextButton() {
        nextButton.setOnClickListener {
            val currentPosition = viewPager.currentItem
            val page = pages[currentPosition]

            // Request permission
            if (ContextCompat.checkSelfPermission(this, page.permission)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(page.permission),
                    PERMISSION_REQUEST_CODE
                )
            } else {
                advanceOrFinish()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            // Advance regardless of grant result
            advanceOrFinish()
        }
    }

    private fun advanceOrFinish() {
        val currentPosition = viewPager.currentItem
        if (currentPosition < pages.size - 1) {
            viewPager.currentItem = currentPosition + 1
        } else {
            finishOnboarding()
        }
    }

    private fun finishOnboarding() {
        // Save that onboarding is complete
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ONBOARDING_COMPLETE, true)
            .apply()

        // Navigate to main
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    companion object {
        const val PREFS_NAME = "headunit_prefs"
        const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
        private const val PERMISSION_REQUEST_CODE = 100

        fun isOnboardingComplete(activity: AppCompatActivity): Boolean {
            return activity.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getBoolean(KEY_ONBOARDING_COMPLETE, false)
        }
    }
}

data class OnboardingPage(
    val titleRes: Int,
    val descriptionRes: Int,
    val iconRes: Int,
    val permission: String
)
```

**Step 2: Commit**

```bash
git add app/src/main/java/ca/yyx/hu/ui/onboarding/OnboardingActivity.kt
git commit -m "feat: add OnboardingActivity with permission handling"
```

---

### Task 13: Create OnboardingPagerAdapter

**Files:**
- Create: `app/src/main/java/ca/yyx/hu/ui/onboarding/OnboardingPagerAdapter.kt`

**Step 1: Create the pager adapter**

```kotlin
package ca.yyx.hu.ui.onboarding

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class OnboardingPagerAdapter(
    activity: AppCompatActivity,
    private val pages: List<OnboardingPage>
) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = pages.size

    override fun createFragment(position: Int): Fragment {
        val page = pages[position]
        return OnboardingPageFragment.newInstance(
            page.titleRes,
            page.descriptionRes,
            page.iconRes
        )
    }
}
```

**Step 2: Commit**

```bash
git add app/src/main/java/ca/yyx/hu/ui/onboarding/OnboardingPagerAdapter.kt
git commit -m "feat: add OnboardingPagerAdapter"
```

---

### Task 14: Create OnboardingPageFragment

**Files:**
- Create: `app/src/main/java/ca/yyx/hu/ui/onboarding/OnboardingPageFragment.kt`

**Step 1: Create the page fragment**

```kotlin
package ca.yyx.hu.ui.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import ca.yyx.hu.R

class OnboardingPageFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_onboarding_page, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val titleRes = arguments?.getInt(ARG_TITLE_RES) ?: return
        val descriptionRes = arguments?.getInt(ARG_DESCRIPTION_RES) ?: return
        val iconRes = arguments?.getInt(ARG_ICON_RES) ?: return

        view.findViewById<TextView>(R.id.titleText).setText(titleRes)
        view.findViewById<TextView>(R.id.descriptionText).setText(descriptionRes)
        view.findViewById<ImageView>(R.id.iconImage).setImageResource(iconRes)
    }

    companion object {
        private const val ARG_TITLE_RES = "title_res"
        private const val ARG_DESCRIPTION_RES = "description_res"
        private const val ARG_ICON_RES = "icon_res"

        fun newInstance(titleRes: Int, descriptionRes: Int, iconRes: Int): OnboardingPageFragment {
            return OnboardingPageFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_TITLE_RES, titleRes)
                    putInt(ARG_DESCRIPTION_RES, descriptionRes)
                    putInt(ARG_ICON_RES, iconRes)
                }
            }
        }
    }
}
```

**Step 2: Commit**

```bash
git add app/src/main/java/ca/yyx/hu/ui/onboarding/OnboardingPageFragment.kt
git commit -m "feat: add OnboardingPageFragment"
```

---

### Task 15: Create Dot Indicator Drawable

**Files:**
- Create: `app/src/main/res/drawable/dot_indicator.xml`

**Step 1: Create circular dot drawable**

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="oval">
    <solid android:color="@color/textPrimary" />
    <size
        android:width="@dimen/onboarding_dot_size"
        android:height="@dimen/onboarding_dot_size" />
</shape>
```

**Step 2: Commit**

```bash
git add app/src/main/res/drawable/dot_indicator.xml
git commit -m "feat: add dot indicator drawable for onboarding"
```

---

## Phase 4: Home Dashboard

### Task 16: Create Home Fragment Layout

**Files:**
- Create: `app/src/main/res/layout/fragment_home.xml`

**Step 1: Create responsive grid layout for home tiles**

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/background"
    android:padding="@dimen/tile_spacing">

    <GridLayout
        android:id="@+id/tilesGrid"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:layout_gravity="center"
        android:alignmentMode="alignBounds"
        android:columnCount="3"
        android:rowCount="1"
        android:useDefaultMargins="true">

        <!-- USB Connect Tile -->
        <LinearLayout
            android:id="@+id/usbTile"
            style="@style/TileStyle"
            android:layout_width="0dp"
            android:layout_height="0dp"
            android:layout_columnWeight="1"
            android:layout_rowWeight="1"
            android:layout_margin="@dimen/spacing_small"
            android:orientation="vertical"
            android:gravity="center">

            <ImageView
                android:layout_width="@dimen/tile_icon_size_large"
                android:layout_height="@dimen/tile_icon_size_large"
                android:src="@drawable/ic_usb"
                android:contentDescription="@string/usb_connect" />

            <TextView
                style="@style/TileLabelStyle"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="@dimen/spacing_small"
                android:text="@string/usb_connect" />

            <TextView
                android:id="@+id/usbStatus"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="@string/usb_status_ready"
                android:textColor="@color/textSecondary"
                android:textSize="@dimen/text_caption" />

        </LinearLayout>

        <!-- Settings Tile -->
        <LinearLayout
            android:id="@+id/settingsTile"
            style="@style/TileStyle"
            android:layout_width="0dp"
            android:layout_height="0dp"
            android:layout_columnWeight="1"
            android:layout_rowWeight="1"
            android:layout_margin="@dimen/spacing_small"
            android:orientation="vertical"
            android:gravity="center">

            <ImageView
                android:layout_width="@dimen/tile_icon_size_large"
                android:layout_height="@dimen/tile_icon_size_large"
                android:src="@drawable/ic_settings"
                android:contentDescription="@string/settings" />

            <TextView
                style="@style/TileLabelStyle"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="@dimen/spacing_small"
                android:text="@string/settings" />

        </LinearLayout>

        <!-- Exit Tile -->
        <LinearLayout
            android:id="@+id/exitTile"
            style="@style/TileStyle"
            android:layout_width="0dp"
            android:layout_height="0dp"
            android:layout_columnWeight="1"
            android:layout_rowWeight="1"
            android:layout_margin="@dimen/spacing_small"
            android:orientation="vertical"
            android:gravity="center">

            <ImageView
                android:layout_width="@dimen/tile_icon_size_large"
                android:layout_height="@dimen/tile_icon_size_large"
                android:src="@drawable/ic_exit"
                android:contentDescription="@string/exit" />

            <TextView
                style="@style/TileLabelStyle"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="@dimen/spacing_small"
                android:text="@string/exit" />

        </LinearLayout>

    </GridLayout>

</FrameLayout>
```

**Step 2: Create portrait variant**

Create `app/src/main/res/layout-port/fragment_home.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/background"
    android:padding="@dimen/tile_spacing">

    <GridLayout
        android:id="@+id/tilesGrid"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:layout_gravity="center"
        android:alignmentMode="alignBounds"
        android:columnCount="1"
        android:rowCount="3"
        android:useDefaultMargins="true">

        <!-- USB Connect Tile -->
        <LinearLayout
            android:id="@+id/usbTile"
            style="@style/TileStyle"
            android:layout_width="0dp"
            android:layout_height="0dp"
            android:layout_columnWeight="1"
            android:layout_rowWeight="1"
            android:layout_margin="@dimen/spacing_small"
            android:orientation="vertical"
            android:gravity="center">

            <ImageView
                android:layout_width="@dimen/tile_icon_size_large"
                android:layout_height="@dimen/tile_icon_size_large"
                android:src="@drawable/ic_usb"
                android:contentDescription="@string/usb_connect" />

            <TextView
                style="@style/TileLabelStyle"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="@dimen/spacing_small"
                android:text="@string/usb_connect" />

            <TextView
                android:id="@+id/usbStatus"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="@string/usb_status_ready"
                android:textColor="@color/textSecondary"
                android:textSize="@dimen/text_caption" />

        </LinearLayout>

        <!-- Settings Tile -->
        <LinearLayout
            android:id="@+id/settingsTile"
            style="@style/TileStyle"
            android:layout_width="0dp"
            android:layout_height="0dp"
            android:layout_columnWeight="1"
            android:layout_rowWeight="1"
            android:layout_margin="@dimen/spacing_small"
            android:orientation="vertical"
            android:gravity="center">

            <ImageView
                android:layout_width="@dimen/tile_icon_size_large"
                android:layout_height="@dimen/tile_icon_size_large"
                android:src="@drawable/ic_settings"
                android:contentDescription="@string/settings" />

            <TextView
                style="@style/TileLabelStyle"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="@dimen/spacing_small"
                android:text="@string/settings" />

        </LinearLayout>

        <!-- Exit Tile -->
        <LinearLayout
            android:id="@+id/exitTile"
            style="@style/TileStyle"
            android:layout_width="0dp"
            android:layout_height="0dp"
            android:layout_columnWeight="1"
            android:layout_rowWeight="1"
            android:layout_margin="@dimen/spacing_small"
            android:orientation="vertical"
            android:gravity="center">

            <ImageView
                android:layout_width="@dimen/tile_icon_size_large"
                android:layout_height="@dimen/tile_icon_size_large"
                android:src="@drawable/ic_exit"
                android:contentDescription="@string/exit" />

            <TextView
                style="@style/TileLabelStyle"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="@dimen/spacing_small"
                android:text="@string/exit" />

        </LinearLayout>

    </GridLayout>

</FrameLayout>
```

**Step 3: Commit**

```bash
mkdir -p app/src/main/res/layout-port
git add app/src/main/res/layout/fragment_home.xml app/src/main/res/layout-port/fragment_home.xml
git commit -m "feat: add home fragment layouts for landscape and portrait"
```

---

### Task 17: Create HomeFragment

**Files:**
- Create: `app/src/main/java/ca/yyx/hu/ui/home/HomeFragment.kt`

**Step 1: Create the home fragment**

```kotlin
package ca.yyx.hu.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import ca.yyx.hu.R
import ca.yyx.hu.main.UsbListFragment
import ca.yyx.hu.ui.settings.SettingsActivity

class HomeFragment : Fragment() {

    private lateinit var usbStatus: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        usbStatus = view.findViewById(R.id.usbStatus)

        // USB tile click
        view.findViewById<LinearLayout>(R.id.usbTile).setOnClickListener {
            onUsbClicked()
        }

        // Settings tile click
        view.findViewById<LinearLayout>(R.id.settingsTile).setOnClickListener {
            onSettingsClicked()
        }

        // Exit tile click
        view.findViewById<LinearLayout>(R.id.exitTile).setOnClickListener {
            onExitClicked()
        }
    }

    private fun onUsbClicked() {
        // Show USB device list or start connection
        parentFragmentManager.beginTransaction()
            .replace(R.id.main_content, UsbListFragment())
            .addToBackStack(null)
            .commit()
    }

    private fun onSettingsClicked() {
        startActivity(Intent(requireContext(), SettingsActivity::class.java))
    }

    private fun onExitClicked() {
        requireActivity().finishAffinity()
    }

    fun updateUsbStatus(status: String) {
        usbStatus.text = status
    }
}
```

**Step 2: Commit**

```bash
git add app/src/main/java/ca/yyx/hu/ui/home/HomeFragment.kt
git commit -m "feat: add HomeFragment with tile click handlers"
```

---

## Phase 5: Settings

### Task 18: Create Settings Category Layout

**Files:**
- Create: `app/src/main/res/layout/activity_settings.xml`
- Create: `app/src/main/res/layout/fragment_settings_category.xml`

**Step 1: Create settings activity layout**

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/settings_container"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/background" />
```

**Step 2: Create settings category fragment layout**

Create `fragment_settings_category.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/background"
    android:padding="@dimen/tile_spacing">

    <GridLayout
        android:id="@+id/categoriesGrid"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:layout_gravity="center"
        android:alignmentMode="alignBounds"
        android:columnCount="2"
        android:rowCount="3"
        android:useDefaultMargins="true">

        <!-- Graphics -->
        <LinearLayout
            android:id="@+id/graphicsTile"
            style="@style/TileStyle"
            android:layout_width="0dp"
            android:layout_height="0dp"
            android:layout_columnWeight="1"
            android:layout_rowWeight="1"
            android:layout_margin="@dimen/spacing_small"
            android:orientation="vertical"
            android:gravity="center">

            <ImageView
                android:layout_width="@dimen/tile_icon_size"
                android:layout_height="@dimen/tile_icon_size"
                android:src="@drawable/ic_graphics" />

            <TextView
                style="@style/TileLabelStyle"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="@dimen/spacing_small"
                android:text="@string/settings_graphics" />

        </LinearLayout>

        <!-- Audio -->
        <LinearLayout
            android:id="@+id/audioTile"
            style="@style/TileStyle"
            android:layout_width="0dp"
            android:layout_height="0dp"
            android:layout_columnWeight="1"
            android:layout_rowWeight="1"
            android:layout_margin="@dimen/spacing_small"
            android:orientation="vertical"
            android:gravity="center">

            <ImageView
                android:layout_width="@dimen/tile_icon_size"
                android:layout_height="@dimen/tile_icon_size"
                android:src="@drawable/ic_audio" />

            <TextView
                style="@style/TileLabelStyle"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="@dimen/spacing_small"
                android:text="@string/settings_audio" />

        </LinearLayout>

        <!-- GPS -->
        <LinearLayout
            android:id="@+id/gpsTile"
            style="@style/TileStyle"
            android:layout_width="0dp"
            android:layout_height="0dp"
            android:layout_columnWeight="1"
            android:layout_rowWeight="1"
            android:layout_margin="@dimen/spacing_small"
            android:orientation="vertical"
            android:gravity="center">

            <ImageView
                android:layout_width="@dimen/tile_icon_size"
                android:layout_height="@dimen/tile_icon_size"
                android:src="@drawable/ic_gps" />

            <TextView
                style="@style/TileLabelStyle"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="@dimen/spacing_small"
                android:text="@string/settings_gps" />

        </LinearLayout>

        <!-- Input -->
        <LinearLayout
            android:id="@+id/inputTile"
            style="@style/TileStyle"
            android:layout_width="0dp"
            android:layout_height="0dp"
            android:layout_columnWeight="1"
            android:layout_rowWeight="1"
            android:layout_margin="@dimen/spacing_small"
            android:orientation="vertical"
            android:gravity="center">

            <ImageView
                android:layout_width="@dimen/tile_icon_size"
                android:layout_height="@dimen/tile_icon_size"
                android:src="@drawable/ic_input" />

            <TextView
                style="@style/TileLabelStyle"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="@dimen/spacing_small"
                android:text="@string/settings_input" />

        </LinearLayout>

        <!-- Other -->
        <LinearLayout
            android:id="@+id/otherTile"
            style="@style/TileStyle"
            android:layout_width="0dp"
            android:layout_height="0dp"
            android:layout_columnWeight="1"
            android:layout_rowWeight="1"
            android:layout_margin="@dimen/spacing_small"
            android:orientation="vertical"
            android:gravity="center">

            <ImageView
                android:layout_width="@dimen/tile_icon_size"
                android:layout_height="@dimen/tile_icon_size"
                android:src="@drawable/ic_other" />

            <TextView
                style="@style/TileLabelStyle"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="@dimen/spacing_small"
                android:text="@string/settings_other" />

        </LinearLayout>

        <!-- Back -->
        <LinearLayout
            android:id="@+id/backTile"
            style="@style/TileStyle"
            android:layout_width="0dp"
            android:layout_height="0dp"
            android:layout_columnWeight="1"
            android:layout_rowWeight="1"
            android:layout_margin="@dimen/spacing_small"
            android:orientation="vertical"
            android:gravity="center">

            <ImageView
                android:layout_width="@dimen/tile_icon_size"
                android:layout_height="@dimen/tile_icon_size"
                android:src="@drawable/ic_back" />

            <TextView
                style="@style/TileLabelStyle"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="@dimen/spacing_small"
                android:text="@string/back" />

        </LinearLayout>

    </GridLayout>

</FrameLayout>
```

**Step 3: Commit**

```bash
git add app/src/main/res/layout/activity_settings.xml app/src/main/res/layout/fragment_settings_category.xml
git commit -m "feat: add settings activity and category grid layouts"
```

---

### Task 19: Create SettingsActivity

**Files:**
- Create: `app/src/main/java/ca/yyx/hu/ui/settings/SettingsActivity.kt`

**Step 1: Create the settings activity**

```kotlin
package ca.yyx.hu.ui.settings

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import ca.yyx.hu.R

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, SettingsCategoryFragment())
                .commit()
        }
    }

    fun navigateToDetail(fragment: Fragment, title: String) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.settings_container, fragment)
            .addToBackStack(title)
            .commit()
    }

    override fun onBackPressed() {
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
        } else {
            super.onBackPressed()
        }
    }
}
```

**Step 2: Commit**

```bash
git add app/src/main/java/ca/yyx/hu/ui/settings/SettingsActivity.kt
git commit -m "feat: add SettingsActivity with fragment navigation"
```

---

### Task 20: Create SettingsCategoryFragment

**Files:**
- Create: `app/src/main/java/ca/yyx/hu/ui/settings/SettingsCategoryFragment.kt`

**Step 1: Create the category fragment**

```kotlin
package ca.yyx.hu.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import ca.yyx.hu.R

class SettingsCategoryFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings_category, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<LinearLayout>(R.id.graphicsTile).setOnClickListener {
            navigateTo(GraphicsSettingsFragment(), getString(R.string.settings_graphics))
        }

        view.findViewById<LinearLayout>(R.id.audioTile).setOnClickListener {
            navigateTo(AudioSettingsFragment(), getString(R.string.settings_audio))
        }

        view.findViewById<LinearLayout>(R.id.gpsTile).setOnClickListener {
            navigateTo(GpsSettingsFragment(), getString(R.string.settings_gps))
        }

        view.findViewById<LinearLayout>(R.id.inputTile).setOnClickListener {
            navigateTo(InputSettingsFragment(), getString(R.string.settings_input))
        }

        view.findViewById<LinearLayout>(R.id.otherTile).setOnClickListener {
            navigateTo(OtherSettingsFragment(), getString(R.string.settings_other))
        }

        view.findViewById<LinearLayout>(R.id.backTile).setOnClickListener {
            requireActivity().finish()
        }
    }

    private fun navigateTo(fragment: Fragment, title: String) {
        (activity as? SettingsActivity)?.navigateToDetail(fragment, title)
    }
}
```

**Step 2: Commit**

```bash
git add app/src/main/java/ca/yyx/hu/ui/settings/SettingsCategoryFragment.kt
git commit -m "feat: add SettingsCategoryFragment with navigation"
```

---

### Task 21: Create Settings Detail Fragment Layout

**Files:**
- Create: `app/src/main/res/layout/fragment_settings_detail.xml`

**Step 1: Create the detail fragment layout with header**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:background="@color/background">

    <LinearLayout
        android:id="@+id/header"
        android:layout_width="match_parent"
        android:layout_height="@dimen/settings_header_height"
        android:orientation="horizontal"
        android:gravity="center_vertical"
        android:background="@color/primary"
        android:paddingLeft="@dimen/spacing_medium"
        android:paddingRight="@dimen/spacing_medium">

        <ImageView
            android:id="@+id/backButton"
            android:layout_width="@dimen/tile_icon_size"
            android:layout_height="@dimen/tile_icon_size"
            android:src="@drawable/ic_back"
            android:padding="@dimen/spacing_small"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:clickable="true"
            android:focusable="true" />

        <TextView
            android:id="@+id/headerTitle"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:layout_marginLeft="@dimen/spacing_medium"
            android:textColor="@color/onPrimary"
            android:textSize="@dimen/text_title"
            android:fontFamily="sans-serif-medium" />

    </LinearLayout>

    <FrameLayout
        android:id="@+id/preferences_container"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />

</LinearLayout>
```

**Step 2: Commit**

```bash
git add app/src/main/res/layout/fragment_settings_detail.xml
git commit -m "feat: add settings detail fragment layout with header"
```

---

### Task 22: Create Base Settings Detail Fragment

**Files:**
- Create: `app/src/main/java/ca/yyx/hu/ui/settings/BaseSettingsFragment.kt`

**Step 1: Create base class for settings fragments**

```kotlin
package ca.yyx.hu.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceFragmentCompat
import ca.yyx.hu.R

abstract class BaseSettingsFragment : Fragment() {

    abstract val titleResId: Int
    abstract fun createPreferenceFragment(): PreferenceFragmentCompat

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<TextView>(R.id.headerTitle).setText(titleResId)

        view.findViewById<ImageView>(R.id.backButton).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        if (savedInstanceState == null) {
            childFragmentManager.beginTransaction()
                .replace(R.id.preferences_container, createPreferenceFragment())
                .commit()
        }
    }
}
```

**Step 2: Commit**

```bash
git add app/src/main/java/ca/yyx/hu/ui/settings/BaseSettingsFragment.kt
git commit -m "feat: add BaseSettingsFragment for settings detail screens"
```

---

### Task 23: Create Settings Detail Fragments

**Files:**
- Create: `app/src/main/java/ca/yyx/hu/ui/settings/GraphicsSettingsFragment.kt`
- Create: `app/src/main/java/ca/yyx/hu/ui/settings/AudioSettingsFragment.kt`
- Create: `app/src/main/java/ca/yyx/hu/ui/settings/GpsSettingsFragment.kt`
- Create: `app/src/main/java/ca/yyx/hu/ui/settings/InputSettingsFragment.kt`
- Create: `app/src/main/java/ca/yyx/hu/ui/settings/OtherSettingsFragment.kt`

**Step 1: Create GraphicsSettingsFragment**

```kotlin
package ca.yyx.hu.ui.settings

import androidx.preference.PreferenceFragmentCompat
import ca.yyx.hu.R

class GraphicsSettingsFragment : BaseSettingsFragment() {
    override val titleResId = R.string.settings_graphics
    override fun createPreferenceFragment() = GraphicsPreferenceFragment()
}

class GraphicsPreferenceFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: android.os.Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_graphics, rootKey)
    }
}
```

**Step 2: Create AudioSettingsFragment**

```kotlin
package ca.yyx.hu.ui.settings

import androidx.preference.PreferenceFragmentCompat
import ca.yyx.hu.R

class AudioSettingsFragment : BaseSettingsFragment() {
    override val titleResId = R.string.settings_audio
    override fun createPreferenceFragment() = AudioPreferenceFragment()
}

class AudioPreferenceFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: android.os.Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_audio, rootKey)
    }
}
```

**Step 3: Create GpsSettingsFragment**

```kotlin
package ca.yyx.hu.ui.settings

import androidx.preference.PreferenceFragmentCompat
import ca.yyx.hu.R

class GpsSettingsFragment : BaseSettingsFragment() {
    override val titleResId = R.string.settings_gps
    override fun createPreferenceFragment() = GpsPreferenceFragment()
}

class GpsPreferenceFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: android.os.Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_gps, rootKey)
    }
}
```

**Step 4: Create InputSettingsFragment**

```kotlin
package ca.yyx.hu.ui.settings

import androidx.preference.PreferenceFragmentCompat
import ca.yyx.hu.R

class InputSettingsFragment : BaseSettingsFragment() {
    override val titleResId = R.string.settings_input
    override fun createPreferenceFragment() = InputPreferenceFragment()
}

class InputPreferenceFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: android.os.Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_input, rootKey)
    }
}
```

**Step 5: Create OtherSettingsFragment**

```kotlin
package ca.yyx.hu.ui.settings

import androidx.preference.PreferenceFragmentCompat
import ca.yyx.hu.R

class OtherSettingsFragment : BaseSettingsFragment() {
    override val titleResId = R.string.settings_other
    override fun createPreferenceFragment() = OtherPreferenceFragment()
}

class OtherPreferenceFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: android.os.Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_other, rootKey)
    }
}
```

**Step 6: Commit**

```bash
git add app/src/main/java/ca/yyx/hu/ui/settings/GraphicsSettingsFragment.kt app/src/main/java/ca/yyx/hu/ui/settings/AudioSettingsFragment.kt app/src/main/java/ca/yyx/hu/ui/settings/GpsSettingsFragment.kt app/src/main/java/ca/yyx/hu/ui/settings/InputSettingsFragment.kt app/src/main/java/ca/yyx/hu/ui/settings/OtherSettingsFragment.kt
git commit -m "feat: add settings detail fragments for each category"
```

---

### Task 24: Create Preference XML Files

**Files:**
- Create: `app/src/main/res/xml/preferences_graphics.xml`
- Create: `app/src/main/res/xml/preferences_audio.xml`
- Create: `app/src/main/res/xml/preferences_gps.xml`
- Create: `app/src/main/res/xml/preferences_input.xml`
- Create: `app/src/main/res/xml/preferences_other.xml`

**Step 1: Create preferences_graphics.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<PreferenceScreen xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto">

    <ListPreference
        android:key="resolution"
        android:title="@string/resolution"
        android:summary="Choose screen resolution"
        android:entries="@array/resolution_entries"
        android:entryValues="@array/resolution_values"
        android:defaultValue="1280x720" />

    <ListPreference
        android:key="night_mode"
        android:title="@string/night_mode"
        android:summary="Day/night theme control"
        android:entries="@array/night_mode_entries"
        android:entryValues="@array/night_mode_values"
        android:defaultValue="auto" />

</PreferenceScreen>
```

**Step 2: Create preferences_audio.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<PreferenceScreen xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto">

    <ListPreference
        android:key="mic_sample_rate"
        android:title="@string/mic_sample_rate"
        android:summary="Microphone sampling rate"
        android:entries="@array/sample_rate_entries"
        android:entryValues="@array/sample_rate_values"
        android:defaultValue="16000" />

</PreferenceScreen>
```

**Step 3: Create preferences_gps.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<PreferenceScreen xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto">

    <SwitchPreferenceCompat
        android:key="gps_enabled"
        android:title="@string/gps_for_navigation"
        android:summary="Share GPS location with phone"
        android:defaultValue="true" />

</PreferenceScreen>
```

**Step 4: Create preferences_input.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<PreferenceScreen xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto">

    <Preference
        android:key="keymap"
        android:title="@string/keymap"
        android:summary="Configure hardware key mapping" />

</PreferenceScreen>
```

**Step 5: Create preferences_other.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<PreferenceScreen xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto">

    <EditTextPreference
        android:key="bt_address"
        android:title="@string/bluetooth_address_s"
        android:summary="Bluetooth MAC address for wireless connection" />

    <SwitchPreferenceCompat
        android:key="debug_mode"
        android:title="Debug Mode"
        android:summary="Enable debug logging"
        android:defaultValue="false" />

</PreferenceScreen>
```

**Step 6: Create arrays.xml for preference entries**

Create `app/src/main/res/values/arrays.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string-array name="resolution_entries">
        <item>800x480</item>
        <item>1280x720</item>
        <item>1920x1080</item>
    </string-array>
    <string-array name="resolution_values">
        <item>800x480</item>
        <item>1280x720</item>
        <item>1920x1080</item>
    </string-array>

    <string-array name="night_mode_entries">
        <item>Auto</item>
        <item>Day</item>
        <item>Night</item>
    </string-array>
    <string-array name="night_mode_values">
        <item>auto</item>
        <item>day</item>
        <item>night</item>
    </string-array>

    <string-array name="sample_rate_entries">
        <item>8000 Hz</item>
        <item>16000 Hz</item>
        <item>44100 Hz</item>
    </string-array>
    <string-array name="sample_rate_values">
        <item>8000</item>
        <item>16000</item>
        <item>44100</item>
    </string-array>
</resources>
```

**Step 7: Commit**

```bash
mkdir -p app/src/main/res/xml
git add app/src/main/res/xml/preferences_graphics.xml app/src/main/res/xml/preferences_audio.xml app/src/main/res/xml/preferences_gps.xml app/src/main/res/xml/preferences_input.xml app/src/main/res/xml/preferences_other.xml app/src/main/res/values/arrays.xml
git commit -m "feat: add preference XML files for settings categories"
```

---

## Phase 6: Integration

### Task 25: Update MainActivity

**Files:**
- Modify: `app/src/main/java/ca/yyx/hu/main/MainActivity.kt`

**Step 1: Read current MainActivity**

Read the file to understand its structure.

**Step 2: Update to use HomeFragment and check onboarding**

The MainActivity should:
1. Check if onboarding is complete
2. If not, redirect to OnboardingActivity
3. If yes, show HomeFragment

Add this logic to onCreate:

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Check onboarding
    if (!OnboardingActivity.isOnboardingComplete(this)) {
        startActivity(Intent(this, OnboardingActivity::class.java))
        finish()
        return
    }

    setContentView(R.layout.activity_main)

    if (savedInstanceState == null) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.main_content, HomeFragment())
            .commit()
    }
}
```

**Step 3: Commit**

```bash
git add app/src/main/java/ca/yyx/hu/main/MainActivity.kt
git commit -m "feat: update MainActivity to check onboarding and show HomeFragment"
```

---

### Task 26: Update AndroidManifest

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`

**Step 1: Read current manifest**

Read to understand current structure.

**Step 2: Add new activities**

Add OnboardingActivity and SettingsActivity declarations:

```xml
<activity
    android:name="ca.yyx.hu.ui.onboarding.OnboardingActivity"
    android:theme="@style/AppTheme"
    android:screenOrientation="unspecified" />

<activity
    android:name="ca.yyx.hu.ui.settings.SettingsActivity"
    android:theme="@style/AppTheme"
    android:screenOrientation="unspecified" />
```

**Step 3: Commit**

```bash
git add app/src/main/AndroidManifest.xml
git commit -m "feat: register OnboardingActivity and SettingsActivity in manifest"
```

---

### Task 27: Add ViewPager2 Dependency

**Files:**
- Modify: `app/build.gradle`

**Step 1: Read current build.gradle**

Read to see existing dependencies.

**Step 2: Add ViewPager2 and Preference dependencies**

Add to dependencies block:

```groovy
implementation 'androidx.viewpager2:viewpager2:1.0.0'
implementation 'androidx.preference:preference:1.1.0'
```

**Step 3: Commit**

```bash
git add app/build.gradle
git commit -m "feat: add ViewPager2 and Preference dependencies"
```

---

### Task 28: Build and Verify

**Step 1: Run gradle build**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL

**Step 2: Fix any compilation errors**

Address any issues that arise.

**Step 3: Final commit**

```bash
git add -A
git commit -m "fix: resolve any build issues from UI redesign"
```

---

## Summary

This plan covers 28 tasks across 6 phases:

1. **Phase 1 (Tasks 1-5):** Theme foundation - colors, dimensions, styles, drawables
2. **Phase 2 (Tasks 6-8):** Vector icon assets for all screens
3. **Phase 3 (Tasks 9-15):** Onboarding flow - layouts, fragments, activity
4. **Phase 4 (Tasks 16-17):** Home dashboard with responsive grid
5. **Phase 5 (Tasks 18-24):** Settings with category grid and preference screens
6. **Phase 6 (Tasks 25-28):** Integration and verification

Each task produces a working commit, allowing incremental progress and easy rollback if needed.
