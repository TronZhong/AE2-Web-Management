# AE2 Web Management

This repository contains separate projects for the Minecraft mod and the web
application. They are intentionally independent; their future integration
should be limited to a versioned data contract and network communication.

## Repository layout

- `mod/` — NeoForge mod project. Owns Minecraft and AE2 integration.
- `web/backend/` — Spring Boot backend scaffold. Will own web APIs, access
  control, and persistence.
- `web/frontend/` — Reserved for the browser application.
- `contracts/` — Reserved for API and message schemas shared by the mod and
  backend. Do not place implementation code from either project here.

The frontend, backend, and mod can be built and run independently. No AE2
integration, API, or data exchange is implemented yet.

## Build

Build the mod from the repository root:

```powershell
.\mod\gradlew.bat -p .\mod build
```

Build and test the backend from its project directory:

```powershell
cd .\web\backend
.\mvnw.cmd test
```

## Boundaries

- The mod is the only component that reads or changes in-game AE2 state.
- The backend coordinates requests, authorization, and web-facing data.
- The frontend talks only to the backend.
- The mod and backend exchange data through documented, versioned contracts;
  neither project should depend on the other's internal classes.
