# CloudHub / Runtime Fabric → Kubernetes Deployment Migration Guide

This guide maps MuleSoft deployment infrastructure to Camel deployment on Kubernetes. Use it during Phase 2 when the BRD identifies deployment target as Kubernetes or when migrating from CloudHub / Runtime Fabric.

---

## Deployment Model Comparison

| Capability | CloudHub | Runtime Fabric | Camel on Kubernetes |
|-----------|----------|---------------|---------------------|
| Runtime | Mule Engine (managed) | Mule Engine (self-hosted) | JVM (Quarkus / Spring Boot) |
| Container orchestration | Proprietary | Kubernetes (licensed) | Kubernetes (open-source) |
| Scaling | vCore-based (licensed) | Replica-based (licensed) | Replica-based (free) |
| Load balancing | Dedicated Load Balancer | Ingress controller | Ingress / Gateway API |
| Secrets | Anypoint Secure Properties | Kubernetes Secrets | Kubernetes Secrets / Vault |
| Config management | Anypoint Properties | ConfigMaps | ConfigMaps / application.properties |
| CI/CD | Anypoint CLI / Maven plugin | Anypoint CLI | Standard Kubernetes tooling |

---

## Runtime Selection

Choose the Camel runtime based on deployment requirements:

| Requirement | Recommended Runtime | Reason |
|------------|--------------------|---------|
| Fast startup, low memory (serverless, scale-to-zero) | Camel Quarkus (native) | GraalVM native image: ~50ms startup, ~30MB RSS |
| Fast startup, JVM mode | Camel Quarkus (JVM) | ~1s startup, smaller footprint than Spring Boot |
| Spring ecosystem (Spring Security, Spring Data, etc.) | Camel Spring Boot | Full Spring integration |
| Lightweight, no framework | Camel Main | Minimal dependencies |

---

## CloudHub Configuration → Kubernetes Mapping

### Scaling & Resources

| CloudHub Setting | Kubernetes Equivalent | Example |
|-----------------|----------------------|---------|
| vCore size (0.1 / 0.2 / 1 / 2 / 4) | `resources.requests` + `resources.limits` | See table below |
| Workers count | `replicas` | `replicas: 3` |
| Auto-restart on failure | `restartPolicy: Always` + liveness probe | Default in Deployments |
| Static IPs | `Service` with `LoadBalancer` type | Or use Ingress with external IP |

### vCore → Kubernetes Resource Mapping

| CloudHub vCore | CPU Request | CPU Limit | Memory Request | Memory Limit |
|---------------|-------------|-----------|----------------|--------------|
| 0.1 | 100m | 200m | 256Mi | 512Mi |
| 0.2 | 200m | 400m | 512Mi | 1Gi |
| 1.0 | 500m | 1000m | 1Gi | 2Gi |
| 2.0 | 1000m | 2000m | 2Gi | 4Gi |
| 4.0 | 2000m | 4000m | 4Gi | 8Gi |

> **Note:** Camel on Quarkus typically uses significantly less memory than the Mule runtime. Start with lower resource limits and adjust based on load testing.

### Properties & Secrets

| Mule Mechanism | Kubernetes Equivalent | Notes |
|---------------|----------------------|-------|
| `mule-artifact.json` secure properties | Kubernetes Secret | Mount as env vars or volume |
| `${mule.env}` environment prefix | ConfigMap per environment | Use Kustomize overlays or Helm values |
| Anypoint Secure Properties (`secure::`) | Kubernetes Secret or HashiCorp Vault | Use `camel-hashicorp-vault` for Vault integration |
| CloudHub environment variables | `env` in Pod spec or ConfigMap | Map to `application.properties` |
| Property placeholders `${property.name}` | `{{property.name}}` in Camel | Camel uses `{{` `}}` for property placeholders |

---

## Kubernetes Manifest Templates

### Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: {route-id}
  labels:
    app: {route-id}
    migrated-from: mulesoft
