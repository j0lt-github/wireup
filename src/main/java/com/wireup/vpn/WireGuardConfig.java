package com.wireup.vpn;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * WireGuard configuration parser and validator
 */
public class WireGuardConfig implements VpnConfig {

    private String rawConfig;
    private Map<String, String> interfaceSection;
    private Map<String, String> peerSection;
    private boolean valid;
    private String errorMessage;

    public WireGuardConfig(String config) {
        this.rawConfig = config;
        this.interfaceSection = new HashMap<>();
        this.peerSection = new HashMap<>();
        parse();
    }

    private void parse() {
        if (rawConfig == null || rawConfig.trim().isEmpty()) {
            valid = false;
            errorMessage = "Config is empty";
            return;
        }

        try {
            String[] lines = rawConfig.split("\n");
            Map<String, String> currentSection = null;

            for (String line : lines) {
                line = line.trim();

                // Skip comments and empty lines
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                // Check for section headers
                if (line.equalsIgnoreCase("[Interface]")) {
                    currentSection = interfaceSection;
                    continue;
                } else if (line.equalsIgnoreCase("[Peer]")) {
                    currentSection = peerSection;
                    continue;
                }

                // Parse key-value pairs
                if (currentSection != null && line.contains("=")) {
                    String[] parts = line.split("=", 2);
                    if (parts.length == 2) {
                        String key = parts[0].trim();
                        String value = parts[1].trim();
                        currentSection.put(key, value);
                    }
                }
            }

            validate();

        } catch (Exception e) {
            valid = false;
            errorMessage = "Parse error: " + e.getMessage();
        }
    }

    private void validate() {
        // Check required Interface fields
        if (!interfaceSection.containsKey("PrivateKey")) {
            valid = false;
            errorMessage = "Missing PrivateKey in [Interface]";
            return;
        }

        if (!interfaceSection.containsKey("Address")) {
            valid = false;
            errorMessage = "Missing Address in [Interface]";
            return;
        }

        // Check required Peer fields
        if (!peerSection.containsKey("PublicKey")) {
            valid = false;
            errorMessage = "Missing PublicKey in [Peer]";
            return;
        }

        if (!peerSection.containsKey("Endpoint")) {
            valid = false;
            errorMessage = "Missing Endpoint in [Peer]";
            return;
        }

        // Validate endpoint format (host:port)
        String endpoint = peerSection.get("Endpoint");
        if (!endpoint.matches(".+:\\d+")) {
            valid = false;
            errorMessage = "Invalid Endpoint format (expected host:port)";
            return;
        }

        valid = true;
        errorMessage = null;
    }

    @Override
    public VpnType getType() {
        return VpnType.WIREGUARD;
    }

    @Override
    public boolean isValid() {
        return valid;
    }

    @Override
    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public String getRawConfig() {
        return rawConfig;
    }

    public String getInterfaceValue(String key) {
        return interfaceSection.get(key);
    }

    public String getPeerValue(String key) {
        return peerSection.get(key);
    }

    /**
     * Get a summary of the configuration for display
     */
    @Override
    public String getSummary() {
        if (!valid) {
            return "Invalid configuration: " + errorMessage;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("WireGuard Config\n");
        sb.append("Interface Address: ").append(interfaceSection.get("Address")).append("\n");
        sb.append("Endpoint: ").append(peerSection.get("Endpoint")).append("\n");

        if (peerSection.containsKey("AllowedIPs")) {
            sb.append("Allowed IPs: ").append(peerSection.get("AllowedIPs"));
        }

        return sb.toString();
    }
}
