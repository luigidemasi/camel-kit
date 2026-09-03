# Security Requirements Guide

## When to Load This Guide

Load when user mentions:
- Security / authentication
- Credentials / secrets
- PII / sensitive data
- Compliance (GDPR, HIPAA, PCI-DSS)
- Encryption

Load `shared/camel-security-checklist.md` first. It is the canonical source for the five core security rules and the
configuration snippets (secret references, transport security, log masking, input validation). This guide covers the
design decisions around them — which authentication method, which PII fields, which compliance regime — and records
them in the design spec.

---

## Authentication Methods

### API Keys

**Use when:** Simple service-to-service auth

**Storage:** a secret reference — security checklist rule 1 snippets. Never hardcode.

**Rotation:** Manual or via secrets manager

---

### OAuth2 / JWT

**Use when:** Modern APIs, token-based auth

**Configuration:** token URL, client ID, and client secret as secret references — security checklist rule 1 snippets.

**Rotation:** Automatic token refresh

---

### Mutual TLS

**Use when:** Certificate-based, high security

**Configuration:** client keystore plus truststore (security checklist rule 2 snippets), with locations and passwords as
secret references (rule 1 snippets).

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

**Log Masking:** security checklist rule 3 — enable log masking and mask or omit the canonical PII fields listed there.

---

### Transport Security

Security checklist rule 2 — TLS everywhere: HTTPS, broker SSL/SASL_SSL, database TLS, TLS 1.2 or higher. Configuration
snippets are in the checklist.

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

Pick one secrets manager (environment variables, Kubernetes Secrets, HashiCorp Vault, or AWS Secrets Manager), record
it in the design spec, and reference every secret with the canonical syntax in security checklist rule 1 snippets.

**Rotation:** Vault rotates secrets automatically; record the rotation approach of the chosen store in the design
spec.

---

## Input Validation

Security checklist rule 4 — schema validation at every ingress, message size limits, prepared statements, and no
unsanitized external input in expressions. Snippets are in the checklist. Record in the design spec which schema
validates each external input and where size limits are enforced.

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
- [ ] Credentials stored in: [Environment variables | K8s Secrets | Vault | AWS Secrets Manager]
- [ ] Transport security: [TLS/SSL enabled]
- [ ] PII fields identified: [list]
- [ ] PII masked in logs: [Yes/No]
- [ ] Input validation: [Schema validation enabled]
- [ ] Message size limits: [Set]
- [ ] Compliance requirements: [GDPR | HIPAA | PCI-DSS | None]
- [ ] Encryption at rest: [Yes/No, where]
- [ ] Encryption in transit: [Yes - TLS]
- [ ] Audit logging: [Yes/No, what events]
