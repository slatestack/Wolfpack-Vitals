package com.example.wolfpackvitals.data

import android.content.Context
import android.content.SharedPreferences
import java.io.File
import java.security.MessageDigest

data class UserRecord(
    val name: String,
    val email: String,
    val passwordHash: String,
    val createdAt: String = System.currentTimeMillis().toString()
)

sealed class AuthResult {
    data class Success(val user: UserRecord, val message: String = "") : AuthResult()
    data class Error(val message: String) : AuthResult()
}

object UserAuthManager {
    private const val CSV_FILENAME = "wolfpack_users.csv"
    private const val CSV_HEADER = "name,email,password_hash,created_at"
    private const val PREFS_NAME = "wolfpack_auth_prefs"
    private const val PREFS_KEY_USERS = "saved_users_list"

    // Thread-safe in-memory cache of all active users
    private val cachedUsers = mutableListOf<UserRecord>()
    private var isInitialized = false

    /**
     * Compute standard SHA-256 hash of a plain-text password.
     * Uses (it.toInt() and 0xFF) for guaranteed unsigned hex formatting across all architectures.
     */
    fun hashPassword(password: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(password.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }

    /**
     * Robust email format validator: ensures not blank, contains '@', domain contains '.', and no spaces.
     */
    fun isValidEmail(email: String): Boolean {
        val trimmed = email.trim()
        if (trimmed.length < 5 || !trimmed.contains("@") || trimmed.contains(" ")) {
            return false
        }
        val parts = trimmed.split("@")
        if (parts.size != 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
            return false
        }
        return parts[1].contains(".") && parts[1].substringAfterLast(".").isNotEmpty()
    }

    /**
     * Get the file reference in app's internal files directory.
     */
    private fun getCsvFile(context: Context): File {
        val dir = context.filesDir
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, CSV_FILENAME)
    }

