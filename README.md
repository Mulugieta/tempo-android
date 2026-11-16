# Tempo for Android

Workout log with camera rep counting. Kotlin, Jetpack Compose, CameraX and ML Kit pose detection.

## Build and run

1. Install Android Studio (Ladybug or newer) with an Android 15 SDK (API 35).
2. Open this folder. Studio downloads Gradle 8.9 and syncs.
3. Plug in a phone with USB debugging on and press Run. A real phone is best; emulator cameras do not show a person.

To make an installable file: Build > Build APK(s). Debug APKs can be sideloaded directly.

Min Android version is 8.0 (API 26).

## Counting reps from the camera

Pick an exercise, tap Start, step into view. After a 3 second countdown the app reads your body
pose on the phone, draws a skeleton over the preview and adds one rep each time you go from the
resting position to the working position and back. Video is processed on the device and is never
stored or uploaded.

| Exercise | Measured | Rest position | Counts as working position |
| --- | --- | --- | --- |
| Squats | knee angle (hip, knee, ankle) | above 160 degrees | below 110 degrees |
| Push-ups | elbow angle, body must be roughly horizontal | above 150 degrees | below 100 degrees |
| Curls | elbow angle | above 140 degrees | below 55 degrees |
| Jumping jacks | hands vs head, foot gap vs shoulder width | hands down, feet together | hands above head, feet wide |

Setup tips: side-on for squats, push-ups and curls (prop the phone against something, 2 to 3 metres
away, whole body in frame); face the camera for jumping jacks. Good light and a plain background help.
Rep thresholds are at the top of `RepEngine.kt` if you want stricter or looser counting.

## Workout planner

Tap Create on the home screen to build a 4 week plan. Answer six questions (goal, experience, days per
week, time per session, equipment, low impact) and Tempo builds the plan on the phone. No account,
server or API key is involved.

What makes it personal:
- The goal sets the weekly mix of strength and cardio (for example upper/lower split for strength,
  strength plus run-walk intervals for weight loss).
- Experience and week set sets, reps and cardio structure. Weeks 1 to 3 build up, week 4 is lighter.
- Time per session decides how many exercises fit, with a cardio finisher if time is left.
- Your history adjusts it: if you have been training less often than you asked for, week 1 starts with
  fewer sessions and builds up. Rep targets for squats, push-ups and curls are set from your best
  camera-counted set.
- Low impact swaps running for walking and avoids jumping.

Each exercise the camera can count has a Count button that opens the camera with that exercise and the
target reps preset. Sessions are ticked off with Mark done. The plan logic lives in `plan/Planner.kt`
and is plain Kotlin, covered by `PlannerTest.kt`.

## Layout

- `pose/RepEngine.kt` rep counting state machine, plain Kotlin, no Android dependencies.
- `pose/PoseMapping.kt` converts ML Kit landmarks to the engine's frame.
- `ui/CameraScreen.kt` CameraX preview, pose overlay, countdown, save.
- `ui/HomeScreen.kt`, `ui/ManualScreen.kt` weekly summary, history, manual logging.
- `plan/Planner.kt` plan generator, `plan/PlanJson.kt` plan saving, `ui/PlanScreen.kt` plan form and view.
- `data/Workout.kt` workout model and stats, `data/Store.kt` workouts and plan stored as JSON files on the phone, weekly goal in preferences.
- `app/src/test/.../RepEngineTest.kt` unit tests for the counter (run with `./gradlew test`).
