# Bilejack AI

A native Android app that acts as a two-way SMS relay to communicate with LLM APIs via OpenRouter. It's designed for enabling AI chat capabilities on dumbphones via SMS.

I made this app for my mandatory military service (in [Bilecik, Turkey](https://www.google.com/maps/place/Bilecik+Jandarma+E%C4%9Fitim+Alay+Komutanl%C4%B1%C4%9F%C4%B1/@40.153232,29.9720952,17z/data=!4m16!1m9!3m8!1s0x14cb8fc3ac4f70c3:0xb8462eaffb3049ac!2zQmlsZWNpayBKYW5kYXJtYSBFxJ9pdGltIEFsYXkgS29tdXRhbmzEscSfxLE!8m2!3d40.153232!4d29.9746701!9m1!1b1!16s%2Fg%2F11txrhk7wz!3m5!1s0x14cb8fc3ac4f70c3:0xb8462eaffb3049ac!8m2!3d40.153232!4d29.9746701!16s%2Fg%2F11txrhk7wz?hl=tr&entry=ttu&g_ep=EgoyMDI1MDkwMy4wIKXMDSoASAFQAw%3D%3D)), where we were only allowed to use dumbphones.

It helped us to settle a lot of stupid debates... 🫠

## 🚀 Features

- **OpenRouter Integration**: Unified LLM gateway supporting multiple models (Perplexity, OpenAI, etc.)
- **Real-time Web Search**: Models can search the web for current information
- **Phone Number Whitelist**: Only allow SMS from specific phone numbers for security
- **Model Selection**: Choose from multiple available LLM models
- **Automatic SMS Processing**: Receives SMS messages and sends them to LLM
- **Smart Response Splitting**: Automatically splits long LLM responses into multiple SMS messages
- **Background Service**: Runs reliably in the background with foreground service
- **Error Handling**: Robust error handling with automatic error SMS responses
- **Simple UI**: Girlfriend-friendly interface with big buttons and clear status indicators
- **Auto-start**: Automatically starts on device boot

## 📱 Requirements

- Android device with API level 36+ (Android 14+)
- Active SIM card with SMS capabilities
- Internet connection (WiFi or mobile data)
- OpenRouter API key (get one at [openrouter.ai](https://openrouter.ai))

## 🛠️ Installation

1. **Clone and Build**:
   ```bash
   git clone https://github.com/altaywtf/bilejack-ai.git
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

1. **Configure API Key**:
   - Edit `app/src/main/res/values/config.xml`
   - Replace `OPENROUTER_API_KEY` with your actual OpenRouter API key
   - Replace `ALLOWED_PHONE_NUMBERS` with comma-separated phone numbers (e.g., `+1234567890,+0987654321`)

2. **Build and Install**:
   - Build the app: `./gradlew assembleDebug`
   - Install: `adb install app/build/outputs/apk/debug/app-debug.apk`

3. **Test Connection**:
   - Open the app
   - Tap "🧪 Test LLM" to verify OpenRouter connection
   - Check status indicators for LLM API and network connectivity

4. **Configure Whitelist** (Optional):
   - Add allowed phone numbers through the app UI
   - Or manage them in the config file

5. **Select Model** (Optional):
   - Choose from available models: Perplexity Sonar, OpenAI GPT-4o, etc.
   - Default is Perplexity Sonar with web search capabilities

## ⚙️ Configuration

The app uses `app/src/main/res/values/config.xml` for configuration:

```xml
<!-- OpenRouter Configuration -->
<string name="openrouter_api_key">OPENROUTER_API_KEY</string>
<string name="openrouter_system_prompt">You are an SMS assistant...</string>
<string name="openrouter_available_models">perplexity/sonar,perplexity/sonar-pro,...</string>
<string name="openrouter_default_model">perplexity/sonar</string>
<string name="openrouter_web_search_context_size">medium</string>

<!-- Security -->
<string name="allowed_phone_numbers">ALLOWED_PHONE_NUMBERS</string>

<!-- SMS Settings -->
<integer name="sms_max_length">140</integer>
<integer name="sms_chunk_delay_ms">2000</integer>
<integer name="service_max_retries">5</integer>
<integer name="service_retry_delay_ms">500</integer>
```

**Required Changes Before Building:**
1. Replace `OPENROUTER_API_KEY` with your actual OpenRouter API key
2. Replace `ALLOWED_PHONE_NUMBERS` with comma-separated phone numbers

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

- **Status Indicators**: LLM API and network connectivity status
- **Whitelist Summary**: Shows number of allowed phone numbers
- **Model Summary**: Shows currently selected LLM model
- **🔄 Restart Service**: Restart background processing
- **🧪 Test LLM**: Test OpenRouter API connectivity
- **Add Phone Number**: Add numbers to whitelist
- **Manage Whitelist**: View and delete whitelisted numbers
- **Clear Whitelist**: Remove all whitelisted numbers
- **Select Model**: Choose from available LLM models

## 🔧 Technical Details

### Architecture
- **MainActivity**: UI and controls with whitelist/model management
- **SmsReceiver**: Handles incoming SMS broadcasts
- **SmsRelayService**: Background processing and OpenRouter communication
- **OpenRouterProvider**: OpenRouter API communication with web search
- **OpenRouterModelManager**: Model selection and configuration
- **WhitelistedNumbersManager**: Phone number whitelist management
- **BootReceiver**: Auto-start on device boot

### Supported Models
- **Perplexity Sonar**: Real-time web search capabilities
- **Perplexity Sonar Pro**: Enhanced reasoning with web search
- **Perplexity Sonar Reasoning**: Advanced reasoning model
- **OpenAI GPT-4o Search Preview**: GPT-4o with web search
- **OpenAI GPT-4o Mini Search Preview**: Lightweight GPT-4o with search

### Message Flow
1. SMS received → SmsReceiver → Check whitelist
2. If whitelisted → SmsRelayService processes message
3. Send to OpenRouter API with selected model → Get response
4. Split response if needed → Send SMS replies
5. Log results and update status

### Error Handling
- Network failures: Automatic retry with backoff
- API errors: Send error message via SMS
- SMS sending failures: Log for manual review
- Whitelist violations: Silent ignore (security feature)

## 🔒 Security Notes

- **API Key Security**: Stored in config file (replace constants before building)
- **Phone Number Whitelist**: Only whitelisted numbers can trigger responses
- **No External Servers**: All processing happens on device
- **OpenRouter Integration**: Uses OpenRouter as unified LLM gateway
- **Physical Security**: Consider device physical security for API key protection

## 🐛 Troubleshooting

### App not receiving SMS
- Check SMS permissions granted
- Verify SIM card is active
- Check if another SMS app is set as default

### OpenRouter responses not sent
- Test LLM connection in app
- Check internet connectivity
- Verify API key is correctly set in config.xml
- Check for OpenRouter API rate limits
- Ensure phone number is whitelisted

### Service stops working
- Disable battery optimization for app
- Use "Restart Service" button
- Check device storage space
- Reboot device if needed

## 💡 Tips

- **Configuration**: Always replace `OPENROUTER_API_KEY` and `ALLOWED_PHONE_NUMBERS` in config.xml before building
- **Model Selection**: Perplexity Sonar is recommended for real-time web search capabilities
- **Whitelist Management**: Use the app UI to manage phone numbers or edit config.xml directly
- **Power Management**: Keep device charged and disable battery optimization for the app
- **Cost Monitoring**: Monitor both SMS costs and OpenRouter API usage
- **Testing**: Test thoroughly with whitelisted numbers before deploying

## 📊 Development

### Linting & Formatting
```bash
./lint.sh               # Run all checks and auto-fix
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

**⚠️ Important**: This app will incur costs for both SMS messages and OpenRouter API usage. Monitor usage carefully and set appropriate limits. Always configure the whitelist to prevent unauthorized usage. 
