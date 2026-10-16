# Pocket Coach

An open-source strength and conditioning app for Android, in the spirit of TrainHeroic: a coach writes a program,
the athlete follows it day by day, logs every set, and watches their numbers move. Kotlin, Jetpack Compose,
Material You. Everything lives on the phone.

- **Today** – a week strip with your calendar. The session your program puts on today is one tap from starting;
  logged workouts stay on their day. An empty workout can be started on any date.
- **Programs** – write programs as weeks × days. Each day is a session of blocks (A, B, C…); a block with two
  exercises is a superset. Every set carries its own prescription: reps, time or distance, and a load given as
  **% of 1RM**, **RPE**, a fixed weight or nothing. Copy a week forward or to every week, duplicate a program, and
  follow it from a chosen Monday.
- **Workout** – the logging screen. Targets on the left (percentages already turned into kilos or pounds from your
  current 1RM, rounded to your plate step), load / reps / RPE inputs, a tick per set, a rest timer with a beep, and a
  finish summary. Each exercise shows what you did last time, and those numbers sit in the load and reps fields as
  placeholders, so ticking an untouched set logs the prescription, or failing that last time's numbers, as performed.
- **Library** – ~95 built-in exercises by category and equipment, plus your own, each with a how-to description and
  a demo video button (your own link, or a web video search opened in the browser; the app itself stays offline).
  Tap an exercise during a workout to read it. Each also has a one-rep max you can enter or let the app estimate
  (Epley) from what you lift, an e1RM chart and a full set history.
- **Progress** – totals, a 12-week tonnage / sets / days chart, records per exercise and the workout history.
- **Bring your own plan** – import a training plan from a spreadsheet: a `.csv` or `.xlsx` with one row per exercise
  (see the format below). Open the file from any app's share sheet, or Programs → import. The app can save a
  filled-in template to start from, and rows it can't read are skipped and counted rather than failing the import.
- **Coaching without a server** – a program exports to a small JSON file. Send it however you like; the athlete
  opens it in Pocket Coach (share sheet or Programs → import) and follows it. Exercises travel by name and are
  created on the other phone if missing.

There is no account, no server and no analytics; the app has no network permission. Data is plain SQLite and is
included in Android device backups.

## Installing

- **F-Droid client (Droid-ify, Neo Store, F-Droid)**: add the repo `https://frenchtoastbig7-ux.github.io/fdroid/repo`
  and install Pocket Coach; updates arrive through the client.
- **Direct APK**: each release on the [Releases page](https://github.com/frenchtoastbig7-ux/pocket-coach/releases)
  carries the signed APK.

Release signing certificate (SHA-256):
`b3527f4e3fb3aebcc861ffc8b8ea2b4eb461c48d2ce631972eed564fc37e3446`

## Building

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew assembleDebug
```

Requires JDK 21 and an Android SDK with platform 37.2 (`local.properties` points at it). minSdk 31. Release builds
are R8-minified; signing is read from `pocketcoach.*` properties in `~/.gradle/gradle.properties` when present.

Unit tests: `./gradlew testDebugUnitTest` (strength arithmetic, unit rounding, program file round trip).

## Spreadsheet plan format

One row per exercise. The first row names the columns; order doesn't matter and the names are matched loosely
(`Wk`, `Movement`, `Weight`… all work). Google Sheets, Excel and Numbers exports are fine; only the first sheet of an
`.xlsx` is read.

| Column | Meaning |
|---|---|
| Week | 1, 2, 3… Carries down when blank. |
| Day | 1–7 or Mon, Tue… (1 = Monday). Carries down when blank. |
| Session | Title for that day (optional). |
| Block | A, B, C… Rows with the same letter on the same day form a superset; blank means one block per row. |
| Exercise | Name. Unknown names are added to the library. |
| Sets | How many. `5x5` in the Reps cell works too. |
| Reps | `5`, `8-12`, `AMRAP`, or a time (`45s`, `1:30`) or distance (`40 m`, `2 km`). A list (`5/5/3`) gives each set its own value. |
| Load | `75%` (of 1RM), `RPE 8`, `100 kg`, `225 lb`, a bare number (your unit), `BW`, or blank. Lists work: `70%/75%/80%`. |
| Rest | Seconds or m:ss. |
| Notes | Coach cues. Optional extras: Category, Equipment, Type (weight / bodyweight / time / distance). |
| Video, Description | Optional: a demo link and how-to text, stored on the exercise. |

```csv
Week,Day,Session,Block,Exercise,Sets,Reps,Load,Rest,Notes
1,Mon,Lower,A,Back Squat,5,5,70%,3:00,Belt on the last two sets
1,Mon,,B,Romanian Deadlift,3,8,RPE 7,2:00,
1,Mon,,C,Walking Lunge,3,10,,1:00,Superset with plank
1,Mon,,C,Plank,3,45s,,1:00,
1,Fri,Full body,A,Deadlift,3,5/5/3,75%/80%/85%,3:00,Top set heavy
```

The program is named after the file. Percentages are resolved against the athlete's 1RM when a session is started.

Want Claude to write the plan? [docs/claude-plan-prompt.md](docs/claude-plan-prompt.md) is a prompt that makes it answer in exactly
this CSV format.

## Program file format

```json
{
  "format": "pocketcoach.program",
  "version": 1,
  "program": {
    "name": "Foundations", "description": "…", "author": "…", "weeks": 4,
    "sessions": [
      { "week": 1, "day": 1, "title": "Lower", "notes": "",
        "blocks": [
          { "label": "A", "notes": "", "items": [
            { "exercise": { "name": "Back Squat", "category": "Squat", "equipment": "Barbell", "loadKind": "weight" },
              "restSec": 180, "notes": "",
              "sets": [ { "reps": "5", "rx": "percent", "load": 70 } ] }
          ] }
        ] }
    ]
  }
}
```

`day` is 1 (Monday) to 7. `rx` is `none`, `percent`, `rpe` or `absolute` (kilograms). Timed and distance sets use
`seconds` / `distance` (metres) instead of `reps`. `loadKind` is `weight`, `bodyweight`, `time` or `distance`.

## Layout

```
app/src/main/java/com/sovereignedge/pocketcoach/
  CoachApp.kt, MainActivity.kt, Settings.kt
  data/        Models, Strength (Epley, unit rounding), CoachDb (SQLite schema + seed library and sample program),
               Repo (all reads as Flows, all writes), ProgramJson (program file codec), SpreadsheetProgram (CSV/XLSX reader)
  ui/          AppRoot (tabs + route stack), Theme; common widgets, charts and the exercise picker
  ui/today     Week strip, planned sessions, logged workouts
  ui/programs  Program list, week × day grid editor, session/block/set-prescription editor
  ui/workout   Set logging, rest timer, finish + records
  ui/library   Exercise list, exercise detail (1RM, e1RM chart, history)
  ui/progress  Totals, weekly chart, records, history
  ui/settings  Units, rounding, rest, theme, coach name
```

## Roadmap

- Plate calculator and warm-up set generator
- Body-weight log and photos
- Leaderboards and team feed over an optional self-hosted server (the data model already carries UUIDs for this)
- Import TrainHeroic's own export once a sample of its layout is available

## License

Apache License 2.0. Copyright 2026 Sovereign Edge Inc. See `LICENSE` and `NOTICE`.
