# Crow Theatron

A comprehensive Android video player and media management application with advanced features for video organization, enhancement, and playback.

## Description

Crow Theatron is a feature-rich Android application designed for video enthusiasts who want a powerful yet intuitive media player. The app combines advanced video playback capabilities with sophisticated media management, including folder scanning, video enhancement, playback memory, and a modern user interface.

## Features

### Core Functionality
- **Advanced Video Playback**: Smooth video streaming using ExoPlayer with comprehensive playback controls
- **Local Database**: SQLite database for storing video metadata, preferences, and playback history
- **Folder Scanning**: Automatic detection and indexing of video files from device storage
- **Video Library**: Organized video collection with metadata management

### Enhanced Features
- **Video Enhancement**: Built-in video enhancement tools for improved viewing experience
- **Playback Memory**: Resume videos from where you left off across sessions
- **Multiple UI Themes**: Modern, responsive interface with multiple activity layouts
- **Settings Management**: Comprehensive user preferences and app configuration

### User Interface
- **Main Activity**: Central hub for navigation and quick access to features
- **Player Activity**: Full-featured video player with advanced controls
- **Library View**: Organized video browsing and management
- **Explore Mode**: Discover and browse video content
- **Settings Screen**: Comprehensive app configuration options

## Technical Stack

- **Language**: Kotlin
- **Platform**: Android (API 26 - 36)
- **Database**: SQLite with custom database helper
- **Media Player**: ExoPlayer (Media3) for advanced video playback
- **Architecture**: Android MVC pattern with ViewBinding
- **Build System**: Gradle with Kotlin DSL
- **UI Framework**: Android Views with Material Design components
- **Coroutines**: Kotlin Coroutines for asynchronous operations

## Project Structure

```
CrowTheatron/
├── app/
│   ├── src/main/
│   │   ├── java/com/crowtheatron/app/
│   │   │   ├── data/           # Database and data management
│   │   │   │   ├── AppPrefs.kt          # Application preferences
│   │   │   │   ├── CrowDbHelper.kt      # SQLite database helper
│   │   │   │   ├── EnhancementMode.kt   # Video enhancement settings
│   │   │   │   ├── FolderScanner.kt     # Media folder scanning
│   │   │   │   ├── VideoEntity.kt       # Video data model
│   │   │   │   └── VideoRepository.kt   # Data access layer
│   │   │   ├── enhancement/    # Video enhancement features
│   │   │   ├── explore/         # Content discovery
│   │   │   ├── folder/          # Folder management
│   │   │   ├── library/         # Video library management
│   │   │   ├── main/            # Main activity
│   │   │   ├── memory/          # Playback memory
│   │   │   ├── player/          # Video player
│   │   │   ├── settings/        # App settings
│   │   │   ├── splash/          # Splash screen
│   │   │   ├── ui/              # UI components and utilities
│   │   │   └── util/            # Utility classes
│   │   ├── res/
│   │   │   ├── layout/          # XML layout files
│   │   │   │   ├── activity_main.xml
│   │   │   │   ├── activity_player.xml
│   │   │   │   ├── activity_library.xml
│   │   │   │   ├── activity_explore.xml
│   │   │   │   ├── activity_settings.xml
│   │   │   │   ├── activity_splash.xml
│   │   │   │   ├── activity_folder_select.xml
│   │   │   │   ├── activity_playback_memory.xml
│   │   │   │   ├── activity_video_enhancement.xml
│   │   │   │   └── item_*.xml   # List item layouts
│   │   │   └── ...              # Other resources (drawables, values, etc.)
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── settings.gradle.kts
└── README.md
```

## Key Components

### Data Layer
- **VideoEntity.kt**: Comprehensive data model for video information including metadata
- **CrowDbHelper.kt**: Advanced SQLite database helper for managing video data, preferences, and playback history
- **VideoRepository.kt**: Data access layer implementing repository pattern
- **FolderScanner.kt**: Automated media file discovery and indexing
- **AppPrefs.kt**: Application-wide preferences management

### Feature Modules
- **Player Module**: Advanced video player with ExoPlayer integration
- **Library Module**: Video collection management and organization
- **Enhancement Module**: Video quality and playback enhancement tools
- **Memory Module**: Playback position persistence and resume functionality
- **Explore Module**: Content discovery and browsing features

### UI Components
- **Activities**: Main, Player, Library, Settings, Explore, Splash, and more
- **Custom Views**: Specialized UI components for video cards and controls
- **Layouts**: Comprehensive XML layouts with Material Design principles

## Getting Started

### Prerequisites

- Android Studio Hedgehog or later
- Android SDK (API level 26 or higher)
- Kotlin 1.9+
- Java 17

### Installation

1. Clone this repository:
   ```bash
   git clone https://github.com/Chris654Cajes/Crow-Theatron-Android.git
   ```

2. Open the project in Android Studio

3. Sync the project with Gradle files

4. Build and run the application on an emulator or physical device

### Build Configuration

The project uses modern Android Gradle configuration with Kotlin DSL. Key requirements:
- **Compile SDK**: 36
- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 36 (Android 14)
- **Java Version**: 17
- **Kotlin Target JVM**: 17

### Dependencies

Key dependencies include:
- **ExoPlayer (Media3)**: Advanced media playback
- **AndroidX Libraries**: Core Android components
- **Material Design**: UI components and theming
- **Kotlin Coroutines**: Asynchronous programming
- **Lifecycle Components**: Android lifecycle management
- **DocumentFile**: File system access

## Database Schema

The app uses a comprehensive SQLite database to store:
- **Video Metadata**: File paths, duration, resolution, format information
- **Playback History**: Recently watched videos with timestamps
- **User Preferences**: App settings and customization options
- **Playback Memory**: Video positions for resume functionality
- **Enhancement Settings**: Video enhancement preferences per video

## Contributing

We welcome contributions to Crow Theatron! Please follow these guidelines:

1. **Fork the repository** and create a feature branch
2. **Follow the existing code style** and architecture patterns
3. **Test thoroughly** on both emulators and physical devices
4. **Update documentation** for any new features
5. **Submit a pull request** with a clear description of changes

### Development Guidelines
- Use Kotlin coding conventions
- Follow Android architecture best practices
- Ensure backward compatibility with Android 8.0+
- Add appropriate error handling and user feedback
- Test with various video formats and file sizes

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Contact

For questions, bug reports, or feature requests:
- **Issues**: Open an issue on GitHub
- **Discussions**: Use GitHub Discussions for general questions
- **Email**: [Your contact email if applicable]

## App Features Showcase

### 🎬 Video Player
- Support for multiple video formats (MP4, AVI, MKV, etc.)
- Gesture controls for brightness, volume, and seek
- Picture-in-picture mode support
- Subtitle support

### 📚 Library Management
- Automatic video detection and indexing
- Folder-based organization
- Metadata extraction and display
- Search and filter capabilities

### 🎨 Video Enhancement
- Brightness and contrast adjustments
- Saturation and hue controls
- Playback speed adjustment
- Audio enhancement options

### 🧠 Smart Features
- Resume playback from last position
- Recently watched videos tracking
- Favorite videos management
- Playback history with timestamps

---

**Note**: This is an advanced Android project that requires appropriate development environment setup. The app is designed for modern Android devices and takes full advantage of the latest Android features while maintaining backward compatibility.
