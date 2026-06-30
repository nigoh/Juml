# CarSystemUI / Launcher / RRO Cheatsheet

## CarSystemUI 構成

```
packages/apps/CarSystemUI/
├── src/com/android/systemui/
│   ├── car/  (AAOS 特化)
│   │   ├── systembars/  (status bar, nav bar)
│   │   ├── notification/  (notification mgr)
│   │   ├── input/  (input handling: steering wheel)
│   │   ├── window/  (multi-display)
│   │   ├── hvac/  (HVAC UI)
│   │   ├── media/  (media control)
│   │   └── ...
│   ├── recents/  (recent apps)
│   ├── statusbar/  (base statusbar)
│   ├── navigationbar/  (nav control)
│   └── ...
├── res/
│   ├── values/  (strings, dimens, colors)
│   ├── values-land/  (landscape)
│   ├── drawable/  (assets)
│   └── layout/  (UI layouts)
└── AndroidManifest.xml
```

---

## CarSystemUI Activity / Service

### CarUIViewType enum

```java
enum CarUIViewType {
    MAIN_CONTENT,        // 中央メインコンテンツエリア
    STATUS_BAR_TOP,      // ステータスバー (上)
    NAVIGATION_BAR,      // ナビゲーションバー (下)
    HUN,                 // Heads-up Notification (小窓)
    VOICE_PLATE,         // 音声認識プレートエリア
    SYSTEM_DIALOG,       // Dialog window
}
```

### CarLauncher

別アプリ:

```
packages/apps/CarLauncher/
├── src/com/android/car/launcher/
│   ├── CarLauncher.java  (main activity)
│   ├── AppGridFragment.java  (grid layout)
│   ├── AppsSeparator.java  (category separator)
│   └── ...
├── res/  (layouts, drawables)
└── AndroidManifest.xml
```

**役割**: Home screen (desktop equivalent)。

---

## Multi-Display Support

```
DisplayManager
├── DISPLAY_ID_MAIN  (infotainment, user-facing, width=1920)
├── DISPLAY_ID_CLUSTER  (instrument cluster, 1024×256 or similar)
├── DISPLAY_ID_REAR  (rear seat display, optional)
└── DISPLAY_ID_AUXILIARY  (other zones)

CarSystemUI registers listeners:
onDisplayAdded(displayId)
onDisplayRemoved(displayId)
onDisplayChanged(displayId)
  ↓
update UI layout for each display
```

### Context per Display

```java
DisplayContext displayContext = context.createDisplayContext(display);
  ↓
UI elements can be created per display
```

---

## Runtime Resource Overlay (RRO)

OEM が framework リソース (色, フォント, 文字列) を置き換え:

```
framework/
  ├── res/
  │   ├── values/colors.xml
  │   │   <color name="color_accent">#FF6200EE</color>
  │   └── values/strings.xml
  │       <string name="app_name">Android</string>
  └── ...

vendor/overlay/  (OEM RRO)
  ├── framework/
  │   ├── res/
  │   │   ├── values/colors.xml  (OEM override)
  │   │   │   <color name="color_accent">#FF0066FF</color>  ← 置き換え
  │   │   └── ...
  │   └── ...
  └── SystemUI/
      └── res/
          ├── values/config.xml  (OEM config)
          └── ...
```

### RRO マニフェスト

```xml
<manifest xmlns:android="...">
    <overlay
        android:targetPackage="com.android.systemui"
        android:isStatic="true"
        android:priority="20"
        android:requiredSystemPropertyName="..."
        />
</manifest>
```

### RRO 有効化 (OEM カスタマイズ)

```sh
# ビルド時に overlay が自動統合
# または adb shell:
adb shell cmd overlay list
adb shell cmd overlay enable vendor.overlay.framework
```

---

## Theme / Color Customization

RRO 経由:

```xml
<resources>
    <color name="primary_color">#FF6200EE</color>
    <color name="secondary_color">#FF03DAC6</color>
    <dimen name="status_bar_height">24dp</dimen>
    <dimen name="font_size_large">24sp</dimen>
</resources>
```

CarSystemUI layout が参照:

```xml
<LinearLayout
    android:background="@color/primary_color"
    android:textSize="@dimen/font_size_large"
    ... />
```

---

## Input Routing

```
Steering wheel / Controller key event
  ↓ (kernel input device)
InputManager
  ↓
CarInputService
  ↓
registered callbacks (CarInputManager listeners)
  ├─→ CarLauncher (home key)
  ├─→ media controller (play/pause)
  ├─→ voice assistant (voice button)
  └─→ CarSystemUI (nav buttons)
```

### Input Event Listener

