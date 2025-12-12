package com.wireup.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Security utilities for secure file handling and data sanitization
 */
public class SecurityUtils {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * Securely delete a file by overwriting with random data before deletion
     * Helps prevent recovery of sensitive WireGuard configs
     */
    public static void secureDelete(File file) throws IOException {
        if (file == null || !file.exists()) {
            return;
        }

        if (file.isFile()) {
            // Get file size
            long fileSize = file.length();

            // Overwrite with random data (3 passes for defense in depth)
            byte[] randomData = new byte[4096];
            for (int pass = 0; pass < 3; pass++) {
                try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(file, "rws")) {
                    long remaining = fileSize;
                    while (remaining > 0) {
                        int toWrite = (int) Math.min(randomData.length, remaining);
                        SECURE_RANDOM.nextBytes(randomData);
                        raf.write(randomData, 0, toWrite);
                        remaining -= toWrite;
                    }
                }
            }
        }

        // Delete the file
        Files.delete(file.toPath());
    }

    /**
     * Securely delete a directory and all its contents
     */
    public static void secureDeleteDirectory(Path directory) throws IOException {
        if (directory == null || !Files.exists(directory)) {
            return;
        }

        Files.walk(directory)
                .sorted((a, b) -> -a.compareTo(b)) // Delete files before directories
                .forEach(path -> {
                    try {
                        if (Files.isRegularFile(path)) {
                            secureDelete(path.toFile());
                        } else {
                            Files.delete(path);
                        }
                    } catch (IOException e) {
                        // Log but continue cleanup
                    }
                });
    }

    /**
     * Set restrictive file permissions (owner read/write only)
     * Prevents other users from reading sensitive config files
     */
    public static void setRestrictivePermissions(Path file) throws IOException {
        if (!System.getProperty("os.name").toLowerCase().contains("win")) {
            // Unix-like systems: set 600 permissions (rw-------)
            Set<PosixFilePermission> perms = new HashSet<>();
            perms.add(PosixFilePermission.OWNER_READ);
            perms.add(PosixFilePermission.OWNER_WRITE);
            Files.setPosixFilePermissions(file, perms);
        }
        // Windows: File permissions handled differently, default is restrictive
    }

    /**
     * Sanitize string for logging - redact sensitive patterns
     * Prevents accidental logging of private keys, passwords, etc.
     */
    public static String sanitizeForLogging(String input) {
        if (input == null) {
            return null;
        }

        // Redact WireGuard keys (base64 patterns after "PrivateKey", "PublicKey",
        // "PresharedKey")
        String sanitized = input.replaceAll(
                "(?i)(PrivateKey|PublicKey|PresharedKey|Password)\\s*=\\s*[A-Za-z0-9+/=]{30,}",
                "$1 = [REDACTED]");

        // Redact potential passwords or secrets
        sanitized = sanitized.replaceAll(
                "(?i)(password|secret|token|key)\\s*[=:]\\s*\\S+",
                "$1 = [REDACTED]");

        return sanitized;
    }

    /**
     * Validate file path to prevent directory traversal attacks
     */
    public static boolean isValidFilePath(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            return false;
        }

        // Reject paths with directory traversal patterns
        if (filePath.contains("..") || filePath.contains("~")) {
            return false;
        }

        // Reject absolute paths outside temp directory
        File file = new File(filePath);
        try {
            String canonical = file.getCanonicalPath();
            String tmpDir = System.getProperty("java.io.tmpdir");
            return canonical.startsWith(tmpDir);
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Validate that a string doesn't contain shell metacharacters
     * Prevents command injection
     */
    public static boolean isSafeString(String input) {
        if (input == null) {
            return true;
        }

        // Reject strings with shell metacharacters
        String dangerous = ";|&$`<>(){}[]!*?~#";
        for (char c : dangerous.toCharArray()) {
            if (input.indexOf(c) >= 0) {
                return false;
            }
        }

        return true;
    }

    /**
     * Securely clear sensitive data from memory
     * Overwrites byte arrays and strings
     */
    public static void clearSensitiveData(byte[] data) {
        if (data != null) {
            Arrays.fill(data, (byte) 0);
        }
    }

    /**
     * Securely clear sensitive character array
     */
    public static void clearSensitiveData(char[] data) {
        if (data != null) {
            Arrays.fill(data, '\0');
        }
    }
}
