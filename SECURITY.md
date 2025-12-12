# WireUp Security Documentation

## Security Architecture

WireUp implements defense-in-depth security measures to protect sensitive WireGuard configurations and ensure safe operation.

## 🔒 Security Features Implemented

### 1. Sensitive Data Protection

#### **Automatic Log Sanitization**
- All log messages automatically sanitized using regex patterns
- WireGuard private keys, public keys, and preshared keys automatically redacted
- Pattern matching for: `PrivateKey`, `PublicKey`, `PresharedKey`, `Password`
- Prevents accidental exposure in Burp Suite extension logs

```java
// Example: Logs show [REDACTED] instead of actual keys
logger.info("Config: PrivateKey = abc123..."); 
// Outputs: "Config: PrivateKey = [REDACTED]"
```

#### **Secure File Handling**
- **Restrictive Permissions**: Config files created with `600` permissions (owner read/write only)
- **Secure Deletion**: 3-pass random data overwrite before file deletion
- **No Path Exposure**: File paths redacted from logs
- **Temporary Storage**: Configs stored in OS temp directory with automatic cleanup

### 2. Container Security

#### **Resource Limits**
Prevents resource exhaustion and DoS attacks:
- **Memory**: 512 MB maximum (no swap)
- **CPU**: 50% of one core maximum
- **Processes**: 100 maximum PIDs
- **Network**: Isolated container network

#### **Minimal Capabilities**
Only required capabilities granted:
- `NET_ADMIN` - Network configuration (required for WireGuard)
- `SYS_MODULE` - Kernel module loading (required for WireGuard)
- **Privileged mode**: Required for WireGuard kernel module, but scoped with resource limits

### 3. Input Validation

#### **Config Validation**
- WireGuard config parsed and validated before use
- Required fields checked (`PrivateKey`, `Address`, `PublicKey`, `Endpoint`)
- Format validation for endpoints (host:port)
- Malformed configs rejected before Docker execution

#### **Path Validation**
- Directory traversal prevention (`..`, `~` rejected)
- Paths validated against temp directory boundaries
- Canonical path checking

#### **String Sanitization**
- Shell metacharacter filtering (`;|&$<>(){}[]!*?~#`)
- Prevents command injection in Docker commands

### 4. Network Isolation

#### **SOCKS5 Binding**
- Proxy binds only to `127.0.0.1` (localhost)
- Not accessible from external networks
- Container network isolated from host

#### **Traffic Isolation**
- Only Burp Suite traffic routes through VPN
- Host system network unaffected
- Docker network namespace separation

### 5. Error Handling

#### **Secure Error Messages**
- Error messages sanitized before display
- No stack traces in production mode
- Sensitive data redacted from exceptions
- Container logs filtered for sensitive info

#### **Graceful Degradation**
- Connection failures don't expose config details
- Container cleanup on all error paths
- Automatic resource cleanup on extension unload

---

## 🛡️ Security Best Practices for Users

### Config Management

**DO**:
- ✅ Store WireGuard configs in encrypted storage (password manager, encrypted drive)
- ✅ Use restrictive file permissions on `.conf` files
- ✅ Rotate keys periodically
- ✅ Use different configs for different purposes
- ✅ Review extension logs periodically

**DON'T**:
- ❌ Commit configs to version control (Git, etc.)
- ❌ Share configs via unencrypted channels (email, Slack)
- ❌ Store configs in shared directories
- ❌ Reuse configs across multiple machines
- ❌ Leave configs in Downloads or Desktop folders

### VPN Provider Selection

Choose VPN providers that:
- Offer WireGuard support
- Have a no-logs policy
- Support modern encryption
- Provide dedicated IP options
- Allow security research use cases

### Testing Environment

- Test extension in isolated environment first
- Use test/dev WireGuard configs for initial testing
- Verify IP isolation before production use
- Monitor Docker resource usage
- Review extension logs for anomalies

---

## 🔍 Security Auditing

### What We Protect Against

