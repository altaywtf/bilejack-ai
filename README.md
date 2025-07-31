# Bilejack AI

A native Android app that acts as a two-way SMS relay to communicate with the OpenAI ChatGPT API. Perfect for enabling AI chat capabilities on dumbphones via SMS.

## 🚀 Features

- **Automatic SMS Processing**: Receives SMS messages and sends them to ChatGPT
- **Smart Response Splitting**: Automatically splits long GPT responses into multiple SMS messages
- **Background Service**: Runs reliably in the background with foreground service
- **Message History**: Track all conversations with timestamps and status
- **Error Handling**: Robust error handling with automatic error SMS responses
- **Simple UI**: Girlfriend-friendly interface with big buttons and clear status indicators
- **Auto-start**: Automatically starts on device boot

## 📱 Requirements

- Android device with API level 36+ (Android 14+)
- Active SIM card with SMS capabilities
- Internet connection (WiFi or mobile data)
- OpenAI API key

## 🛠️ Installation

1. **Clone and Build**:
   ```bash
   git clone <repository-url>
   cd bilejack-ai
   ./gradlew assembleDebug
   ```

2. **Install APK**:
   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

3. **Grant Permissions**:
   - SMS permissions (send/receive)
   - Phone permissions
   - Allow in background/disable battery optimization

## ⚙️ Setup

1. **Set API Key**:
   - Open the app
   - Tap "🔑 Set API Key"
   - Enter your OpenAI API key

2. **Test Connection**:
   - Tap "🧪 Test GPT"
   - Verify successful connection

3. **Send Test SMS**:
   - Send an SMS to the device from another phone
   - Check the message history in the app

## 🎯 Usage

### For End Users (Dumbphone Users)
- Simply send SMS to the relay device phone number
- Receive AI responses via SMS
- No setup required on sender side

### For App Operator
- Monitor message history and statistics
- Restart service if needed
- Clear logs periodically
- Check error messages

## 🎛️ UI Controls

- **📩/📤/❌ Stats**: Shows received, sent, and error counts
- **Status Indicators**: GPT API and network connectivity status
- **🔄 Refresh**: Update statistics and message list
- **🔄 Restart Service**: Restart background processing
- **🔑 Set API Key**: Configure OpenAI API key
- **🧪 Test GPT**: Test API connectivity
- **🧹 Clear Log**: Delete all message history

## 🔧 Technical Details

### Architecture
- **MainActivity**: UI and controls
- **SmsReceiver**: Handles incoming SMS broadcasts
- **SmsRelayService**: Background processing and GPT communication
- **GptClient**: OpenAI API communication
- **Room Database**: Message storage and statistics

### Message Flow
1. SMS received → SmsReceiver → Database
2. Service picks up unprocessed messages
3. Send to OpenAI API → Get response
4. Split response if needed → Send SMS replies
5. Update database with results

### Error Handling
- Network failures: Automatic retry with backoff
- API errors: Send error message via SMS
- SMS sending failures: Log for manual review

## 🔒 Security Notes

- API key stored locally in SharedPreferences
- No external servers involved
- All processing happens on device
- Consider device physical security

## 🐛 Troubleshooting

### App not receiving SMS
- Check SMS permissions granted
- Verify SIM card is active
- Check if another SMS app is set as default

### GPT responses not sent
- Test GPT connection in app
- Check internet connectivity
- Verify API key is correct
- Check for API rate limits

### Service stops working
- Disable battery optimization for app
- Use "Restart Service" button
- Check device storage space
- Reboot device if needed

## 💡 Tips

- Keep device charged and connected to power
- Monitor SMS credit balance
- Test thoroughly before deploying
- Consider setting SMS spending limits
- Backup message history periodically

## 📊 Development

### Linting & Formatting
```bash
./lint.sh              # Run all checks and auto-fix
./gradlew ktlintCheck   # Check code style
./gradlew ktlintFormat  # Auto-fix formatting
```

### Building
```bash
./gradlew assembleDebug    # Debug build
./gradlew assembleRelease  # Release build
```

## 📄 License

MIT License - See LICENSE file for details.

---

**⚠️ Important**: This app will incur costs for both SMS messages and OpenAI API usage. Monitor usage carefully and set appropriate limits. 
