# Conversational AI

An offline AI chat application for Android powered by MediaPipe LLM Inference. Chat with AI models entirely on-device - no internet required after downloading models.

![Android](https://img.shields.io/badge/Android-15+-green.svg)
![Kotlin](https://img.shields.io/badge/Kotlin-1.9+-purple.svg)
![MediaPipe](https://img.shields.io/badge/MediaPipe-0.10.29-blue.svg)

## ✨ Features

### 🤖 Multiple AI Models
| Model | Size | Capabilities |
|-------|------|--------------|
| **Smart Text Chat** (Qwen 1.5B) | 1.57 GB | Conversations, coding, math, writing |
| **High-Precision Text AI** (Gemma3 1B) | 1.05 GB | Detailed text reasoning |
| **Versatile Text AI** (Gemma2 2B) | 2.71 GB | General text + **Function Calling** |
| **Mobile Text Edge** (Gemma2 2B) | 2.7 GB | Fast text on limited devices |
| **Ultimate Vision Assistant** (Gemma 3n) | 3.14 GB | **Image analysis** & visual Q&A |
| **Thinking & Reasoning** (DeepSeek R1) | 1.86 GB | Math, logic, chain-of-thought |

### 📷 Vision Support
- Analyze images with the Gemma 3n vision model
- Ask questions about photos
- Image descriptions and visual understanding

### 🔧 Function Calling (Tools)
With the "Versatile Text AI" model, you can:
- **Open apps**: "Open WhatsApp", "Launch YouTube"
- **Browse web**: "Go to google.com", "Search for weather"
- **Make calls**: "Call 123456789"
- **Send SMS**: "Send message to Mom"
- **Set alarms**: "Set alarm for 7am"
- **Set timers**: "Set timer for 5 minutes"
- **Open settings**: "Open WiFi settings"
- **Take photos**: "Take a photo"
- **Play music**: "Play some music"

### 💬 Chat Features
- Conversation history with sessions
- Streaming responses (real-time typing effect)
- Dark theme UI
- Auto-scroll to new messages
- Session management (create, delete, switch)

### ⚙️ Settings
- Customizable system prompt
- Clear chat history
- About & version info

## 📱 Screenshots

*Coming soon*

## 🚀 Getting Started

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or later
- Android device with Android 10+ (API 29+)
- At least 4GB free storage for models

### Installation

1. Clone the repository:
```bash
git clone https://github.com/yourusername/conversational-ai.git
cd conversational-ai
```

2. Open in Android Studio

3. Build and run on your device:
```bash
./gradlew installDebug
```

4. Grant storage permissions when prompted

5. Select a model and download it (requires internet for first download)

6. Start chatting offline!

### Model Download Location
Models are stored in:
```
/storage/emulated/0/Documents/NiqueWrld/models/
```
Models persist after app uninstall.

## 🏗️ Architecture

```
app/
├── src/main/java/com/niquewrld/conversationalai/
│   ├── MainActivity.kt          # Main chat UI
│   ├── MainViewModel.kt         # LLM inference & state management
│   ├── Model.kt                 # Model data class
│   ├── ChatSettingActivity.kt   # Settings screen
│   ├── ChatHistoryActivity.kt   # History screen
│   ├── Room/                    # Database (sessions & messages)
│   ├── service/                 # Model download service
│   ├── tools/                   # Function calling executor
│   └── adapter/                 # RecyclerView adapters
├── src/main/res/
│   ├── layout/                  # XML layouts
│   ├── drawable/                # Icons & backgrounds
│   └── values/                  # Colors, strings, themes
└── src/main/assets/
    └── models.json              # Available models config
```

## 🔧 Tech Stack

- **Language**: Kotlin
- **UI**: Android Views + Material Design
- **AI Inference**: MediaPipe Tasks GenAI
- **Database**: Room
- **Async**: Kotlin Coroutines & Flow
- **Architecture**: MVVM with ViewModel

## 📦 Dependencies

```kotlin
// MediaPipe LLM Inference
implementation("com.google.mediapipe:tasks-genai:0.10.29")
implementation("com.google.mediapipe:tasks-vision:0.10.29")

// Room Database
implementation("androidx.room:room-runtime:2.6.1")
implementation("androidx.room:room-ktx:2.6.1")

// Material Design
implementation("com.google.android.material:material:1.11.0")
```

## 🎯 Performance Tips

- **First response is slower** - model needs to warm up
- **Smaller models = faster responses** - try Gemma3 1B for speed
- **Limit conversation length** - start new sessions for best performance
- **Close other apps** - LLM inference uses significant RAM

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- [MediaPipe](https://developers.google.com/mediapipe) for on-device ML inference
- [Google Gemma](https://ai.google.dev/gemma) for the language models
- [Qwen](https://github.com/QwenLM/Qwen) for the Qwen 2.5 model
- [DeepSeek](https://www.deepseek.com/) for the R1 reasoning model

## 👤 Author

**NiqueWrld**
- Website: [niquewrld.com](https://niquewrld.com)

---

Made with ❤️ for offline AI enthusiasts
