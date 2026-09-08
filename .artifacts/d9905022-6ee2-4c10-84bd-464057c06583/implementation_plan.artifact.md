# Fix Redeclaration of NotificacionReceiver

The goal is to resolve the `Redeclaration` error for the `NotificacionReceiver` class, which is currently defined in both `MainActivity.kt` and `NotificacionReceiver.kt`. I will consolidate the implementation in `NotificacionReceiver.kt` and remove it from `MainActivity.kt`.

## Proposed Changes

### [app]

#### [MODIFY] [NotificacionReceiver.kt](file:///Users/naisor/AndroidStudioProjects/StorylineAnniversary/app/src/main/java/com/example/storyline_anniversary/NotificacionReceiver.kt)
- Update the class with the latest implementation from `MainActivity.kt` (including `canal_actividades_aniversario_v2`, custom sound, and transparent icon).
- Add necessary imports: `android.media.AudioAttributes`, `android.content.ContentResolver`, and `android.net.Uri`.

#### [MODIFY] [MainActivity.kt](file:///Users/naisor/AndroidStudioProjects/StorylineAnniversary/app/src/main/java/com/example/storyline_anniversary/MainActivity.kt)
- Remove the `NotificacionReceiver` class declaration to resolve the redeclaration error.

## Verification Plan

### Automated Tests
- Run `./gradlew :app:compileDebugKotlin` to verify the redeclaration error is resolved and the project builds successfully.
