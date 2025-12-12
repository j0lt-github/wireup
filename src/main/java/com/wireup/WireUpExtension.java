package com.wireup;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import com.wireup.docker.DockerManager;
import com.wireup.ui.WireUpTab;
import com.wireup.utils.Logger;
import com.wireup.vpn.ConnectionManager;

/**
 * WireUp - Burp Suite Extension for WireGuard VPN Routing
 * 
 * This extension routes all Burp Suite traffic through a WireGuard VPN
 * while keeping the host system's traffic unaffected using Docker
 * containerization.
 */
public class WireUpExtension implements BurpExtension {

    private MontoyaApi api;
    private Logger logger;
    private DockerManager dockerManager;
    private ConnectionManager connectionManager;
    private WireUpTab mainTab;

    @Override
    public void initialize(MontoyaApi api) {
        this.api = api;

        // Set extension name
        api.extension().setName("WireUp");

        // Initialize logger FIRST
        this.logger = new Logger(api);
        logger.info("WireUp extension loading...");
        logger.info("Burp Montoya API version: 2023.12.1+");

        // CRITICAL: Register UI tab BEFORE initializing Docker
        // This ensures the tab appears even if Docker is not available
        try {
            logger.info("Creating UI components...");

            // Create UI with null managers initially
            this.mainTab = new WireUpTab(api, null, logger);
            api.userInterface().registerSuiteTab("WireUp", mainTab.getComponent());
            logger.info("✓ WireUp tab registered successfully!");

        } catch (Exception e) {
            logger.error("CRITICAL: Failed to register UI tab: " + e.getMessage(), e);
            // Don't throw - try to continue anyway
        }

        // Now try to initialize Docker components with smart detection
        try {
            logger.info("Checking Docker availability...");

            // Check if Docker is installed
            if (!com.wireup.utils.DockerUtils.isDockerInstalled()) {
                logger.warn("=".repeat(60));
                logger.warn("Docker is NOT installed on this system");
                logger.warn("OS: " + com.wireup.utils.DockerUtils.getOsName());
                logger.warn("Download from: " + com.wireup.utils.DockerUtils.getDockerDownloadUrl());
                logger.warn("=".repeat(60));

                // Show installation dialog
                showDockerInstallationDialog();
                return; // Stop here, UI is still loaded
            }

            logger.info("✓ Docker is installed");

            // Check if Docker is running
            if (!com.wireup.utils.DockerUtils.isDockerRunning()) {
                logger.warn("Docker is installed but not running");
                logger.info("Attempting to start Docker...");

                if (com.wireup.utils.DockerUtils.tryStartDocker()) {
                    logger.info("Docker start command sent - waiting 10 seconds...");

                    // Wait for Docker to start
                    for (int i = 0; i < 20; i++) {
                        Thread.sleep(500);
                        if (com.wireup.utils.DockerUtils.isDockerRunning()) {
                            logger.info("✓ Docker started successfully!");
                            break;
                        }
                    }

                    // Check again
                    if (!com.wireup.utils.DockerUtils.isDockerRunning()) {
                        logger.warn("Docker did not start automatically");
                        logger.warn("Please start Docker Desktop manually and reload extension");
                        return;
                    }
                } else {
                    logger.warn("Could not auto-start Docker");
                    logger.warn("Please start Docker Desktop manually and reload extension");
                    return;
                }
            }

            logger.info("✓ Docker is running");
            logger.info("Initializing Docker manager...");
            this.dockerManager = new DockerManager(logger);
            logger.info("✓ Docker manager initialized");

            // Pre-build the Docker image so it's ready when user connects
            logger.info("Preparing WireGuard+SOCKS5 Docker image...");
            try {
                if (!dockerManager.imageExists()) {
                    logger.info("Building Docker image (first time setup - may take 1-2 minutes)...");
                    dockerManager.buildImage();
                    logger.info("✓ Docker image built successfully!");
                } else {
                    logger.info("✓ Docker image already exists (skipping build)");
                }
            } catch (Exception e) {
                logger.warn("Failed to build Docker image: " + e.getMessage());
                logger.warn("Image will be built when you first connect to VPN");
                // Don't fail - image can be built on first connect
            }

            logger.info("Initializing connection manager...");
            this.connectionManager = new ConnectionManager(api, dockerManager, logger);
            logger.info("✓ Connection manager initialized");

            // Update the tab with the real connection manager
            if (mainTab != null) {
                mainTab.setConnectionManager(connectionManager);
                logger.info("✓ UI connected to connection manager");
            }

            // Register unload handler
            api.extension().registerUnloadingHandler(this::cleanup);

            logger.info("=".repeat(60));
            logger.info("✓ WireUp extension loaded successfully!");
            logger.info("  Check 'WireUp' tab in Burp Suite main window");
            logger.info("=".repeat(60));

        } catch (Exception e) {
            logger.error("WARNING: Failed to initialize Docker components: " + e.getMessage(), e);
            logger.warn("=".repeat(60));
            logger.warn("WireUp UI is available, but VPN features are disabled");
            logger.warn("Reason: " + e.getMessage());
            logger.warn("Fix: Ensure Docker Desktop is running and try reloading extension");
            logger.warn("=".repeat(60));
            // Don't throw - extension can still load with UI only
        }
    }

    /**
     * Show Docker installation dialog with instructions
     */
    private void showDockerInstallationDialog() {
        javax.swing.SwingUtilities.invokeLater(() -> {
            String instructions = com.wireup.utils.DockerUtils.getInstallationInstructions();
            javax.swing.JOptionPane.showMessageDialog(
                    null,
                    instructions,
                    "Docker Not Installed - WireUp",
                    javax.swing.JOptionPane.WARNING_MESSAGE);
        });
    }

    /**
     * Cleanup when extension is unloaded
     */
    private void cleanup() {
        logger.info("WireUp extension unloading...");

        try {
            if (connectionManager != null) {
                connectionManager.disconnect();
            }

            if (dockerManager != null) {
                dockerManager.cleanup();
            }

            logger.info("WireUp extension unloaded successfully");
        } catch (Exception e) {
            logger.error("Error during cleanup: " + e.getMessage(), e);
        }
    }
}
