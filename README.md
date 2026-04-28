# Crow Theatron

An Android application for video playback and management.

## Description

Crow Theatron is an Android app that provides video playback functionality with local database storage for video metadata. The app allows users to manage and watch videos with a clean, intuitive interface.

## Features

- **Video Playback**: Smooth video streaming and playback
- **Local Database**: SQLite database for storing video metadata
- **Video Management**: Add, organize, and manage video collections
- **Player Activity**: Dedicated player interface with playback controls
- **Data Persistence**: Local storage of video information and preferences

## Technical Stack

- **Language**: Kotlin
- **Platform**: Android
- **Database**: SQLite (via Android Room or SQLiteOpenHelper)
- **Architecture**: Android MVC/MVVM pattern
- **Build System**: Gradle

## Project Structure

```
app/
├── src/main/
│   ├── java/com/crowtheatron/app/
│   │   ├── data/           # Database entities and helpers
│   │   │   ├── VideoEntity.kt
│   │   │   └── CrowDbHelper.kt
│   │   ├── player/         # Player activity and related components
│   │   │   └── PlayerActivity.kt
│   │   └── ...             # Other app components
│   ├── res/
│   │   ├── layout/         # XML layout files
│   │   │   └── activity_player.xml
│   │   └── ...             # Other resources
│   └── AndroidManifest.xml
├── build.gradle
└── ...                     # Other project files
```

## Key Components

### Data Layer
- **VideoEntity.kt**: Data model for video information
- **CrowDbHelper.kt**: SQLite database helper for managing video data

### UI Layer
- **PlayerActivity.kt**: Main video player interface
- **activity_player.xml**: Layout for the player screen

## Getting Started

### Prerequisites

- Android Studio Arctic Fox or later
- Android SDK (API level 21 or higher)
- Kotlin 1.5+

### Installation

1. Clone this repository:
   ```bash
   git clone <repository-url>
   ```

2. Open the project in Android Studio

3. Sync the project with Gradle files

4. Build and run the application on an emulator or physical device

### Build Configuration

The project uses standard Android Gradle configuration. Ensure you have:
- Android SDK installed
- Proper build tools version
- Required dependencies in `build.gradle`

## Database Schema

The app uses a local SQLite database to store video metadata including:
- Video file paths
- Video metadata (duration, resolution, etc.)
- User preferences
- Playback history

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Test thoroughly
5. Submit a pull request

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Contact

For questions or support, please open an issue in the repository.

---

**Note**: This is an Android project and requires appropriate development environment setup to build and run.
