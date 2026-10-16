# Java code style

Use 4-space indentation, same-line braces, 100-column wrapping, and 8-space continuations. Run the SHA-256-pinned **google-java-format 1.28.0 in AOSP mode** through `tools/format_java.py`.

## Final by default

- Make all method/constructor parameters, explicit lambda parameters, catch parameters, and resource declarations `final`. Record components are inherently final; do not add an explicit component modifier.
- Use final locals and fields when their bindings do not change. A final collection or array reference still allows content updates; preserve existing collection semantics and avoid unnecessary defensive copies.
- Normalize arguments into separately named values instead of reassigning parameters.
- Keep actual algorithm state mutable: counters, minima/maxima, rolling word context, lazy buffers, and reused dynamic-programming row pointers. Keep Android lifecycle fields initialized after construction and interactive state mutable.
- Do not add wrappers, streams, allocations, repeated calculations, or unsafe constructor/context work merely to add more final modifiers. Compute immutable model state locally and assign final fields once.

## Types, imports, and braces

- Use explicit types instead of `var`, including lambda parameters.
- Use explicit imports. Fully qualified names are appropriate when needed to distinguish conflicting names, such as this application's `R` and `android.R`.
- Declare one field/local per statement. Classic loop initializers may group counters.
- Always brace control-flow bodies, including one-line bodies. Keep conventional `else if` chains flat. Expression lambdas are permitted with explicit final parameters.

## Format and verify

Use Python 3 and JDK 17 or newer. Set `JAVA_HOME` or put `java` on PATH:

```text
python tools/format_java.py
python tools/format_java.py --check
```

The first run downloads the official formatter to ignored `.gradle/style` and verifies its pinned SHA-256. For offline use, pass `--formatter-jar PATH`; it must match the same checksum. Pass `--java PATH` to select a JDK executable.

Both commands run the formatter and a syntax-tree checker using JDK compiler APIs, without Android SDK or parser dependencies. Checks cover final parameters/resources, explicit lambda types, absence of `var`/wildcard imports, one declaration per statement, and braced control flow across app/core/test/benchmark/tool sources. Formatting fixes layout and import ordering; it does not change parameter or variable semantics automatically.

The checker does not claim full definite-assignment or lifecycle analysis. Compiler errors, code review, and behavioral checks determine which local bindings and fields require mutation. Existing Gradle and portable core verification commands remain unchanged. Run the appropriate build, lint, and behavioral tests after changes.