spec:
  replicas: {worker-count}
  selector:
    matchLabels:
      app: {route-id}
  template:
    metadata:
      labels:
        app: {route-id}
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/path: "/q/metrics"           # Quarkus
        # prometheus.io/path: "/actuator/prometheus"  # Spring Boot
        prometheus.io/port: "8080"
    spec:
      containers:
        - name: {route-id}
          image: {registry}/{route-id}:{version}
          ports:
            - containerPort: 8080
          envFrom:
            - configMapRef:
                name: {route-id}-config
            - secretRef:
                name: {route-id}-secrets
          resources:
            requests:
              cpu: "{cpu-request}"
              memory: "{memory-request}"
            limits:
              cpu: "{cpu-limit}"
              memory: "{memory-limit}"
          livenessProbe:
            httpGet:
              path: /q/health/live
              port: 8080
            initialDelaySeconds: 10
            periodSeconds: 30
          readinessProbe:
            httpGet:
              path: /q/health/ready
              port: 8080
            initialDelaySeconds: 5
            periodSeconds: 10
```

### Service

```yaml
apiVersion: v1
kind: Service
metadata:
  name: {route-id}
spec:
  selector:
    app: {route-id}
  ports:
    - port: 80
      targetPort: 8080
```

### Ingress (for HTTP-exposed routes)

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: {route-id}
  annotations:
    # Map from CloudHub DLB path rewriting if applicable
    nginx.ingress.kubernetes.io/rewrite-target: /
spec:
  rules:
    - host: {domain}
      http:
        paths:
          - path: /{api-path}
            pathType: Prefix
            backend:
              service:
                name: {route-id}
                port:
                  number: 80
```

### ConfigMap

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: {route-id}-config
data:
  application.properties: |
    # Migrated from CloudHub/Runtime Fabric properties
    # camel.component.{component}.{option}={value}
```

### Secret

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: {route-id}-secrets
type: Opaque
stringData:
  # Migrated from Anypoint secure properties
  # db.password: changeme
  # api.key: changeme
```

---

## Container Image Build

### Quarkus (Dockerfile)

```dockerfile
FROM registry.access.redhat.com/ubi8/openjdk-17:latest
COPY target/quarkus-app /deployments/
EXPOSE 8080
```

### Quarkus Native (Dockerfile)

```dockerfile
FROM registry.access.redhat.com/ubi8/ubi-minimal:latest
COPY target/*-runner /application
EXPOSE 8080
ENTRYPOINT ["./application", "-Dquarkus.http.host=0.0.0.0"]
```

### Spring Boot (Dockerfile)

```dockerfile
FROM eclipse-temurin:17-jre
COPY target/*.jar /app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

---

## CI/CD Pipeline Migration

| Anypoint CLI Command | Kubernetes Equivalent |
|---------------------|----------------------|
| `anypoint-cli deploy` | `kubectl apply -f` or `helm upgrade --install` |
| `anypoint-cli runtime-mgr application modify` | `kubectl set image` or `kubectl rollout restart` |
| `anypoint-cli runtime-mgr application describe` | `kubectl get deployment` |
| `anypoint-cli runtime-mgr application stop` | `kubectl scale --replicas=0` |

### Typical CI/CD Pipeline Steps

1. `mvn package` (or `mvn package -Dnative` for Quarkus native)
2. `docker build` / `podman build`
3. `docker push` to container registry
4. `kubectl apply` or `helm upgrade --install`

---

## TDD Section: Deployment

When the BRD includes deployment migration requirements, add this section to each TDD file:

```markdown
## Section 5h: Deployment

### Target Infrastructure
| Field | Value |
|-------|-------|
| Runtime | Quarkus / Spring Boot |
| Deployment | Kubernetes |
| Replicas | [from CloudHub worker count] |
| CPU request/limit | [from vCore mapping] |
| Memory request/limit | [from vCore mapping] |

### Migrated Configuration
| CloudHub Property | Kubernetes Mechanism | Key |
|------------------|---------------------|-----|
| [property name] | ConfigMap / Secret | [key name] |

### Container Image
| Field | Value |
|-------|-------|
| Base image | [base image] |
| Registry | [target registry] |
| Build type | JVM / Native |
```
