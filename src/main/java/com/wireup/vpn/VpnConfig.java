package com.wireup.vpn;

/**
 * Interface for different VPN configuration types
 */
public interface VpnConfig {

    enum VpnType {
        WIREGUARD,
        OPENVPN
    }

    /**
     * Get the type of VPN
     */
    VpnType getType();

    /**
     * Get the raw configuration content
     */
    String getRawConfig();

    /**
     * Check if the configuration is valid
     */
    boolean isValid();

    /**
     * Get validation error message if invalid
     */
    String getErrorMessage();

    /**
     * Get a user-friendly summary of the configuration
     */
    String getSummary();
}
