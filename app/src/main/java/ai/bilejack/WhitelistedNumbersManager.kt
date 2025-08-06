package ai.bilejack

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

class WhitelistedNumbersManager(private val context: Context) {
    private val tag = "WhitelistedNumbersManager"
    private val prefs: SharedPreferences = context.getSharedPreferences("bilejack_whitelist", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_WHITELIST = "allowed_phone_numbers"
    }

    init {
        if (getAllowedNumbers().isEmpty()) {
            Log.d(tag, "🔄 Migrating phone numbers from config.xml to SharedPreferences")
            migrateFromConfig()
        }
    }

    private fun migrateFromConfig() {
        try {
            val configNumbers = context.getString(R.string.allowed_phone_numbers)
            if (configNumbers.isNotBlank() && configNumbers != "your_phone_numbers_here") {
                Log.d(tag, "📱 Migrating numbers from config: $configNumbers")
                saveAllowedNumbers(configNumbers)
            } else {
                Log.w(
                    tag,
                    "⚠️ No valid numbers in config.xml - phone numbers will be empty until numbers are added via UI",
                )
            }
        } catch (e: Exception) {
            Log.e(tag, "❌ Error migrating from config", e)
        }
    }

    fun getAllowedNumbers(): List<String> {
        val numbersString = prefs.getString(KEY_WHITELIST, "") ?: ""
        return if (numbersString.isBlank()) {
            emptyList()
        } else {
            numbersString.split(",").map { it.trim() }.filter { it.isNotBlank() }
        }
    }

    fun saveAllowedNumbers(numbersString: String) {
        prefs.edit().putString(KEY_WHITELIST, numbersString).apply()
        Log.d(tag, "💾 Saved phone numbers: $numbersString")
    }

    fun addPhoneNumber(phoneNumber: String): Boolean {
        val trimmed = phoneNumber.trim()
        if (trimmed.isBlank()) {
            Log.w(tag, "❌ Cannot add blank phone number")
            return false
        }

        val currentNumbers = getAllowedNumbers().toMutableList()

        if (currentNumbers.any { normalizePhoneNumber(it) == normalizePhoneNumber(trimmed) }) {
            Log.w(tag, "⚠️ Phone number $trimmed already in allowed numbers")
            return false
        }

        currentNumbers.add(trimmed)
        saveAllowedNumbers(currentNumbers.joinToString(","))
        Log.d(tag, "✅ Added phone number: $trimmed")
        return true
    }

    fun removePhoneNumber(phoneNumber: String): Boolean {
        val trimmed = phoneNumber.trim()
        val currentNumbers = getAllowedNumbers().toMutableList()

        val removed = currentNumbers.removeAll { normalizePhoneNumber(it) == normalizePhoneNumber(trimmed) }

        if (removed) {
            saveAllowedNumbers(currentNumbers.joinToString(","))
            Log.d(tag, "🗑️ Removed phone number: $trimmed")
            return true
        } else {
            Log.w(tag, "⚠️ Phone number $trimmed not found in allowed numbers")
            return false
        }
    }

    fun isPhoneNumberAllowed(phoneNumber: String): Boolean {
        val allowedNumbers = getAllowedNumbers()

        if (allowedNumbers.isEmpty()) {
            Log.w(tag, "🚫 No phone numbers configured in allowed numbers")
            return false
        }

        val normalizedIncoming = normalizePhoneNumber(phoneNumber)

        for (allowedNumber in allowedNumbers) {
            val normalizedAllowed = normalizePhoneNumber(allowedNumber)

            if (normalizedIncoming == normalizedAllowed ||
                normalizedIncoming.contains(normalizedAllowed) ||
                normalizedAllowed.contains(normalizedIncoming)
            ) {
                Log.d(tag, "✅ Phone number $phoneNumber matches allowed entry: $allowedNumber")
                return true
            }
        }

        Log.w(tag, "🚫 Phone number $phoneNumber not found in allowed numbers: $allowedNumbers")
        return false
    }

    private fun normalizePhoneNumber(phoneNumber: String): String {
        return phoneNumber.replace(Regex("[\\s\\-\\(\\)\\.]"), "")
    }

    fun getWhitelistSummary(): String {
        val numbers = getAllowedNumbers()
        return when (numbers.size) {
            0 -> "No numbers configured"
            1 -> "1 number: ${numbers.first()}"
            else -> "${numbers.size} numbers: ${numbers.take(
                2,
            ).joinToString(", ")}${if (numbers.size > 2) "..." else ""}"
        }
    }

    fun validatePhoneNumber(phoneNumber: String): String? {
        val trimmed = phoneNumber.trim()

        if (trimmed.isBlank()) {
            return "Phone number cannot be empty"
        }

        if (trimmed.length < 5) {
            return "Phone number too short"
        }

        if (trimmed.length > 20) {
            return "Phone number too long"
        }

        if (!trimmed.matches(Regex("^[+\\d\\s\\-\\(\\)\\.]+$"))) {
            return "Invalid phone number format"
        }

        return null
    }
}
