# tools/kt_lint.py

A small, dependency-free static checker for this project — not a real Kotlin
compiler (we can't run one with the full Android/androidx classpath outside
Android Studio/CI), but purpose-built to catch the exact bug classes that have
actually crashed this app in the field:

- `Regex(...)` / `Pattern.compile(...)` literals with an unescaped `{` or `}`.
  These compile fine on your dev machine's desktop-JVM regex engine but throw
  `PatternSyntaxException` on Android's ICU regex engine at runtime. (This is
  what crashed `TsplBuilder`.)
- Unsafe `as Type` casts directly on APIs that are documented to return null
  (`getSystemService`, `findViewById`, intent extras, etc). (This is what
  crashed `UsbPrinter`.)
- Mismatched braces/parens/brackets per file (catches truncated/merge-damaged
  files).
- Duplicate `composable("route")` strings in navigation, and `XScreen(...)`
  calls with no matching `fun XScreen(` anywhere in the project.
- Leftover merge-conflict markers.
- `!!` non-null assertions, listed for manual review.

## Run it locally

```
python3 tools/kt_lint.py app/src/main/java
```

Exit code is 2 if any HIGH severity finding exists, 0 otherwise — so it's
CI-friendly as a gate.

## Run it in the GitHub Actions build

Add this step to `.github/workflows/build-apk.yml`, before the Gradle build
step, so a bug like the two above fails the build instead of shipping:

```yaml
      - name: Static checks (kt_lint)
        run: python3 tools/kt_lint.py app/src/main/java
```
