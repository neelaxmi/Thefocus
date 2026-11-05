# Remaining project files to upload

This repository currently contains only a partial root setup. The supplied `files(3).zip` contains the complete milestone source tree inside `Thefocus.zip`.

Upload the contents of that nested `Thefocus.zip` to the repository root, preserving every directory and filename. Do not upload only the ZIP as a single file.

Important remaining paths include:
- `app/` (all Kotlin source, manifest, resources, unit and instrumentation tests)
- `core-engine/src/` (all engine Kotlin source and tests)
- `docs/` (all project documentation and evidence)
- `scripts/` (verification scripts)
- `.github/workflows/ci.yml`
- `gradle/` (version catalog, wrapper properties and wrapper JAR)
- `gradlew` and `gradlew.bat`
- `.gitignore`

The binary file `gradle/wrapper/gradle-wrapper.jar` must be uploaded as the original binary; do not convert it to text. After extracting on Windows, restore executable permissions for shell scripts and `gradlew` if needed.
