# Decision: jpackage via Maven prod profile

## What
Added a `prod` Maven profile that produces a `.msi` Windows installer via jpackage (ships with JDK 21).

## Build steps
1. `maven-dependency-plugin` copies the fat JAR to `target/jpackage-input/app.jar` (clean staging dir — jpackage bundles everything in the input dir)
2. `exec-maven-plugin` calls `${java.home}/bin/jpackage` to produce `target/installer/Farmacia Scheduler-1.0.0.msi`

## To build the installer
```
mvn verify -Pprod
```
Output: `target/installer/Farmacia Scheduler-1.0.0.msi`

## JVM flags
`-Dapp.packaged=true` triggers auto browser-open on startup (skipped during dev).

## Requirements
- Must run on Windows (jpackage --type msi requires WiX toolset on the build machine)
- `src/main/resources/icon.ico` must exist before building
- JDK 21+ (jpackage ships with the JDK)

## Dev workflow unchanged
Normal development uses `mvn spring-boot:run` or IntelliJ run config. The prod profile only activates with `-Pprod`.