```java
CarInputManager.registerListener(
    new CarInputManager.CarInputEventListener() {
        @Override
        public void onEvent(CarInputEvent event) {
            switch (event.getInputCode()) {
                case CarInputManager.INPUT_TYPE_DPAD_CENTER:
                    // handle OK/confirm
                    break;
                case CarInputManager.INPUT_TYPE_DPAD_LEFT:
                    // handle left navigation
                    break;
                // ...
            }
        }
    },
    INPUT_TYPE_DPAD_CENTER, INPUT_TYPE_DPAD_LEFT, ...
);
```

---

## Multi-Zone Audio UI

```
CarAudioManager
├── Zone 0: Front
├── Zone 1: Rear left
├── Zone 2: Rear right
└── Zone 3: All (broadcast)

CarAudioUI
├── Volume slider zone 0 (front)
├── Volume slider zone 1 (rear L)
└── Volume slider zone 2 (rear R)
```

UI 조작時:

```
user adjusts volume slider for zone 1
  ↓
CarAudioManager.setGroupVolume(zone=1, groupId=NAVIGATION, volume=8)
  ↓ (Binder IPC)
CarAudioService.setGroupVolume(...)
  ↓
HAL audio routing
```

---

## Juml での可視化

### CarSystemUI Manifest 図

```sh
java -Xmx4g -jar Juml.jar -M -o carsystemui-manifest.svg \
  ~/AOSP/packages/apps/CarSystemUI
```

**可視化**:
- Activity 一覧 (exported 属性)
- Service 一覧
- uses-permission リスト (MODIFY_AUDIO_SETTINGS など)

### CarSystemUI クラス図

```sh
java -Xmx4g -jar Juml.jar -c -o carsystemui-class.svg \
  ~/AOSP/packages/apps/CarSystemUI/src/com/android/systemui/car
```

**可視化**:
- Fragment (各 UI component)
- Controller (business logic)
- Listener callback classes

### CarLauncher クラス図

```sh
java -Xmx4g -jar Juml.jar -c -o carlauncher-class.svg \
  ~/AOSP/packages/apps/CarLauncher/src/com/android/car/launcher
```

---

## RRO リソース構造

```sh
# RRO ディレクトリ探索
find ~/AOSP -path "*overlay*" -type d | grep -i "car\|systemui"

# RRO 定義確認
find ~/AOSP/vendor -name "AndroidManifest.xml" | xargs grep -l "overlay" | head -10
```

---

## Status Bar / Navigation Bar カスタマイズ

```xml
<!-- res/layout/car_status_bar.xml -->
<LinearLayout
    android:id="@+id/status_bar_container"
    android:layout_width="match_parent"
    android:layout_height="@dimen/status_bar_height"
    android:background="@color/primary_color"
    android:orientation="horizontal">
    
    <ImageView
        android:id="@+id/signal_strength"
        android:src="@drawable/ic_signal" />
    
    <TextView
        android:id="@+id/clock"
        android:text="14:30" />
    
    <!-- more elements -->
</LinearLayout>
```

---

## Notification Management

CarSystemUI は Custom notification manager:

```java
class CarNotificationView extends FrameLayout {
    void onNotificationPosted(NotificationEvent event) {
        // custom notification rendering
        // NOT default Android notification shade
    }
}
```

通常の Android notification shade は表示されず、
CarSystemUI custom HUN (Heads-up Notification) 窓を使用。

---

## Configuration Management (carservice_config.xml)

```xml
<!-- packages/services/Car/service/res/values/carservice_config.xml -->
<resources>
    <bool name="car_enable_hvac">true</bool>
    <integer name="car_hvac_min_temp">15</integer>
    <integer name="car_hvac_max_temp">32</integer>
    <string-array name="car_enabled_services">
        <item>android.car.CarPropertyService</item>
        <item>android.car.CarAudioService</item>
        <!-- ... -->
    </string-array>
</resources>
```

OEM はこれを RRO で置き換え可能。

---

## デバッグ

```sh
# CarSystemUI log
adb logcat | grep -i "carsystemui\|carlauncher"

# display 状態
adb shell dumpsys display

# input events
adb shell getevent | grep -i "key\|dpad"

# 有効な RRO list
adb shell cmd overlay list
```

---

## Multi-Display Constraints

```
DISPLAY_ID_MAIN: Full UI (CarLauncher, CarSystemUI, user apps)
DISPLAY_ID_CLUSTER: Instrument cluster only (restricted, no app window)
DISPLAY_ID_REAR: Optional rear seat display
DISPLAY_ID_AUXILIARY: OEM specific zones
```

DISPLAY_ID_CLUSTER はシステム専用、アプリは windowType で制限される。
