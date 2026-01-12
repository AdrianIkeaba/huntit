# Huntit
<img width="256" height="256" alt="Hunt it(1)" src="https://github.com/user-attachments/assets/830f0cca-b6eb-47c1-9f3d-0d07e4e8d774" />

## Overview

Huntit is a social scavenger hunt game where friends compete to find and snap items before time runs out. 
This cross-platform app is built with Kotlin Multiplatform and Compose, providing a seamless experience on both Android and iOS devices.

Players can create game rooms, invite friends, and engage in time-based challenges where they need to take photos of specified items. The app includes features like real-time leaderboards, game history, and customizable game settings.

## Technologies

### Core Platform & Languages
- **Kotlin Multiplatform** - Shared code across platforms
- **Android** - Native Android support
- **iOS** - Native iOS support using Kotlin/Native

### UI Framework
- **Jetpack Compose Multiplatform** - Modern declarative UI toolkit
- **Compose Material3** - Material Design 3 components
- **Compose Hot Reload** - For faster development cycles

### Architecture & State Management
- **MVVM Architecture** - Using ViewModels
- **Kotlin Coroutines & Flow** - For reactive state management
- **Navigation Compose** - For app navigation
- **Koin** - For dependency injection across platforms

### Backend & Networking
- **Supabase** - Backend as a Service
  - Authentication, Real-time updates, Storage, Functions, and PostgreSQL database
- **Ktor Client** - Multiplatform HTTP client

### Data Storage & Persistence
- **Room Database** - SQL database (Android)
- **SQLite Bundled** - Database engine
- **Multiplatform Settings** - Key-value storage
- **Datastore & Preferences** - Typed preferences storage

### Media & Assets
- **Coil** - Image loading library
- **CameraX** - Camera API (Android)
- **Media3 ExoPlayer** - Audio/video playback (Android)

## Features

- **User Authentication** - Create accounts and personalize profiles
- **Create Game Rooms** - Set up custom game sessions with specific rules
- **Join Game Rooms** - Enter existing games via room code or browse public games
- **Real-time Game Play** - Compete with friends in real-time
- **Photo Challenges** - Take photos of items during scavenger hunts
- **Leaderboards** - Track scores and rankings
- **Game History** - View past games and performance
- **Sound Settings** - Customize background music and sound effects
- **Cross-platform** - Same experience on both Android and iOS

## Prerequisites

Before getting started, ensure you have the following installed:

- **Android Studio Arctic Fox (2022.3.1)** or newer
- **Xcode 14** or newer (for iOS development)
- **JDK 11** or newer
- **Kotlin 2.2.21** or newer
- **Git**

## Setup & Installation

### 1. Clone the Repository

```bash
git clone https://github.com/AdrianIkeaba/huntit.git
cd huntit
```

### 2. Android Setup

1. Open the project in Android Studio
2. Sync Gradle files
3. Run the app on an Android emulator or physical device:
   - Select the `composeApp` module
   - Choose an Android target device
   - Click Run

### 3. iOS Setup

1. Build the Kotlin framework for iOS:

```bash
./gradlew :composeApp:packForXcode
```

2. Open the Xcode project in the `iosApp` directory
3. Select your target device
4. Build and run the project

## Running the App

### Android

1. Open the project in Android Studio
2. Select the `composeApp` module
3. Choose an Android device or emulator
4. Click the Run button (▶️)

### iOS

1. First, build the Kotlin framework:

```bash
./gradlew :composeApp:packForXcode
```

2. Open the Xcode project in the `iosApp` directory
3. Select your simulator or device
4. Click the Run button in Xcode

## Key Features & How to Try Them

### User Authentication

1. Launch the app
2. Create a new account or sign in with existing credentials
3. Customize your profile by tapping on your avatar

### Creating a Game Room

1. From the home screen, tap "CREATE A GAME ROOM"
2. Set game parameters:
   - Room name
   - Maximum players
   - Round duration
   - Number of rounds
   - Game theme
   - Room visibility (public/private)
3. Tap "CREATE GAME ROOM" to start the lobby

### Joining a Game

1. From the home screen, tap "JOIN A GAME ROOM"
2. Choose to join by code or browse public games
3. If joining by code, enter the 6-digit room code
4. If browsing, select from available public game rooms

### Playing a Game

1. In the lobby, wait for the host to start the game
2. Each round, you'll be given an item to find
3. Tap the camera button to take a photo of the item
4. Submit your photo before time runs out
5. At the end of each round, see results and scores

### Sound Settings

1. Tap the music icon in the top right of the home screen
2. Toggle background music and sound effects
3. Adjust volume levels using the sliders

### Viewing Game History

1. From the home screen, tap "VIEW PAST GAMES"
2. Browse through your game history
3. Tap on a past game to view detailed results

## Project Structure

```
Huntit/
├── composeApp/                    # Main shared module
│   ├── src/
│   │   ├── androidMain/           # Android-specific code
│   │   ├── commonMain/            # Shared code across platforms
│   │   │   ├── kotlin/
│   │   │   │   ├── di/            # Dependency injection
│   │   │   │   ├── data/          # Data layer (repositories, models)
│   │   │   │   ├── ui/            # UI components and screens
│   │   │   │   │   ├── screens/   # App screens
│   │   │   │   │   ├── components/# Reusable UI components
│   │   │   │   │   └── theme/     # Styling and themes
│   │   │   │   ├── utils/         # Utilities and helpers
│   │   │   │   └── navigation/    # Navigation graphs
│   │   │   └── resources/         # Shared resources
│   │   └── iosMain/               # iOS-specific code
│   └── build.gradle.kts           # Module build configuration
├── iosApp/                        # iOS app module
├── gradle/                        # Gradle configuration
│   └── libs.versions.toml         # Dependency versions
├── build.gradle.kts               # Project build configuration
└── settings.gradle.kts            # Project settings
```

## Troubleshooting

### Common Issues

1. **Gradle Sync Failed**
   - Ensure you have the correct Kotlin and Gradle versions
   - Check your `local.properties` file has the correct Supabase credentials

2. **iOS Build Errors**
   - Run `./gradlew clean` and rebuild the project
   - Ensure you've run `./gradlew :composeApp:packForXcode` before opening Xcode

3. **Runtime Crashes**
   - Check Supabase connectivity
   - Ensure permissions are granted for camera access

