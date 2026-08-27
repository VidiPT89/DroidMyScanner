# 📄 DroidMyScanner

> An elegant Android document scanner with manual and on-device AI-powered editing.

[Report Bug](https://github.com/VidiPT89/DroidMyScanner/issues) · [Request Feature](https://github.com/VidiPT89/DroidMyScanner/issues)

## ✨ Features

- ✅ Multi-page document scanning with automatic edge detection (ML Kit Document Scanner)
- ✅ On-device text recognition (OCR) with copy-to-clipboard
- ✅ Manual crop with draggable corner handles, 90° rotation
- ✅ Non-destructive filters: Original, Black & White, Grayscale, Auto Enhance
- ✅ Page reordering and deletion within a document
- ✅ Export to PDF or images, with the native share sheet
- ✅ Document management: rename, delete, animated list
- ✅ Dark mode (default), Light mode, or follow system, with in-app toggle
- ✅ Full localization: Portuguese (Portugal) and English, switchable in-app
- ✅ Onboarding flow for first-time users

## 🛠️ Tech Stack

| Category | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose (Material 3) |
| Architecture | MVVM (`ViewModel` + `StateFlow`) |
| Scanning | ML Kit Document Scanner |
| OCR | ML Kit Text Recognition |
| Persistence | Jetpack DataStore |
| PDF Export | `android.graphics.pdf.PdfDocument` |
| Minimum SDK | 24 |

## 🚀 Quick Start

### Prerequisites

- Android Studio (latest stable)
- A physical Android device is recommended for testing (camera scanning)

### Installation

1. Clone the repository
   ```bash
   git clone https://github.com/VidiPT89/DroidMyScanner.git
   ```
2. Open the project in Android Studio and let it sync Gradle
3. Run on a physical device or emulator with Google Play services

## 📖 Usage

1. Tap the scan button and capture your document — edges are detected automatically
2. Edit each page: crop, rotate, or apply a filter
3. Extract text from any page with the OCR action
4. Export the finished document as a PDF or images, or share it directly

## 🧪 Testing

```bash
./gradlew test
```

## 📄 License

Distributed under the MIT License. See [LICENSE](LICENSE) for details.

## 👨‍💻 Author

**David Arsénio Martins**
🌐 Website: [ividi.dev](https://ividi.dev)
🐙 GitHub: [@VidiPT89](https://github.com/VidiPT89)

## 🤝 Contributing

Issues and feature requests are welcome. Feel free to check the [issues page](https://github.com/VidiPT89/DroidMyScanner/issues).

---

<p align="center">Developed by <a href="https://ividi.dev">David Arsénio Martins</a></p>
<p align="center">If you like this project, consider giving it a ⭐</p>
