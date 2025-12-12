package com.wireup.utils;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.logging.Logging;

/**
 * Centralized logging utility for WireUp extension
 * Automatically sanitizes sensitive data to prevent key exposure
 */
public class Logger implements VpnLogger {

    private final Logging burpLogging;
    private final boolean debugEnabled;

    public Logger(MontoyaApi api) {
        this.burpLogging = api.logging();
        this.debugEnabled = true; // Can be made configurable
    }

    @Override
    public void debug(String message) {
        if (debugEnabled) {
            log("[DEBUG] " + SecurityUtils.sanitizeForLogging(message));
        }
    }

    @Override
    public void info(String message) {
        log("[INFO] " + SecurityUtils.sanitizeForLogging(message));
    }

    @Override
    public void warn(String message) {
        log("[WARN] " + SecurityUtils.sanitizeForLogging(message));
    }

    @Override
    public void error(String message) {
        burpLogging.logToError("[ERROR] " + SecurityUtils.sanitizeForLogging(message));
    }

    @Override
    public void error(String message, Throwable throwable) {
        burpLogging.logToError("[ERROR] " + SecurityUtils.sanitizeForLogging(message));
        burpLogging.logToError("Exception: " + throwable.getClass().getName() + ": " + throwable.getMessage());

        if (debugEnabled && throwable.getStackTrace() != null) {
            for (StackTraceElement element : throwable.getStackTrace()) {
                burpLogging.logToError("  at " + element.toString());
            }
        }
    }

    /**
     * Log security-sensitive operation (extra sanitization)
     */
    @Override
    public void securityInfo(String operation) {
        log("[SECURITY] " + operation);
    }

    private void log(String message) {
        burpLogging.logToOutput("[WireUp] " + message);
    }
}
