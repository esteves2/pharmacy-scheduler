# Decision 008 — frontend-maven-plugin wiring

## What
Added `com.github.eirslett:frontend-maven-plugin:1.15.0` to `pom.xml`.

## Why
Step 8 of the agreed MVP plan: bake the React build into the fat JAR so the end user can double-click one file instead of running a separate `npm run build`.

## Decisions made without explicit approval

### Node version: v22.11.0
Chose the current Node 22 LTS. The plugin downloads Node locally into `frontend/.node/` — no system Node installation required.

### SpaController added
React Router uses client-side routing. Without a fallback, a hard refresh on `/availability` returns a 404 from Spring Boot because no server-side route exists for that path.

`SpaController` catches all non-API, non-asset GET requests and forwards them to `index.html`. The regex `{path:[^\\.]*}` excludes paths with a dot (so `.js`, `.css`, `.ico` etc. pass through to the static resource handler).

### Plugin executes in `generate-resources` phase
This runs before `compile` and `process-resources`, so the built assets are on disk when the Spring Boot packager picks them up. `package` phase would be too late.

## How to build and run
```bash
mvn package -DskipTests
java -jar target/pharmacy-scheduler-0.0.1-SNAPSHOT.jar
```
Or just run from IntelliJ as before — the plugin only triggers on `mvn package`.
