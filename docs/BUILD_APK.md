# Building the SplitSaathi Android APK

## Method 1: Direct Cloud Export (Fastest)
In Google AI Studio, tap the top-right settings/export menu and click **Export APK** or **Download APK**. The system will build and package the signed debug/release APK ready to install on your Android device.

## Method 2: Local Gradle Build
```bash
# Clone the repository
git clone <repo-url>
cd SplitSaathi

# Assemble the Debug APK
gradle assembleDebug

# Output location:
# app/build/outputs/apk/debug/app-debug.apk
```

## Method 3: EAS Build (If deploying to Expo Application Services)
```bash
# 1. Install EAS CLI
npm install -g eas-cli

# 2. Login to your Expo account
eas login

# 3. Configure EAS Build
eas build:configure

# 4. Build Android APK using preview profile
eas build -p android --profile preview

# 5. Download APK from EAS dashboard, transfer to device and enable "Install unknown apps"
```
