# YT-DLP Android (v0.1.0)

A powerful, standalone Android application for downloading media from 1,000+ platforms using `yt-dlp` and `ffmpeg`, designed for technical users and developers.

## The Mission (Proof of Concept)
This app was created as a **technical proof of concept** and a personal learning project. The goal was to demonstrate that it is possible to:
- Port and run **native Linux libraries and binaries** (like Python, FFmpeg, and GNU libs) inside a standalone Android app.
- Execute complex Linux-based workflows without requiring the full Termux app or environment to be installed.
- Bridge the gap between Android's Java/Kotlin world and the powerful CLI tools of the Linux ecosystem.

It serves as a template for anyone interested in making any Linux library or program "portable" for Android. I am happy to share this project with the community as a result of this deep-dive into Android's low-level capabilities.

## New in v0.1.0
- **Advanced Backend View:** Real-time Logcat integration to monitor system errors and debug download issues directly in the app.
- **Improved File Sharing:** Full `FileProvider` support for safe media sharing and Android 10+ MediaStore integration for EPERM-free exports.
- **Filename Sanitization:** Automatic removal of illegal characters and spaces from filenames to ensure compatibility with all file systems.
- **GPLv3 Licensed:** Now fully aligned with the Open Source philosophy of the Termux environment.

## Features
- **Dynamic Quality Selection:** Fetch metadata and choose specific resolutions (up to 4K/60fps).
- **Audio Extraction:** High-quality MP3 (up to 320kbps) download support with dedicated player view.
- **Smart Library:** Integrated media manager with automatic thumbnail previews and one-click playback.
- **Self-Bootstrapping:** Automatically downloads and updates the latest `yt-dlp` binary from GitHub.
- **Terminal Access:** Built-in shell interface to run manual commands like `python3 --version` or custom `yt-dlp` flags.

## Technical Architecture
This project uses a unique manual toolchain without Gradle:
- **Build System:** Custom `build.sh` using `aapt2`, `javac`, `d8`, and `apksigner`.
- **Backend:** Native Python 3.12 environment with over 50 shared libraries sourced from Termux.
- **Binary Integration:** Optimized FFmpeg build for stream merging and thumbnail processing.

## License
This project is licensed under the **GNU General Public License v3.0 (GPLv3)**. See [LICENSE.md](LICENSE.md) for details, including third-party licenses for yt-dlp, FFmpeg, and Python.

## Legal Disclaimer
This software is for **educational and personal use only**. By using this tool, you agree to respect international copyright laws and use the application at your own risk. The developer assumes no liability for misuse.

