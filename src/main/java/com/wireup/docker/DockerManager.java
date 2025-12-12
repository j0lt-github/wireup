package com.wireup.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.BuildImageCmd;
import com.github.dockerjava.api.command.BuildImageResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.api.model.Ulimit;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import com.wireup.utils.Logger;
import com.wireup.utils.VpnLogger;
import com.wireup.utils.SecurityUtils;
import com.wireup.vpn.VpnConfig;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/**
 * Manages Docker containers for WireGuard VPN
 */
public class DockerManager {

    private static final String IMAGE_NAME = "wireup-vpn";
    private static final String IMAGE_TAG = "latest";
    private static final String CONTAINER_NAME = "wireup-vpn-container";
    private static final int SOCKS_PORT = 1080;

    private final VpnLogger logger;
    private final DockerClient dockerClient;
    private String currentContainerId;
    private Path tempConfigDir;

    public DockerManager(VpnLogger logger) throws Exception {
        this.logger = logger;
        this.dockerClient = createDockerClient();
        this.tempConfigDir = Files.createTempDirectory("wireup-");

        logger.debug("Docker manager initialized with temp dir: " + tempConfigDir);

        // Verify Docker is accessible
        try {
            dockerClient.pingCmd().exec();
            logger.info("Docker daemon is accessible");
        } catch (Exception e) {
            throw new Exception("Cannot connect to Docker daemon. Is Docker running?", e);
        }
    }

    private DockerClient createDockerClient() {
        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .build();

        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .build();

        return DockerClientImpl.getInstance(config, httpClient);
    }

    /**
     * Build the WireGuard + SOCKS5 Docker image
     */
    public void buildImage() throws Exception {
        logger.info("Building Docker image: " + IMAGE_NAME + ":" + IMAGE_TAG);

        // Get Dockerfile from resources
        File dockerfileDir = getDockerfileDirectory();

        BuildImageCmd buildCmd = dockerClient.buildImageCmd()
                .withDockerfile(new File(dockerfileDir, "Dockerfile"))
                .withTags(new HashSet<>(Arrays.asList(IMAGE_NAME + ":" + IMAGE_TAG)))
                .withPull(false);

        String imageId = buildCmd.exec(new BuildImageResultCallback())
                .awaitImageId();

        logger.info("Docker image built successfully: " + imageId);
    }

