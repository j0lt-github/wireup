# WireUp - VPN Proxy Extension for Burp Suite

<div align="center">

**Route Burp Suite traffic through VPN tunnels with automatic SOCKS5 proxy**

[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Burp Suite](https://img.shields.io/badge/Burp%20Suite-Professional%20%7C%20Community-orange.svg)](https://portswigger.net/burp)
[![Docker](https://img.shields.io/badge/Docker-Required-2496ED.svg)](https://www.docker.com/)

*Created by [j0lt](https://github.com/j0lt-github)*

</div>

---

## Overview

WireUp is a Burp Suite extension that enables seamless VPN integration for penetration testing and security research. It automatically creates isolated Docker containers running VPN clients with SOCKS5 proxies, allowing you to route Burp traffic through VPN tunnels without affecting your host system.

### Key Features

- 🔐 **Multiple VPN Support**: OpenVPN and WireGuard protocols
- 🚀 **One-Click Connection**: Simple UI for connecting/disconnecting
- 🐳 **Docker Isolation**: VPN runs in containers, no host configuration needed
- 🔄 **Auto-Cleanup**: Automatically manages Docker resources
- 📊 **Built-in Logging**: Connection status and diagnostic information
- 🛡️ **Security Hardened**: Resource limits, restrictive permissions, minimal attack surface
- ⚡ **Network Optimizations**: Automatic MTU/MSS handling and TCP checksum fixes

## Prerequisites

- **Burp Suite** (Professional or Community Edition)
- **Docker Desktop** (macOS/Windows) or Docker Engine (Linux)
- **Java 17+** (for compilation)
- **Maven 3.6+** (for building from source)

## Installation

### Option 1: From Release (Recommended)

1. Download the latest `wireup-1.0-SNAPSHOT.jar` from [Releases](../../releases)
2. Open Burp Suite
3. Navigate to **Extensions** → **Installed**
4. Click **Add**
5. Select the downloaded JAR file
6. The **WireUp** tab will appear

### Option 2: Build from Source

```bash
# Clone the repository
git clone https://github.com/yourusername/wireup.git
cd wireup

# Build the extension
mvn clean package

# The JAR will be in target/wireup-1.0-SNAPSHOT.jar
```

Then follow steps 2-6 from Option 1.

## Usage

### Basic Workflow

1. **Obtain VPN Configuration**
   - OpenVPN: Get your `.ovpn` file from your VPN provider
   - WireGuard: Get your `.conf` file or configuration text

2. **Configure WireUp**
   - Open Burp Suite and navigate to the **WireUp** tab
   - Select your VPN type (OpenVPN or WireGuard)
   - Paste your configuration into the text area

3. **Connect**
   - Click **Connect to VPN**
   - Wait for the connection to establish (typically 5-10 seconds)
   - Status will show "Connected" when ready

4. **Configure Burp Proxy**
   - Go to **Proxy** → **Settings** → **Network**
   - Add a new SOCKS proxy:
     - **Host**: `127.0.0.1`
     - **Port**: `1080`
   - Enable the proxy for upstream connections

5. **Test Connection**
   - Browse through Burp Proxy
   - Traffic will now route through the VPN tunnel
   - Verify your IP address has changed

### Advanced Configuration

#### Custom DNS Servers

For OpenVPN, DNS is automatically configured via the VPN server's push options.

For WireGuard, DNS entries in the configuration are automatically handled (stripped and managed internally to avoid Docker conflicts).

#### Connection Management

- **Disconnect**: Click to stop the VPN (with confirmation dialog)
- **Reconnect**: Quick reconnect without re-entering credentials
- **View Logs**: Check extension output in Burp's Extensions tab

## Architecture

```
┌─────────────┐     SOCKS5      ┌──────────────────┐     VPN      ┌─────────────┐
│ Burp Suite  │ ────(1080)────→ │ Docker Container │ ──Tunnel───→ │ VPN Server  │
│             │                  │  - OpenVPN/WG    │              │             │
│             │                  │  - Dante SOCKS5  │              │             │
└─────────────┘                  └──────────────────┘              └─────────────┘
```

WireUp creates an Alpine Linux container with:
- VPN client (OpenVPN or WireGuard)
- Dante SOCKS5 server on port 1080
- Network optimizations (MTU, checksum handling, routing)
- Security restrictions (resource limits, non-root execution where possible)

## Troubleshooting

### Extension Won't Load

**Issue**: Extension fails to load in Burp Suite

**Solutions**:
- Ensure Docker is running: `docker ps`
- Check Java version: `java -version` (must be 17+)
- Review Burp's Extensions → Errors tab for stack traces

### Connection Fails

**Issue**: "Connection failed" error

**Solutions**:
- Verify VPN configuration is valid
- Check Docker has internet access
- Review container logs (click "View Logs" button)
- For OpenVPN: Ensure UDP port 1194 is accessible
- For WireGuard: Verify the endpoint is reachable

### Slow Disconnect

**Issue**: Disconnect button takes time to respond

**Solution**: This is expected as Docker needs to stop and remove containers. The button will show "Disconnecting..." status. A confirmation dialog now prevents accidental clicks.

### SOCKS Proxy Not Working

**Issue**: Burp can't connect to `127.0.0.1:1080`

**Solutions**:
- Verify VPN is connected (check status panel)
- Ensure no other service is using port 1080
- Check firewall isn't blocking localhost connections
- Try reconnecting the VPN

## Security Considerations

Please review [SECURITY.md](SECURITY.md) for important security information, including:
- Docker container isolation
- Resource limits
- Credential storage best practices
- Network exposure considerations

## Development

### Project Structure

```
wireup/
├── src/main/java/com/wireup/
│   ├── WireUpExtension.java      # Main extension entry point
│   ├── docker/                   # Docker management
│   ├── vpn/                      # VPN config handlers
│   ├── ui/                       # Burp UI components
│   └── utils/                    # Logging and utilities
├── src/main/resources/
│   └── dockerfile/               # Docker image configuration
├── pom.xml                       # Maven build configuration
└── README.md                     # This file
```

### Building

```bash
# Compile only
mvn clean compile

# Run tests (if any)
mvn test

# Package JAR
mvn package

# Skip tests (faster)
mvn package -DskipTests
```

### Contributing

Contributions are welcome! Please:
1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Submit a pull request

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Credits

**Created by**: [j0lt](https://github.com/j0lt-github)

Special thanks to:
- [PortSwigger](https://portswigger.net/) for Burp Suite
- [docker-java](https://github.com/docker-java/docker-java) library
- [kylemanna/openvpn](https://github.com/kylemanna/docker-openvpn) Docker image
- [Dante SOCKS server](https://www.inet.no/dante/)

## Disclaimer

This tool is designed for authorized security testing and research purposes only. Users are responsible for complying with all applicable laws and regulations. The author assumes no liability for misuse or damage caused by this software.

---

<div align="center">
Made with ❤️ by j0lt for the security community
</div>
