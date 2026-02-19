# Security Requirements Guide

## When to Load This Guide

Load when user mentions:
- Security / authentication
- Credentials / secrets
- PII / sensitive data
- Compliance (GDPR, HIPAA, PCI-DSS)
- Encryption

---

## Authentication Methods

### API Keys

**Use when:** Simple service-to-service auth

**Storage:**
```properties
# NEVER hardcode!
api.key=${vault:secret/api/credentials#key}
```

**Rotation:** Manual or via secrets manager

---

### OAuth2 / JWT

**Use when:** Modern APIs, token-based auth

**Configuration:**
```properties
oauth.tokenUrl=${vault:secret/oauth#tokenUrl}
oauth.clientId=${vault:secret/oauth#clientId}
oauth.clientSecret=${vault:secret/oauth#clientSecret}
```

**Rotation:** Automatic token refresh

---

### Mutual TLS

**Use when:** Certificate-based, high security

**Configuration:**
```properties
kafka.ssl.keystore.location=${vault:secret/kafka#keystorePath}
kafka.ssl.keystore.password=${vault:secret/kafka#keystorePassword}
kafka.ssl.truststore.location=${vault:secret/kafka#truststorePath}
```

---

## Data Protection

### PII (Personal Identifiable Information)

**Common PII Fields:**
- Name, email, phone
- Address, SSN, credit card
- Health records

**Protection:**
```
1. Mask in logs
2. Encrypt in transit (TLS)
3. Encrypt at rest (if stored)
4. Field-level encryption for highly sensitive
```

**Log Masking:**
```properties
logging.mask.fields=email,phone,ssn,creditCard
```

---

### Transport Security

**Always use:**
- HTTPS (not HTTP)
- Kafka with SSL/SASL
- Database with SSL
- TLS 1.2 or higher

**Configuration:**
```properties
# Kafka SSL
camel.component.kafka.securityProtocol=SSL
camel.component.kafka.sslTruststoreLocation=${vault:secret/kafka#truststore}

# HTTP HTTPS only
camel.component.http.sslContextParameters=#sslContextParameters
```

---

### Message Encryption

**End-to-End Encryption:**
```yaml
# Encrypt before sending
- marshal:
    crypto:
      algorithm: AES/CBC/PKCS5Padding
      keyRef: "#encryptionKey"

# Decrypt after receiving
- unmarshal:
    crypto:
      algorithm: AES/CBC/PKCS5Padding
      keyRef: "#encryptionKey"
```

**Field-Level Encryption:**
Encrypt only sensitive fields within message

---

## Secrets Management

### HashiCorp Vault

**Configuration:**
```properties
# Reference secrets from Vault
database.password=${vault:secret/database/credentials#password}
api.key=${vault:secret/api/credentials#key}
```

**Rotation:** Automatic with Vault

---

### AWS Secrets Manager

**Configuration:**
```properties
database.password=${aws-secrets-manager:prod/database/password}
```

---

### Kubernetes Secrets

**Configuration:**
```properties
database.password=${k8s-secret:database-credentials#password}
```

---

## Input Validation

### Schema Validation

**Always validate input:**
```yaml
- to:
    uri: "json-validator:schemas/input-schema.json"
```

**Benefits:**
- Prevent malformed data
- Catch injection attempts
- Enforce contracts

---

### Size Limits

```properties
# Prevent DoS
http.maxRequestSize=1048576  # 1MB
kafka.maxMessageSize=1048576
```

---

### Sanitization

**Prevent injection attacks:**
- SQL injection: Use prepared statements
- XPath injection: Validate/sanitize input
- Script injection: Escape user input

---

## Compliance

### GDPR (General Data Protection Regulation)

**Requirements:**
- Right to be forgotten (data deletion)
- Right to portability (data export)
- Consent management
- Data retention limits

**Implementation:**
```
- Document what PII is collected
- Implement deletion procedures
- Audit logging for PII access
- Data retention policies
```

---

### HIPAA (Health Insurance Portability)

**Requirements:**
- Encryption at rest and in transit
- Access controls and audit trails
- Data integrity controls

**Implementation:**
```
- Encrypt all PHI (Protected Health Information)
- Log all access to PHI
- Implement access controls
- Regular security audits
```

---

### PCI-DSS (Payment Card Industry)

**Requirements:**
- Never store CVV/PIN
- Encrypt cardholder data
- Implement strong access controls
- Regular vulnerability scanning

**Implementation:**
```
- Never log credit card numbers
- Tokenize card data
- Use PCI-compliant payment gateway
- Encrypt card data in transit
```

---

## Security Checklist

- [ ] Authentication method: [API Key | OAuth2 | mTLS]
- [ ] Credentials stored in: [Vault | AWS Secrets Manager | K8s Secrets]
- [ ] Transport security: [TLS/SSL enabled]
- [ ] PII fields identified: [list]
- [ ] PII masked in logs: [Yes/No]
- [ ] Input validation: [Schema validation enabled]
- [ ] Message size limits: [Set]
- [ ] Compliance requirements: [GDPR | HIPAA | PCI-DSS | None]
- [ ] Encryption at rest: [Yes/No, where]
- [ ] Encryption in transit: [Yes - TLS]
- [ ] Audit logging: [Yes/No, what events]