    /**
     * Check if image exists
     */
    public boolean imageExists() {
        try {
            dockerClient.inspectImageCmd(IMAGE_NAME + ":" + IMAGE_TAG).exec();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Create and start a container with the given VPN config
     */
    public String createAndStartContainer(com.wireup.vpn.VpnConfig config) throws Exception {
        // Stop any existing container (including orphaned ones from previous extension
        // loads)
        stopAndRemoveContainer();

        // Force remove old image to ensure we rebuild with latest Dockerfile
        try {
            dockerClient.removeImageCmd(IMAGE_NAME + ":" + IMAGE_TAG)
                    .withForce(true)
                    .exec();
            logger.debug("Removed old image to force rebuild");
        } catch (Exception e) {
            // Image might not exist, that's fine
            logger.debug("No old image to remove");
        }

        // Ensure image exists (will build fresh image)
        if (!imageExists()) {
            logger.info("Image not found, building...");
            buildImage();
        }

        String configContent = config.getRawConfig();
        String configFileName;
        String containerConfigPath;
        String vpnTypeEnv;

        if (config.getType() == com.wireup.vpn.VpnConfig.VpnType.OPENVPN) {
            configFileName = "client.conf";
            containerConfigPath = "/etc/openvpn/client.conf";
            vpnTypeEnv = "openvpn";
        } else {
            configFileName = "wg0.conf";
            containerConfigPath = "/etc/wireguard/wg0.conf";
            vpnTypeEnv = "wireguard";
        }

        // Write config to temp file with restrictive permissions
        File configFile = new File(tempConfigDir.toFile(), configFileName);
        try (FileWriter writer = new FileWriter(configFile)) {
            writer.write(configContent);
        }

        // Set restrictive permissions (owner read/write only)
        try {
            SecurityUtils.setRestrictivePermissions(configFile.toPath());
            logger.securityInfo("VPN config file created with restrictive permissions");
        } catch (Exception e) {
            logger.warn("Could not set restrictive permissions: " + e.getMessage());
        }

        logger.debug(vpnTypeEnv + " config written to temp directory (path redacted for security)");

        // Handle OpenVPN authentication if credentials are provided
        Bind authBind = null;
        if (config.getType() == com.wireup.vpn.VpnConfig.VpnType.OPENVPN) {
            com.wireup.vpn.OpenVpnConfig ovpnConfig = (com.wireup.vpn.OpenVpnConfig) config;
            if (ovpnConfig.hasCredentials()) {
                // Create auth file
                File authFile = new File(tempConfigDir.toFile(), "auth.txt");
                try (FileWriter authWriter = new FileWriter(authFile)) {
                    authWriter.write(ovpnConfig.getUsername() + "\n");
                    authWriter.write(ovpnConfig.getPassword() + "\n");
                }

                // Set restrictive permissions on auth file
                try {
                    SecurityUtils.setRestrictivePermissions(authFile.toPath());
                    logger.securityInfo("OpenVPN auth file created with restrictive permissions");
                } catch (Exception e) {
                    logger.warn("Could not set restrictive permissions on auth file: " + e.getMessage());
                }

                // Create volume binding for auth file
                Volume authVolume = new Volume("/etc/openvpn/auth.txt");
                authBind = new Bind(authFile.getAbsolutePath(), authVolume);

                logger.info("OpenVPN authentication file mounted");
            }
        }

        // Create volume binding for config
        Volume configVolume = new Volume(containerConfigPath);
        Bind configBind = new Bind(configFile.getAbsolutePath(), configVolume);

        // Create bind for SOCKS5 proxy port
        ExposedPort tcp1080 = ExposedPort.tcp(1080);
        Ports portBindings = new Ports();
        portBindings.bind(tcp1080, Ports.Binding.bindPort(1080));

        logger.debug("Port binding: 1080:1080/tcp");

        // Create host config with all settings
        HostConfig hostConfig = HostConfig.newHostConfig()
                .withPrivileged(true)
                .withCapAdd(Capability.NET_ADMIN, Capability.SYS_MODULE)
                .withBinds(authBind != null ? new Bind[] { configBind, authBind } : new Bind[] { configBind })
                .withPortBindings(portBindings)
                .withMemory(512L * 1024 * 1024) // 512MB RAM limit
                .withCpuQuota(50000L) // 0.5 CPU limit
                .withPidsLimit(100L)
                .withPidsLimit(100L)
                // .withDns("8.8.8.8") // COMMENTED OUT: Caused resolution issues with
                // host.docker.internal
                .withReadonlyRootfs(false); // Helper tools need write access

        logger.securityInfo("Creating container with resource limits (512MB RAM, 0.5 CPU)");

        logger.info("Creating container: " + CONTAINER_NAME + " (" + vpnTypeEnv + ")");

        try {
            CreateContainerResponse container = dockerClient.createContainerCmd(IMAGE_NAME + ":" + IMAGE_TAG)
                    .withName(CONTAINER_NAME)
                    .withHostConfig(hostConfig)
                    .withExposedPorts(tcp1080)
                    .withEnv("VPN_TYPE=" + vpnTypeEnv)
                    .exec();

            currentContainerId = container.getId();
            logger.info("Container created: " + currentContainerId);

        } catch (com.github.dockerjava.api.exception.ConflictException e) {
            // Container with this name already exists - force cleanup and retry
            logger.warn("Container name conflict detected - cleaning up old container");
            stopAndRemoveContainer();

            // Retry creation
            CreateContainerResponse container = dockerClient.createContainerCmd(IMAGE_NAME + ":" + IMAGE_TAG)
                    .withName(CONTAINER_NAME)
                    .withHostConfig(hostConfig)
                    .withExposedPorts(tcp1080)
                    .withEnv("VPN_TYPE=" + vpnTypeEnv)
                    .exec();

            currentContainerId = container.getId();
            logger.info("Container created after cleanup: " + currentContainerId);
        }

        // Start container
        logger.info("Starting container...");
        dockerClient.startContainerCmd(currentContainerId).exec();

        // Wait a bit for container to initialize
        Thread.sleep(3000);

        // Check if container is running
        if (!isContainerRunning()) {
            String logs = getContainerLogs();
            throw new Exception("Container failed to start. Logs:\n" + logs);
        }

        logger.info("Container started successfully");
        return currentContainerId;
    }

    /**
     * Stop and remove current container
     */
    public void stopAndRemoveContainer() {
        if (currentContainerId != null) {
            try {
                logger.info("Stopping container: " + currentContainerId);
                dockerClient.stopContainerCmd(currentContainerId)
                        .withTimeout(10)
                        .exec();

                logger.info("Removing container: " + currentContainerId);
                dockerClient.removeContainerCmd(currentContainerId).exec();

                currentContainerId = null;
            } catch (Exception e) {
                logger.warn("Error stopping/removing container: " + e.getMessage());
            }
        }

        // Also try to remove by name in case of orphaned containers
        try {
            List<Container> containers = dockerClient.listContainersCmd()
                    .withShowAll(true)
                    .withNameFilter(Arrays.asList(CONTAINER_NAME))
                    .exec();

            for (Container container : containers) {
                try {
                    dockerClient.stopContainerCmd(container.getId()).withTimeout(5).exec();
                    dockerClient.removeContainerCmd(container.getId()).exec();
                    logger.info("Cleaned up orphaned container: " + container.getId());
                } catch (Exception e) {
                    logger.debug("Could not clean up container: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.debug("Error cleaning up containers: " + e.getMessage());
        }
    }

    /**
     * Check if container is currently running
     */
    public boolean isContainerRunning() {
        if (currentContainerId == null) {
            return false;
        }

        try {
            List<Container> containers = dockerClient.listContainersCmd()
                    .withIdFilter(Arrays.asList(currentContainerId))
                    .exec();

            return !containers.isEmpty() && "running".equalsIgnoreCase(containers.get(0).getState());
        } catch (Exception e) {
            logger.debug("Error checking container status: " + e.getMessage());
            return false;
        }
    }

    /**
     * Get container logs (for debugging)
     */
    public String getContainerLogs() {
        if (currentContainerId == null) {
            return "No container running";
        }

        try {
            // Use LogContainerResultCallback to collect logs
            final StringBuilder logs = new StringBuilder();

            dockerClient.logContainerCmd(currentContainerId)
                    .withStdOut(true)
                    .withStdErr(true)
                    .withTailAll()
                    .exec(new com.github.dockerjava.api.async.ResultCallback.Adapter<com.github.dockerjava.api.model.Frame>() {
                        @Override
                        public void onNext(com.github.dockerjava.api.model.Frame frame) {
                            logs.append(new String(frame.getPayload()));
                        }
                    })
                    .awaitCompletion(5, java.util.concurrent.TimeUnit.SECONDS);

            return logs.toString();
        } catch (Exception e) {
            return "Error reading logs: " + e.getMessage();
        }
    }

    /**
     * Get Dockerfile directory by extracting from JAR resources to temp directory
     */
    private File getDockerfileDirectory() throws Exception {
        // Create temp directory for Dockerfile resources
        Path dockerfileTempDir = Files.createTempDirectory("wireup-dockerfile-");
        File tempDir = dockerfileTempDir.toFile();

        logger.debug("Extracting Dockerfile resources to: " + tempDir.getAbsolutePath());

        try {
            // Extract Dockerfile
            extractResource("/dockerfile/Dockerfile", new File(tempDir, "Dockerfile"));

            // Extract danted.conf
            extractResource("/dockerfile/danted.conf", new File(tempDir, "danted.conf"));

            logger.debug("Dockerfile resources extracted successfully");
            return tempDir;

        } catch (Exception e) {
            // Cleanup on failure
            try {
                Files.walk(dockerfileTempDir)
                        .sorted((a, b) -> -a.compareTo(b))
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (Exception ex) {
                            }
                        });
            } catch (Exception cleanupEx) {
                // Ignore cleanup errors
            }
            throw new Exception("Failed to extract Dockerfile resources: " + e.getMessage(), e);
        }
    }

    /**
     * Extract a resource from JAR to a file
     */
    private void extractResource(String resourcePath, File targetFile) throws Exception {
        try (java.io.InputStream inputStream = getClass().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new Exception("Resource not found in JAR: " + resourcePath);
            }

            try (java.io.FileOutputStream outputStream = new java.io.FileOutputStream(targetFile)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }

            logger.debug("Extracted resource: " + resourcePath + " -> " + targetFile.getName());
        }
    }

    /**
     * Cleanup all resources
     */
    public void cleanup() {
        logger.info("Cleaning up Docker resources...");

        stopAndRemoveContainer();

        // Clean up temp config directory
        if (tempConfigDir != null) {
            try {
                Files.walk(tempConfigDir)
                        .sorted((a, b) -> -a.compareTo(b))
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (Exception e) {
                                // Ignore
                            }
                        });
            } catch (Exception e) {
                logger.debug("Error cleaning temp directory: " + e.getMessage());
            }
        }

        logger.info("Docker cleanup complete");
    }

    public String getCurrentContainerId() {
        return currentContainerId;
    }
}
