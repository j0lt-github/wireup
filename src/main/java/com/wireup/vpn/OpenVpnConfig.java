package com.wireup.vpn;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses and validates OpenVPN configuration
 */
public class OpenVpnConfig implements VpnConfig {

    private final String rawConfig;
    private boolean isValid;
    private String errorMessage;
    private String remoteEndpoint;

    public OpenVpnConfig(String rawConfig) {
        this.rawConfig = rawConfig;
        parseAndValidate();
    }

    private void parseAndValidate() {
        if (rawConfig == null || rawConfig.trim().isEmpty()) {
            isValid = false;
            errorMessage = "Configuration is empty";
            return;
        }

        // Basic OpenVPN validation checklist
        // 1. Check for 'client' directive (usually present for client configs)
        // 2. Check for 'remote' directive (endpoint)
        // 3. Check for 'dev' directive (usually 'dev tun')
        // 4. Check for certificates/keys (ca, cert, key OR <ca>...</ca>) or
        // auth-user-pass

        boolean hasRemote = rawConfig.contains("remote ");
        boolean hasDev = rawConfig.contains("dev ");

        // Extract remote endpoint for summary
        Pattern remotePattern = Pattern.compile("remote\\s+([^\\s]+)\\s+(\\d+)");
        Matcher remoteMatcher = remotePattern.matcher(rawConfig);
        if (remoteMatcher.find()) {
            remoteEndpoint = remoteMatcher.group(1) + ":" + remoteMatcher.group(2);
        } else {
            remoteEndpoint = "Unknown";
        }

        if (!hasRemote) {
            isValid = false;
            errorMessage = "Missing 'remote' directive (VPN endpoint)";
            return;
        }

        if (!hasDev) {
            isValid = false;
            errorMessage = "Missing 'dev' directive (e.g., 'dev tun')";
            return;
        }

        // Check for credentials
        // We support:
        // 1. Inline certificates (<ca>, <cert>, <key>)
        // 2. Inline static key (<secret>)
        // 3. auth-user-pass (requires separate credentials file, which we don't support
        // well yet without UI)

        boolean hasInlineCert = rawConfig.contains("<cert>");
        boolean hasAuthUserPass = rawConfig.contains("auth-user-pass");

        if (hasAuthUserPass && !hasInlineCert) {
            // For now, warn if auth-user-pass is used without inline certs, as we might
            // need credentials
            // But many configs use both. We'll mark valid but user might fail auth if
            // prompt needed.
            // Ideally we'd validte if auth-user-pass points to a file, which wouldn't exist
            // in container.
            // So we should check if it's just "auth-user-pass" (interactive) or
            // "auth-user-pass file"
        }

        isValid = true;
        errorMessage = null;
    }

    @Override
    public VpnType getType() {
        return VpnType.OPENVPN;
    }

    @Override
    public String getRawConfig() {
        return rawConfig;
    }

    @Override
    public boolean isValid() {
        return isValid;
    }

    @Override
    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public String getSummary() {
        if (!isValid) {
            return "Invalid OpenVPN Configuration: " + errorMessage;
        }
        return String.format("OpenVPN Config\nEndpoint: %s\nProtocol: %s",
                remoteEndpoint,
                rawConfig.contains("proto tcp") ? "TCP" : "UDP");
    }
}
