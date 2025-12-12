package com.wireup.vpn;

import burp.api.montoya.MontoyaApi;
import com.wireup.docker.DockerManager;
import com.wireup.docker.ContainerHealthMonitor;
import com.wireup.utils.IpVerifier;
import com.wireup.utils.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Manages VPN connection state and integrates Docker with Burp proxy settings
 */
public class ConnectionManager {

    public enum ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        ERROR
    }

    private final DockerManager dockerManager;
    private final Logger logger;
    private final ContainerHealthMonitor healthMonitor;

    private ConnectionState state;
    private VpnConfig currentConfig;
    private String errorMessage;
    private String vpnIp;
    private List<Consumer<ConnectionState>> stateChangeListeners;

    private static final String PROXY_HOST = "127.0.0.1";
    private static final int PROXY_PORT = 1080;

    public ConnectionManager(MontoyaApi api, DockerManager dockerManager, Logger logger) {
        this.dockerManager = dockerManager;
        this.logger = logger;
        this.healthMonitor = new ContainerHealthMonitor(dockerManager, logger);
        this.state = ConnectionState.DISCONNECTED;
        this.stateChangeListeners = new ArrayList<>();

        // Start health monitoring
        healthMonitor.startMonitoring(this::onContainerStateChange);
    }

    /**
     * Connect to VPN with the given config object
     */
    public void connect(VpnConfig config) {
        new Thread(() -> {
            try {
                setState(ConnectionState.CONNECTING);
                logger.info("Initiating VPN connection (DEBUG BUILD v2)...");
                logger.info("NOTE: Container cleanup is DISABLED on error.");

                // Validate config (double check)
                if (!config.isValid()) {
                    throw new Exception("Invalid config: " + config.getErrorMessage());
                }

                this.currentConfig = config;

                logger.info("Config validated successfully: " + config.getType());
                logger.debug(config.getSummary());

                // Create and start Docker container
                String containerId = dockerManager.createAndStartContainer(config);
                logger.info("Container started: " + containerId);

                // Wait for SOCKS proxy to be ready and retry connection
                logger.info("Waiting for SOCKS proxy to become ready...");
                Thread.sleep(10000); // Increased to 10 seconds

                // Retry logic for SOCKS proxy connection
                String vpnIp = null;
                int maxRetries = 3;
                for (int i = 0; i < maxRetries; i++) {
                    logger.info("Verifying VPN connection (attempt " + (i + 1) + "/" + maxRetries + ")...");
                    vpnIp = IpVerifier.getIpThroughProxy(PROXY_HOST, PROXY_PORT);

                    if (!vpnIp.startsWith("Error")) {
                        logger.info("VPN IP verified: " + vpnIp);
                        break;
                    }

                    if (i < maxRetries - 1) {
                        logger.warn("SOCKS proxy not ready yet: " + vpnIp);
                        logger.info("Waiting 5 more seconds...");
                        Thread.sleep(5000); // Increased to 5 seconds between retries
                    }
                }

                if (vpnIp == null || vpnIp.startsWith("Error")) {
                    // Get container logs for debugging
                    String logs = dockerManager.getContainerLogs();
                    logger.error("Container logs:\n" + logs);
                    throw new Exception(
                            "Cannot connect through SOCKS proxy after " + maxRetries + " attempts: " + vpnIp);
                }

                logger.info("VPN IP verified: " + vpnIp);

                // Configure Burp's upstream proxy
                configureBurpProxy(true);

                setState(ConnectionState.CONNECTED);
                logger.info("VPN connection established successfully!");

            } catch (Exception e) {
                logger.error("Connection failed: " + e.getMessage(), e);
                errorMessage = e.getMessage();
                setState(ConnectionState.ERROR);

                // CRITICAL: Always clean up on error to prevent orphaned containers
                // try {
                // logger.info("Cleaning up failed connection...");
                // dockerManager.stopAndRemoveContainer();
                // logger.debug("Container cleanup completed");
                // } catch (Exception cleanupEx) {
                // logger.debug("Cleanup error (may be already removed): " +
                // cleanupEx.getMessage());
                // }
            }
        }, "WireUp-Connect").start();
    }

    /**
     * Disconnect from VPN
     */
    public void disconnect() {
        new Thread(() -> {
            try {
                logger.info("Disconnecting VPN...");

                // Remove Burp proxy configuration
                configureBurpProxy(false);

                // Stop Docker container
                dockerManager.stopAndRemoveContainer();

                // Don't clear currentConfig here, allowing reconnect
                vpnIp = null;
                errorMessage = null;

                setState(ConnectionState.DISCONNECTED);
                logger.info("VPN disconnected");

            } catch (Exception e) {
                logger.error("Error during disconnect: " + e.getMessage(), e);
            }
        }, "WireUp-Disconnect").start();
    }

    /**
     * Reconnect with current configuration
     */
    public void reconnect() {
        if (currentConfig != null) {
            disconnect();

            // Wait a bit before reconnecting
            new Thread(() -> {
                try {
                    Thread.sleep(2000);
                    // Re-use the existing config object
                    connect(currentConfig);
                } catch (InterruptedException e) {
                    logger.debug("Reconnect interrupted");
                }
            }, "WireUp-Reconnect").start();
        }
    }

    /**
     * Configure Burp Suite's upstream proxy settings
     */
    private void configureBurpProxy(boolean enable) {
        try {
            if (enable) {
                // Note: Burp Montoya API doesn't have direct proxy configuration methods
                // This would need to be done through Burp's UI or settings

                String message = "IMPORTANT: You must configure Burp Suite to use the SOCKS proxy:\n\n" +
                        "1. Go to Settings -> Network -> Connections\n" +
                        "2. Under 'Upstream Proxy Servers', click 'Add'\n" +
                        "3. Set Proxy host: " + PROXY_HOST + "\n" +
                        "4. Set Proxy port: " + PROXY_PORT + "\n" +
                        "5. Select 'SOCKS proxy'\n" +
                        "6. Click 'OK'\n\n" +
                        "Without this, Burp will bypass the VPN!";

                logger.info("=".repeat(60));
                logger.info(message);
                logger.info("=".repeat(60));

                // Show dialog to ensure user sees this
                javax.swing.SwingUtilities.invokeLater(() -> {
                    javax.swing.JOptionPane.showMessageDialog(
                            null,
                            message,
                            "WireUp - Configuration Required",
                            javax.swing.JOptionPane.INFORMATION_MESSAGE);
                });

            } else {
                logger.info("Remember to remove the upstream proxy configuration from Burp Suite");
            }
        } catch (Exception e) {
            logger.warn("Could not configure Burp proxy: " + e.getMessage());
        }
    }

    /**
     * Handle container state changes from health monitor
     */
    private void onContainerStateChange(boolean running) {
        if (!running && state == ConnectionState.CONNECTED) {
            logger.warn("Container stopped unexpectedly!");
            errorMessage = "Container stopped unexpectedly";
            setState(ConnectionState.ERROR);
        }
    }

    /**
     * Set connection state and notify listeners
     */
    private void setState(ConnectionState newState) {
        if (this.state != newState) {
            this.state = newState;
            logger.debug("State changed to: " + newState);

            // Notify all listeners
            for (Consumer<ConnectionState> listener : stateChangeListeners) {
                try {
                    listener.accept(newState);
                } catch (Exception e) {
                    logger.debug("Error notifying listener: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Add a state change listener
     */
    public void addStateChangeListener(Consumer<ConnectionState> listener) {
        stateChangeListeners.add(listener);
    }

    // Getters
    public ConnectionState getState() {
        return state;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getVpnIp() {
        return vpnIp;
    }

    public VpnConfig getCurrentConfig() {
        return currentConfig;
    }

    public boolean isConnected() {
        return state == ConnectionState.CONNECTED;
    }
}
