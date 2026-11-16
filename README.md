# HoDoWo

**How does it work?** A utility to guide you when using home appliances.

Take a photo of the appliance or its control panel, ask a question in natural
language and get model-specific help with a step-by-step visual guide.

> University project · Sprint 1 delivery: October 27, 2026 · Backend: [HoDoWo/Backend](https://github.com/HoDoWo/Backend)

## Main flow

Take a photo → Identify the appliance → Ask the AI → Model-specific answer → Visual guide → Fix it

## Features

### Sprint 1 · Core AI engine
- Appliance identification and control panel recognition
- Natural-language Q&A grounded in technical manuals (RAG)
- Error code translation
- Step-by-step visual guides with image overlays
- Favorites page for saved appliances and guides
- AI response rating
- Off-peak electricity tariff tips

### Sprint 2 · Marketplace and community
- Spare parts replacement catalog, linked to the AI diagnosis
- Second-hand marketplace inside the app
- Official sellers directory, with partner companies recommending their products
- Model-specific discussion forums

### Sprint 3 · Advanced features
- ARCore real-time repair overlays
- Speech-to-text voice queries
- Energy consumption calculator
- Web application

## Ideas under consideration

These are not committed to the current sprints and depend on feasibility and time:

- Detection of "broken" appliances, with replacement parts and new device
  recommendations based on partner companies
- Learning from user feedback and forum content to improve the AI answers
- Predictive maintenance and digital appliance profile
- Recycling assistant and sustainability score
- Digital twin of the user's home
- Acoustic analysis of appliance noises

## Tech stack

| Layer | Technology |
|-------|------------|
| Mobile client | Android · Kotlin · Jetpack Compose · CameraX |
| Backend | Python · FastAPI (serverless on Vercel) |
| AI | LangChain multi-step agent (product identification, information retrieval, image generation) · OpenRouter · RAG over appliance manuals |
| Data | Firebase (Auth, Firestore, Storage) |

This repository contains the **Android client** only. The backend and the
project documentation live in a separate repository: [HoDoWo/Backend](https://github.com/HoDoWo/Backend).

## Repository structure

| Path | Content |
|------|---------|
| `App-HoDoWo/` | Android app (Kotlin + Jetpack Compose) |
| `burndown/` | Burndown chart data |
| `docs/` | Generated project charts (Gantt comparison) |
| `scripts/` | Scripts used by the workflows |
| `.github/` | CI workflows (SonarQube, Detekt, Burndown, Earned Value, Gantt) and PR template |

## Getting started

### Requirements

- Android Studio (latest stable)
- JDK 17
- An Android device or emulator

### Setup

1. Clone the repository:
```bash
   git clone https://github.com/HoDoWo/app-kotlin.git
```
2. Open the `App-HoDoWo/` folder in Android Studio.
3. Add your own `google-services.json` to `App-HoDoWo/app/` (not tracked by Git).
4. Copy `App-HoDoWo/local.properties.template` to `App-HoDoWo/local.properties`
   and fill in the values below (the file is ignored by Git). At minimum set
   `backend.url` (for example `backend.url=https://backend-howdowo.vercel.app`).
5. Sync Gradle and run the app.

#### `local.properties`

| Property | What it is | Without it |
|----------|------------|------------|
| `backend.url` | URL of the backend (see the backend repository) | The app runs with the offline demo service |
| `google.web.client.id` | OAuth **web** client ID of the Firebase project (Firebase console → Authentication → Sign-in method → Google → Web client ID) | Gradle falls back to the web client of `google-services.json`; if there is none the app explains that Google sign-in is not configured in this build |

`google.web.client.id` is read at build time into `BuildConfig.GOOGLE_WEB_CLIENT_ID`.
When the property is missing, Gradle reads it from the OAuth **web** client
(`client_type: 3`) listed in `google-services.json`, so enabling Google in the
Firebase console and downloading the updated file is enough. If neither has a
web client (Google sign-in not enabled in the project yet) the value is empty
in CI too, and the app explains that Google sign-in is not configured in this
build instead of failing.

#### Enabling Google sign-in in the Firebase project

The app cannot create the OAuth client itself: it has to exist in the Firebase
project. In the [Firebase console](https://console.firebase.google.com/project/howdowo/authentication/providers):

1. **Authentication → Sign-in method → Add new provider → Google → Enable**.
   The console creates the OAuth *web* client of the project.
2. **Project settings → Your apps → `com.hodowo` → Add fingerprint**: add the
   SHA-1 (and SHA-256) of the keystore used to sign the app (`./gradlew signingReport`).
   Google checks it when the account picker returns the ID token.
3. Download the new `google-services.json` to `App-HoDoWo/app/`.
4. Rebuild. Gradle picks its `client_type: 3` client up automatically, so no
   `local.properties` change is needed.

## Contributing

- `main` is protected: direct pushes are not allowed.
- One branch per task, named after the backlog ID, e.g. `feature/PB-01-F-01`.
- Every change goes through a Pull Request with at least one approval and passing checks.

See [CONTRIBUTING](CONTRIBUING) for details.

## Project tracking

- Burndown chart and Earned Value report (PV, EV, AC, EAC, ETC, VAC, CPI, SPI), updated every day
  by GitHub Actions on the [`burndown-data`](../../tree/burndown-data) branch.
- Code quality: [SonarCloud](https://sonarcloud.io/project/overview?id=HoDoWo_app-kotlin).

## Team

Anna Marín Nuño · Oriol Ferrer López · Davide Pignataro · Francesc Xavier Sierra Raja
