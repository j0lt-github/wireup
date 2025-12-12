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
    private boolean requiresAuth;

    // Authentication credentials (optional)
    private String username;
    private String password;

    public OpenVpnConfig(String rawConfig) {
        this.rawConfig = rawConfig;
        parseAndValidate();
    }

    public void setCredentials(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public boolean requiresAuth() {
        return requiresAuth;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public boolean hasCredentials() {
        return username != null && !username.isEmpty() &&
                password != null && !password.isEmpty();
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
        // 2. auth-user-pass (requires username/password from UI)

        boolean hasEmbeddedCert = rawConfig.contains("<ca>") || rawConfig.contains("<cert>") ||
                rawConfig.contains("<key>");
        boolean hasAuthDirective = rawConfig.contains("auth-user-pass");

        // Set requiresAuth flag
        this.requiresAuth = hasAuthDirective;

        if (!hasEmbeddedCert && !hasAuthDirective) {
            isValid = false;
            errorMessage = "Missing authentication: need either embedded certificates or 'auth-user-pass' directive";
            return;
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
