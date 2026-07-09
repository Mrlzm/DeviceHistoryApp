# DeviceHistoryApp

Android device history query tool for checking LoRa/BLE history by device ID and time range.

## Features

- Switch between Release and Dev environments.
- Enter multiple device IDs by line break, comma, or space.
- Pick dates from the calendar or type dates manually.
- Select hour and minute from dropdowns.
- Scan device IDs from QR codes.
- Show query results with detail rows and summary counts.

## Requirements

- Android Studio or local Gradle environment
- JDK 17
- Android SDK
- Git

## Clone

```bash
git clone git@github.com:Mrlzm/DeviceHistoryApp.git
cd DeviceHistoryApp
```

## Local Configuration

Create or update `local.properties` in the project root:

```properties
sdk.dir=C\:/Users/<your-name>/AppData/Local/Android/Sdk
api.token=your_api_token_here
```

Do not commit `local.properties`, API tokens, APK files, or build output.

## Build

Open the project in Android Studio and build/install it from there, or use your local Gradle installation:

```bash
gradle assembleRelease
```

The release APK is generated under:

```text
app/build/outputs/apk/release/
```

## Team Collaboration

1. Add team members in GitHub: repository `Settings` -> `Collaborators and teams` -> invite members.
2. Ask each member to add an SSH key to their GitHub account.
3. Members clone the project:

```bash
git clone git@github.com:Mrlzm/DeviceHistoryApp.git
```

4. Before starting work, pull the latest code:

```bash
git pull --rebase origin main
```

5. For a new change, create a branch:

```bash
git checkout -b feature/your-change-name
```

6. Commit and push:

```bash
git add .
git commit -m "Describe the change"
git push origin feature/your-change-name
```

7. Open a Pull Request on GitHub for review, then merge after the team confirms it.

## Notes

- Keep API tokens in `local.properties` only.
- Use the Dev environment for staging verification.
- Use the Release environment only for production data.