    /**
     * Synchronize and load users from the CSV file and SharedPreferences into cache.
     */
    @Synchronized
    private fun ensureLoaded(context: Context) {
        if (isInitialized && cachedUsers.isNotEmpty()) {
            return
        }

        cachedUsers.clear()
        val file = getCsvFile(context)

        // 1. Try reading from CSV file
        if (file.exists() && file.length() > 0) {
            try {
                file.readLines().forEach { rawLine ->
                    val line = rawLine.trim()
                    // Skip empty lines and header rows
                    if (line.isNotEmpty() && !line.startsWith("name,", ignoreCase = true)) {
                        val parts = line.split(",")
                        if (parts.size >= 3) {
                            val name = parts[0].trim()
                            val email = parts[1].trim().lowercase()
                            val hash = parts[2].trim()
                            val created = if (parts.size > 3) parts[3].trim() else System.currentTimeMillis().toString()

                            if (name.isNotEmpty() && email.isNotEmpty() && hash.isNotEmpty()) {
                                if (cachedUsers.none { it.email.equals(email, ignoreCase = true) }) {
                                    cachedUsers.add(UserRecord(name, email, hash, created))
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 2. Fallback to SharedPreferences if CSV was missing or corrupted
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedSet = prefs.getStringSet(PREFS_KEY_USERS, emptySet()) ?: emptySet()
        for (raw in savedSet) {
            val parts = raw.split("||")
            if (parts.size >= 3) {
                val name = parts[0].trim()
                val email = parts[1].trim().lowercase()
                val hash = parts[2].trim()
                val created = if (parts.size > 3) parts[3].trim() else ""
                if (cachedUsers.none { it.email.equals(email, ignoreCase = true) }) {
                    cachedUsers.add(UserRecord(name, email, hash, created))
                }
            }
        }

        // 3. Ensure the default demo participant exists
        val defaultEmail = "user@ncsu.edu"
        if (cachedUsers.none { it.email.equals(defaultEmail, ignoreCase = true) }) {
            cachedUsers.add(
                0,
                UserRecord(
                    name = "Wolfpack User",
                    email = defaultEmail,
                    passwordHash = hashPassword("wolfpack123"),
                    createdAt = System.currentTimeMillis().toString()
                )
            )
        }

        // 4. Save back a clean, canonical CSV file
        persistAllUsers(context)
        isInitialized = true
    }

    /**
     * Atomically writes all cached users to wolfpack_users.csv and SharedPreferences.
     */
    @Synchronized
    private fun persistAllUsers(context: Context) {
        try {
            val file = getCsvFile(context)
            val builder = StringBuilder()
            builder.append(CSV_HEADER).append("\n")
            for (user in cachedUsers) {
                val safeName = user.name.replace(",", " ")
                builder.append(safeName).append(",")
                    .append(user.email.lowercase()).append(",")
                    .append(user.passwordHash).append(",")
                    .append(user.createdAt).append("\n")
            }
            file.writeText(builder.toString(), Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val set = cachedUsers.map { "${it.name}||${it.email}||${it.passwordHash}||${it.createdAt}" }.toSet()
            prefs.edit().putStringSet(PREFS_KEY_USERS, set).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Reads all registered users.
     */
    @Synchronized
    fun getAllUsers(context: Context): List<UserRecord> {
        ensureLoaded(context)
        return cachedUsers.toList()
    }

    /**
     * Find user record by email (case-insensitive).
     */
    @Synchronized
    fun findUserByEmail(context: Context, email: String): UserRecord? {
        ensureLoaded(context)
        val normalized = email.trim().lowercase()
        return cachedUsers.firstOrNull { it.email.equals(normalized, ignoreCase = true) }
    }

    /**
     * Validate and process user login.
     */
    @Synchronized
    fun login(context: Context, emailInput: String, passwordInput: String): AuthResult {
        val email = emailInput.trim()
        val password = passwordInput.trim()

        if (email.isEmpty()) {
            return AuthResult.Error("Please enter your email address.")
        }
        if (!isValidEmail(email)) {
            return AuthResult.Error("Please enter a valid email address (e.g. name@ncsu.edu).")
        }
        if (password.isEmpty()) {
            return AuthResult.Error("Please enter your password.")
        }

        ensureLoaded(context)

        val existingUser = findUserByEmail(context, email)
        if (existingUser == null) {
            return AuthResult.Error("Email address does not exist. Please check your credentials or register.")
        }

        val inputHash = hashPassword(password)
        if (inputHash != existingUser.passwordHash) {
            return AuthResult.Error("Incorrect password. Please try again.")
        }

        return AuthResult.Success(existingUser, "Welcome back, ${existingUser.name}!")
    }

    /**
     * Validate and process new participant registration.
     */
    @Synchronized
    fun register(
        context: Context,
        nameInput: String,
        emailInput: String,
        passwordInput: String,
        confirmPasswordInput: String
    ): AuthResult {
        val name = nameInput.trim()
        val email = emailInput.trim()
        val password = passwordInput.trim()
        val confirmPassword = confirmPasswordInput.trim()

        if (name.isEmpty()) {
            return AuthResult.Error("Please enter your full name.")
        }
        if (name.length < 2) {
            return AuthResult.Error("Full name must be at least 2 characters.")
        }
        if (email.isEmpty()) {
            return AuthResult.Error("Please enter your email address.")
        }
        if (!isValidEmail(email)) {
            return AuthResult.Error("Please enter a valid email address (e.g. name@ncsu.edu).")
        }
        if (password.isEmpty()) {
            return AuthResult.Error("Please choose a password.")
        }
        if (password.length < 6) {
            return AuthResult.Error("Password must be at least 6 characters long.")
        }
        if (confirmPassword.isEmpty()) {
            return AuthResult.Error("Please confirm your password.")
        }
        if (password != confirmPassword) {
            return AuthResult.Error("Passwords do not match. Please re-enter.")
        }

        ensureLoaded(context)

        // Check if email already registered
        val existing = findUserByEmail(context, email)
        if (existing != null) {
            return AuthResult.Error("An account with this email address already exists. Please log in.")
        }

        // Hash password and persist immediately
        val hashed = hashPassword(password)
        val safeName = name.replace(",", " ")
        val newUser = UserRecord(
            name = safeName,
            email = email.lowercase(),
            passwordHash = hashed,
            createdAt = System.currentTimeMillis().toString()
        )

        cachedUsers.add(newUser)
        persistAllUsers(context)

        return AuthResult.Success(newUser, "Account created successfully!")
    }
}