| Threat | Mitigation |
|--------|------------|
| **Private Key Exposure** | Automatic log sanitization, secure file deletion |
| **Unauthorized Access** | File permissions (600), localhost-only SOCKS5 |
| **Resource Exhaustion** | Container memory/CPU/PID limits |
| **Command Injection** | Input validation, path checking, string sanitization |
| **Directory Traversal** | Canonical path validation, restricted temp directory |
| **Container Escape** | Resource limits, minimal capabilities, namespaces |
| **Data Recovery** | 3-pass secure file deletion |
| **Network Exposure** | Localhost binding, network isolation |

### Known Limitations

1. **Docker Privileged Mode**
   - Required for WireGuard kernel module
   - Mitigated by resource limits and cleanup
   - Alternative: Use userspace WireGuard (future enhancement)

2. **Temporary Config Storage**
   - Configs briefly stored in temp directory
   - Mitigated by restrictive permissions and secure deletion
   - Alternative: In-memory config passing (future enhancement)

3. **Manual Burp Proxy Config**
   - User must configure upstream proxy manually
   - Burp Montoya API doesn't expose auto-configuration
   - Risk: User error in configuration

---

## 🚨 Incident Response

### If Private Key Compromised

1. **Immediately**:
   - Disconnect from VPN
   - Revoke compromised keys at VPN provider
   - Generate new WireGuard keypair
   - Update all configs

2. **Investigation**:
   - Check extension logs for unauthorized access
   - Review Docker container logs
   - Audit file system for copied configs
   - Check Burp Suite extension history

3. **Prevention**:
   - Enable disk encryption
   - Use stronger VPN provider authentication
   - Implement key rotation schedule
   - Review extension permissions

### If Container Compromise Suspected

1. Stop all containers: `docker stop $(docker ps -q --filter name=wireup)`
2. Remove containers: `docker rm $(docker ps -a -q --filter name=wireup)`
3. Remove image: `docker rmi wireup-vpn:latest`
4. Reinstall extension with fresh build
5. Generate new WireGuard config

---

## 🔐 Cryptographic Details

### WireGuard Security

- **Key Exchange**: Noise protocol framework (IKpsk2)
- **Encryption**: ChaCha20-Poly1305 (AEAD)
- **Key Derivation**: BLAKE2s + HKDF
- **Authentication**: Curve25519 (ECDH)

### Secure Deletion

- **Algorithm**: 3-pass random overwrite
- **Random Source**: `SecureRandom` (cryptographically secure)
- **Buffer Size**: 4096 bytes
- **Effectiveness**: Prevents recovery on most storage media

---

## 📋 Security Checklist for Deployment

Before using WireUp in production:

- [ ] Docker Desktop installed from official source
- [ ] Extension JAR verified (checksum if provided)
- [ ] WireGuard config obtained from trusted provider
- [ ] Config file permissions set to 600
- [ ] Test config in isolated environment first
- [ ] Verify IP isolation (host vs Burp)
- [ ] Review extension logs for initialization
- [ ] Configure Burp upstream proxy correctly
- [ ] Test with known safe target first
- [ ] Monitor Docker resource usage
- [ ] Set up config rotation schedule
- [ ] Document emergency procedures

---

## 🔄 Regular Security Maintenance

### Weekly
- Review extension logs for anomalies
- Check Docker container resource usage
- Verify config file permissions

### Monthly
- Rotate WireGuard keys
- Update extension to latest version
- Review Docker image for updates
- Audit Burp Suite extensions

### Quarterly
- Full security audit of configs
- Review VPN provider security practices
- Update security documentation
- Test disaster recovery procedures

---

## 📞 Reporting Security Issues

If you discover a security vulnerability in WireUp:

1. **DO NOT** open a public GitHub issue
2. Email security details to: [your-security-email]
3. Include:
   - Vulnerability description
   - Steps to reproduce
   - Potential impact
   - Suggested fix (if any)

We will:
- Acknowledge within 48 hours
- Provide timeline for fix
- Credit reporter (if desired)
- Publish security advisory

---

## 📚 References

- [WireGuard Protocol](https://www.wireguard.com/protocol/)
- [Docker Security Best Practices](https://docs.docker.com/engine/security/)
- [OWASP Secure Coding Practices](https://owasp.org/www-project-secure-coding-practices-quick-reference-guide/)
- [Burp Suite Extension Security](https://portswigger.net/burp/documentation/desktop/extensions)

---

**Last Updated**: 2025-11-23
**Security Version**: 1.0
**Reviewed By**: Development Team
