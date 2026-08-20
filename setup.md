# Project Setup Guide

Follow these instructions to set up the Swadeshi Scanner development environment and build the application.

## 1. Prerequisites

Before you begin, ensure your computer has the following software installed:

- Android Studio: Version Ladybug (2024.2.1) or newer.
- Java Development Kit (JDK): Version 11.
- Android SDK: The project targets SDK 36.

## 2. Project Requirements

The application requires specific hardware and software conditions to function correctly:

- Android Device: A physical phone or emulator running Android 8.0 (API level 26) or higher.
- Internet Connection: Required for file conversion features (Word, Excel, and PowerPoint tools).
- Camera: A functional camera is required for document scanning features.

## 3. Installation Steps

1. Get the Code: Download the project source code to a folder on your computer.
2. Open in Android Studio: Launch Android Studio and choose the option to open an existing project. Navigate to the project folder and select it.
3. Gradle Sync: Wait for Android Studio to finish syncing the project files. This process downloads the necessary libraries and may take several minutes depending on your internet speed.
4. Connect Device: Connect your Android phone to your computer using a USB cable. Ensure that USB Debugging is enabled in the Developer Options of your phone.
5. Build and Run: Locate the green play button in the top toolbar of Android Studio. Click this button to build the project and install the app on your connected device.

## 4. Backend Configuration

The document conversion features (PDF to Word, etc.) rely on a remote server. The application is pre-configured to connect to the following address:

- Backend URL: https://jsujalkumar7899-swadeshi-converter.hf.space

If you wish to use a custom backend, you must update the URL in the following file:
`com.swadeshiscanner.app.network.ConverterApi`

## 5. Cleaning the Project

If you encounter build errors, you can perform a clean build by following these steps:

1. Click on the Build menu in the top toolbar.
2. Select Clean Project.
3. Once finished, select Rebuild Project from the same menu.

## 6. Build Command

To generate an installation file (APK) from your computer terminal, use the following command:

```bash
./gradlew assembleDebug
```

The generated file will be located in the folder: `app/build/outputs/apk/debug/`
