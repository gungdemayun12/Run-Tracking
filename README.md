# RunTrack Android App

RunTrack is an Android fitness tracking app built in Java with Firebase backend. It includes onboarding, authentication, running tracking with Google Maps, Firestore persistence, offline caching with Room, and a bottom navigation UI.

## Setup

1. Open the project in Android Studio.
2. Add your `google-services.json` file to `app/`.
3. Replace `@string/google_maps_key` in `app/src/main/res/values/strings.xml` with your Google Maps API key.
4. Ensure Firebase Authentication, Firestore, and Storage are enabled in your Firebase project.
5. Build the project.

## Key files

- `app/src/main/AndroidManifest.xml` - app permissions and service declarations
- `app/src/main/java/com/example/runtrack/service/RunningService.java` - foreground tracking service
- `app/src/main/java/com/example/runtrack/ui/auth` - login/register/splash activities
- `app/src/main/java/com/example/runtrack/ui/main` - main navigation activity
- `app/src/main/java/com/example/runtrack/ui/fragments` - home/history/progress/profile fragments
- `app/firebase/firestore.rules` - Firestore security rules
- `app/firebase/storage.rules` - Firebase Storage security rules

## Run

```bash
./gradlew assembleDebug
```

Then deploy from Android Studio to a device or emulator.
