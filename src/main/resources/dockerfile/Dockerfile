FROM alpine:latest

# Install WireGuard, OpenVPN and dependencies
RUN apk add --no-cache \
    wireguard-tools

# Install runtime dependencies including iptables and ethtool
RUN apk add --no-cache \
    openvpn \
    dante-server \
    curl \
    iproute2 \
    wireguard-tools \
    bind-tools \
    netcat-openbsd \
    tcpdump \
    iptables \
    ethtool \
    && rm -rf /var/cache/apk/*

# Create danted user and group (idempotent)
RUN if ! id -u sockd >/dev/null 2>&1; then addgroup -S sockd && adduser -S -G sockd sockd; fi

# Create necessary directories
RUN mkdir -p /etc/wireguard /etc/openvpn

# Copy Dante SOCKS5 configuration
COPY danted.conf /etc/danted.conf

# Create startup script using echo to ensure proper Unix line endings
RUN echo '#!/bin/sh' > /start.sh && \
    echo 'echo "Starting WireUp VPN Adapter..."' >> /start.sh && \
    echo 'mkdir -p /dev/net' >> /start.sh && \
    echo 'if [ ! -c /dev/net/tun ]; then' >> /start.sh && \
    echo '    mknod /dev/net/tun c 10 200' >> /start.sh && \
    echo 'fi' >> /start.sh && \
    echo '' >> /start.sh && \
    echo '# Fix potential Checksum Offloading issues (UDP works, TCP fails)' >> /start.sh && \
    echo 'echo "Applying Network Offload fixes..."' >> /start.sh && \
    echo 'ethtool -K eth0 tx off rx off tso off gso off gro off > /dev/null 2>&1 || echo "Warning: ethtool failed (interface might be virtual)"' >> /start.sh && \
    echo '' >> /start.sh && \
    echo '# Select VPN Mode based on environment variable' >> /start.sh && \
    echo 'if [ "$VPN_TYPE" = "openvpn" ]; then' >> /start.sh && \
    echo '    echo "Starting OpenVPN..."' >> /start.sh && \
    echo '    # Create TUN device' >> /start.sh && \
    echo '    mkdir -p /dev/net' >> /start.sh && \
    echo '    mknod /dev/net/tun c 10 200 || true' >> /start.sh && \
    echo '    # Start OpenVPN in background to capture logs to stdout' >> /start.sh && \
    echo '    # Use mssfix to prevent fragmentation issues' >> /start.sh && \
    echo '    openvpn --config /etc/openvpn/client.conf --mssfix 1000 &' >> /start.sh && \
    echo '    VPN_IFACE="tun0"' >> /start.sh && \
    echo 'else' >> /start.sh && \
    echo '    echo "Starting WireGuard..."' >> /start.sh && \
    echo '    # Strip DNS from config to avoid resolvconf issues in Docker' >> /start.sh && \
    echo '    grep -v "^DNS" /etc/wireguard/wg0.conf > /tmp/wg0-nodns.conf || cp /etc/wireguard/wg0.conf /tmp/wg0-nodns.conf' >> /start.sh && \
    echo '    WG_QUICK_USERSPACE_IMPLEMENTATION=boringtun wg-quick up /tmp/wg0-nodns.conf || { echo "WireGuard failed to start"; exit 1; }' >> /start.sh && \
    echo '    VPN_IFACE="wg0-nodns"' >> /start.sh && \
    echo 'fi' >> /start.sh && \
    echo '' >> /start.sh && \
    echo '# Wait for VPN interface to be created' >> /start.sh && \
    echo 'echo "Waiting for interface $VPN_IFACE..."' >> /start.sh && \
    echo 'timeout=10' >> /start.sh && \
    echo 'while ! ip link show "$VPN_IFACE" > /dev/null 2>&1; do' >> /start.sh && \
    echo '    sleep 1' >> /start.sh && \
    echo '    timeout=$((timeout-1))' >> /start.sh && \
    echo '    if [ "$timeout" -eq 0 ]; then' >> /start.sh && \
    echo '        echo "Timeout waiting for $VPN_IFACE";' >> /start.sh && \
    echo '        echo "--- TOP 20 PROCESSES ---"; ps aux | head -n 20;' >> /start.sh && \
    echo '        echo "--- OPENVPN LOGS ---"; cat /etc/openvpn/openvpn.log 2>/dev/null || echo "No log file";' >> /start.sh && \
    echo '        echo "--- IP ADDR ---"; ip addr;' >> /start.sh && \
    echo '        echo "Entering debug wait loop (container kept alive)...";' >> /start.sh && \
    echo '        while true; do sleep 3600; done' >> /start.sh && \
    echo '    fi' >> /start.sh && \
    echo 'done' >> /start.sh && \
    echo '' >> /start.sh && \
    echo 'echo "VPN interface $VPN_IFACE is up."' >> /start.sh && \
    echo '' >> /start.sh && \
    echo '# Enable Source Port Routing with Dedicated Table' >> /start.sh && \
    echo '# Create table 100 for host traffic' >> /start.sh && \
    echo '# Disable RP Filter to allow asymmetric routing' >> /start.sh && \
    echo 'echo 0 > /proc/sys/net/ipv4/conf/all/rp_filter' >> /start.sh && \
    echo 'echo 0 > /proc/sys/net/ipv4/conf/eth0/rp_filter' >> /start.sh && \
    echo 'ip route show table main | grep "dev eth0" | while read -r line; do' >> /start.sh && \
    echo '    ip route add $line table 100' >> /start.sh && \
    echo 'done' >> /start.sh && \
    echo '# Force SOCKS replies (port 1080) to use table 100' >> /start.sh && \
    echo '# Exempt local/LAN traffic from forcing table 100' >> /start.sh && \
    echo 'ip rule add to 127.0.0.0/8 lookup main priority 80' >> /start.sh && \
    echo 'ip rule add to 10.0.0.0/8 lookup main priority 81' >> /start.sh && \
    echo 'ip rule add to 172.16.0.0/12 lookup main priority 82' >> /start.sh && \
    echo 'ip rule add to 192.168.0.0/16 lookup main priority 83' >> /start.sh && \
    echo 'ip rule add sport 1080 table 100 priority 90' >> /start.sh && \
    echo '' >> /start.sh && \
    echo '# Enable Split Tunneling (Legacy/Backup)' >> /start.sh && \
    echo 'ip rule add to 10.0.0.0/8 table main' >> /start.sh && \
    echo 'ip rule add to 172.16.0.0/12 table main' >> /start.sh && \
    echo 'ip rule add to 192.168.0.0/16 table main' >> /start.sh && \
    echo '' >> /start.sh && \
    echo '# Configure Dante to use correct interfaces' >> /start.sh && \
    echo 'echo "Configuring Dante for $VPN_IFACE..."' >> /start.sh && \
    echo 'VPN_IP=""' >> /start.sh && \
    echo 'ip_wait=0' >> /start.sh && \
    echo 'while [ -z "$VPN_IP" ] && [ $ip_wait -lt 10 ]; do' >> /start.sh && \
    echo '    VPN_IP=$(ip -4 addr show dev "$VPN_IFACE" | grep inet | awk "{print \$2}" | cut -d/ -f1 | head -n 1)' >> /start.sh && \
    echo '    if [ -z "$VPN_IP" ]; then' >> /start.sh && \
    echo '        echo "Waiting for IP on $VPN_IFACE... ($ip_wait/10)"' >> /start.sh && \
    echo '        ip addr show "$VPN_IFACE" || ip link show' >> /start.sh && \
    echo '        sleep 1' >> /start.sh && \
    echo '        ip_wait=$((ip_wait+1))' >> /start.sh && \
    echo '    fi' >> /start.sh && \
    echo 'done' >> /start.sh && \
    echo 'if [ -z "$VPN_IP" ]; then echo "FATAL: Could not detect IP for $VPN_IFACE"; ip addr; sleep 3600; exit 1; fi' >> /start.sh && \
    echo 'echo "Detected VPN IP: $VPN_IP"' >> /start.sh && \
    echo 'echo "Setting MTU to 1200 to prevent fragmentation issues..."' >> /start.sh && \
    echo 'ip link set dev "$VPN_IFACE" mtu 1200' >> /start.sh && \
    echo '# Fix TCP Checksums using iptables (mangle)' >> /start.sh && \
    echo 'echo "Applying TCP Checksum fill..."' >> /start.sh && \
    echo 'iptables -t mangle -A POSTROUTING -p tcp -j CHECKSUM --checksum-fill || echo "Warning: Checksum fill failed"' >> /start.sh && \
    echo 'sed -i "s/internal: eth0/internal: 0.0.0.0/" /etc/danted.conf' >> /start.sh && \
    echo 'sed -i "s/external: wg0/external: $VPN_IP/" /etc/danted.conf' >> /start.sh && \
    echo '# Enable Verbose Logging' >> /start.sh && \
    echo 'sed -i "s/log: error/log: connect disconnect error/" /etc/danted.conf' >> /start.sh && \
    echo '' >> /start.sh && \
    echo '# Test connectivity (UDP vs TCP)' >> /start.sh && \
    echo 'echo "Testing UDP (DNS)..."' >> /start.sh && \
    echo 'nslookup google.com 8.8.8.8 || echo "UDP/DNS FAILED"' >> /start.sh && \
    echo 'echo "Testing TCP (Netcat)..."' >> /start.sh && \
    echo 'nc -z -v -w 5 1.1.1.1 80 || echo "TCP/HTTP FAILED"' >> /start.sh && \
    echo '' >> /start.sh && \
    echo '# Start SOCKS5 proxy' >> /start.sh && \
    echo 'echo "Starting SOCKS5 proxy..."' >> /start.sh && \
    echo '/usr/sbin/sockd -f /etc/danted.conf -D &' >> /start.sh && \
    echo 'sleep 2' >> /start.sh && \
    echo '' >> /start.sh && \
    echo 'echo "Running Internal SOCKS Self-Test..."' >> /start.sh && \
    echo 'curl -v --max-time 5 -x socks5://127.0.0.1:1080 http://1.1.1.1 || echo "SELF-TEST FAILED"' >> /start.sh && \
    echo '' >> /start.sh && \
    echo 'echo "Container Ready. Debug dump:"' >> /start.sh && \
    echo 'ip rule show' >> /start.sh && \
    echo 'ip route show table 100' >> /start.sh && \
    echo 'cat /proc/sys/net/ipv4/conf/eth0/rp_filter' >> /start.sh && \
    echo 'echo "Waiting for traffic..."' >> /start.sh && \
    echo 'tail -f /var/log/sockd.log 2>/dev/null || tail -f /dev/null' >> /start.sh && \
    chmod +x /start.sh

# Set working directory
WORKDIR /

# Expose SOCKS5 port
EXPOSE 1080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=10s --retries=3 \
    CMD wg show wg0 > /dev/null 2>&1 || exit 1

# Start services using explicit shell
CMD ["/bin/sh", "/start.sh"]
