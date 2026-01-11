package com.wireup.utils;

/**
 * Docker detection and auto-start utilities
 */
public class DockerUtils {

    /**
     * Check if Docker is installed on the system
     */
    public static boolean isDockerInstalled() {
        try {
            Process process = Runtime.getRuntime().exec(new String[] { "docker", "--version" });
            process.waitFor();
            return process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Check if Docker daemon is running
     */
    public static boolean isDockerRunning() {
        try {
            Process process = Runtime.getRuntime().exec(new String[] { "docker", "info" });
            process.waitFor();
            return process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Attempt to start Docker Desktop (macOS and Linux)
     * Returns true if start command was executed, false otherwise
     */
    public static boolean tryStartDocker() {
        String os = System.getProperty("os.name").toLowerCase();

        try {
            if (os.contains("mac")) {
                // macOS: Open Docker Desktop app
                Runtime.getRuntime().exec(new String[] { "open", "-a", "Docker" });
                return true;

            } else if (os.contains("linux")) {
                // Linux: Try systemctl (requires sudo, may not work)
                // Most users will need to start Docker manually
                try {
                    Runtime.getRuntime().exec(new String[] { "systemctl", "start", "docker" });
                    return true;
                } catch (Exception e) {
                    return false;
                }

            } else if (os.contains("win")) {
                // Windows: Start Docker Desktop
                Runtime.getRuntime().exec(new String[] { "cmd", "/c", "start", "", "Docker Desktop" });
                return true;
            }
        } catch (Exception e) {
            return false;
        }

        return false;
    }

    /**
     * Get Docker Desktop download URL for current OS
     */
    public static String getDockerDownloadUrl() {
        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("mac")) {
            // Detect Apple Silicon vs Intel
            String arch = System.getProperty("os.arch").toLowerCase();
            if (arch.contains("aarch64") || arch.contains("arm")) {
                return "https://desktop.docker.com/mac/main/arm64/Docker.dmg";
            } else {
                return "https://desktop.docker.com/mac/main/amd64/Docker.dmg";
            }
        } else if (os.contains("win")) {
            return "https://desktop.docker.com/win/main/amd64/Docker%20Desktop%20Installer.exe";
        } else if (os.contains("linux")) {
            return "https://docs.docker.com/desktop/install/linux-install/";
        }

        return "https://www.docker.com/products/docker-desktop/";
    }

    /**
     * Get OS-specific installation instructions
     */
    public static String getInstallationInstructions() {
        String os = System.getProperty("os.name").toLowerCase();
        StringBuilder sb = new StringBuilder();

        sb.append("Docker Desktop is required for WireUp to function.\n\n");

        if (os.contains("mac")) {
            sb.append("macOS Installation:\n");
            sb.append("1. Download Docker Desktop from:\n");
            sb.append("   ").append(getDockerDownloadUrl()).append("\n");
            sb.append("2. Open the downloaded .dmg file\n");
            sb.append("3. Drag Docker to Applications folder\n");
            sb.append("4. Launch Docker Desktop\n");
            sb.append("5. Wait for Docker to start (whale icon in menu bar)\n");
            sb.append("6. Reload this Burp extension\n");

        } else if (os.contains("win")) {
            sb.append("Windows Installation:\n");
            sb.append("1. Download Docker Desktop from:\n");
            sb.append("   ").append(getDockerDownloadUrl()).append("\n");
            sb.append("2. Run the installer\n");
            sb.append("3. Follow the installation wizard\n");
            sb.append("4. Restart your computer if prompted\n");
            sb.append("5. Launch Docker Desktop\n");
            sb.append("6. Reload this Burp extension\n");

        } else if (os.contains("linux")) {
            sb.append("Linux Installation:\n");
            sb.append("Visit: ").append(getDockerDownloadUrl()).append("\n");
            sb.append("Or use your package manager:\n");
            sb.append("  Ubuntu/Debian: sudo apt install docker.io\n");
            sb.append("  Fedora: sudo dnf install docker\n");
            sb.append("  Arch: sudo pacman -S docker\n");
            sb.append("\nThen start Docker:\n");
            sb.append("  sudo systemctl start docker\n");
            sb.append("  sudo systemctl enable docker\n");
        }

        return sb.toString();
    }

    /**
     * Get current OS name
     */
    public static String getOsName() {
        return System.getProperty("os.name");
    }
}
