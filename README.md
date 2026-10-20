# HoDoWo

**How does it work?** A utility to guide you when using home appliances.

Take a photo of the appliance or its control panel, ask a question in natural
language and get model-specific help with a step-by-step visual guide.

> University project · Sprint 1 delivery: October 27, 2026

## Main flow

Take a photo → Identify the appliance → Ask the AI → Model-specific answer → Visual guide → Fix it

## Features

### Sprint 1 · Core AI engine (in progress)
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
project documentation live in a separate repository: [LINK-TO-BACKEND-REPO](#).

## Repository structure

| Path | Content |
|------|---------|
| `App-HoDoWo/` | Android app (Kotlin + Jetpack Compose) |
| `burndown/` | Burndown chart data |
| `.github/` | CI workflows (Sonar, Detekt, burndown) and PR template |

## Getting started

### Requirements

- Android Studio (latest stable)
- JDK 17
- An Android device or emulator

### Setup

1. Clone the repository:
```bash
   git clone https://github.com/Anna-Marin/Howdowo.git
```
2. Open the `App-HoDoWo/` folder in Android Studio.
3. Add your own `google-services.json` to `App-HoDoWo/app/` (not tracked by Git).
4. Copy `App-HoDoWo/local.properties.template` to `App-HoDoWo/local.properties`
   and fill in the values below (the file is ignored by Git).
5. Sync Gradle and run the app.

#### `local.properties`

| Property | What it is | Without it |
|----------|------------|------------|
| `backend.url` | URL of the backend (see the backend repository) | The app runs with the offline demo service |
| `google.web.client.id` | OAuth **web** client ID of the Firebase project (Firebase console → Authentication → Sign-in method → Google → Web client ID) | The "Continue with Google" button is disabled and the app answers that Google sign-in is not configured in this build |

`google.web.client.id` is read at build time into `BuildConfig.GOOGLE_WEB_CLIENT_ID`,
so it is empty in CI (where `local.properties` does not exist) and the Google
button simply stays disabled. Alternatively, add the OAuth web client to the
Firebase project and use the `default_web_client_id` string that the
`google-services` plugin generates from `google-services.json`.

## Contributing

- `main` is protected: direct pushes are not allowed.
- One branch per task, named after the backlog ID, e.g. `feature/PB-01-F-01`.
- Every change goes through a Pull Request with at least one approval and passing checks.

See [CONTRIBUTING.md](CONTRIBUTING.md) for details.

## Team

Anna Marín Nuño · Oriol Ferrer López · Davide Pignataro · Francesc Xavier Sierra Raja
