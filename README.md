# Raven URL Mutating Webhook

A Kubernetes mutating webhook service built with Spring Boot that automatically injects the `RAVEN_URLS` environment variable into all containers in pods.

## Overview

This webhook intercepts pod creation/update requests in Kubernetes and adds a `RAVEN_URLS=foo` environment variable to all containers (including init containers) that don't already have it defined.

## Technology Stack

- **Spring Boot**: 3.5.0-SNAPSHOT
- **Java**: 25 (Amazon Corretto)
- **Kubernetes Client**: 21.0.1
- **Build Tool**: Maven 3.9+
- **Container Runtime**: Docker

## Project Structure

```
MutatingWebHook/
├── src/
│   ├── main/
│   │   ├── java/com/example/ravenwebhook/
│   │   │   ├── RavenWebhookApplication.java      # Main Spring Boot application
│   │   │   ├── controller/
│   │   │   │   └── WebhookController.java        # REST endpoint for webhook (/mutate)
│   │   │   ├── model/
│   │   │   │   ├── AdmissionReview.java          # K8s AdmissionReview model
│   │   │   │   └── PatchOperation.java           # JSON Patch operations
│   │   │   └── service/
│   │   │       └── PodMutationService.java       # Core mutation logic
│   │   └── resources/
│   │       ├── application.yaml                  # Spring Boot config (SSL on 8085, actuator on 8090)
│   │       └── keystore.p12                      # PKCS12 keystore for TLS
│   └── test/
│       └── java/com/example/ravenwebhook/
│           └── service/
│               └── PodMutationServiceTest.java   # Unit tests
├── Dockerfile                                     # Multi-stage build with Amazon Corretto 25
├── Jenkinsfile                                    # CI/CD pipeline for deployment
├── buildPod.yml                                   # Jenkins agent pod configuration
├── k8s.yml                                        # Kubernetes deployment template
├── mutating-webhook-config.yaml                  # MutatingWebhookConfiguration
├── secrets.yml                                    # JASYPT secret template
├── pom.xml                                        # Maven build configuration
└── README.md                                      # This file
```

## Key Components

### Application Configuration

**Ports:**
- `8085`: Main webhook endpoint (HTTPS with TLS)
- `8090`: Management/actuator endpoints (HTTP, no SSL)

**Endpoints:**
- `POST /mutate` - Webhook mutation endpoint
- `GET /health` - Simple health check
- `GET /actuator/health/liveness` - Kubernetes liveness probe
- `GET /actuator/health/readiness` - Kubernetes readiness probe

### Mutation Logic

The `PodMutationService` creates JSON Patch operations to inject environment variables:
- Processes both regular containers and init containers
- Skips containers that already have `RAVEN_URLS` defined
- Creates the `env` array if it doesn't exist, or appends to existing array

### Webhook Configuration

The webhook is configured to:
- Intercept `CREATE` and `UPDATE` operations on pods
- Only apply to namespaces labeled with `inject-raven-url: enabled`
- Use `failurePolicy: Ignore` (pod creation succeeds even if webhook fails)
- Timeout after 5 seconds
- Use TLS with provided CA bundle

## Build & Deployment

### Local Development

```bash
# Build the project
mvn clean package

# Run tests
mvn test

# Build Docker image
docker build -t raven-webhook:latest .
```

### CI/CD Pipeline (Jenkins)

The Jenkinsfile defines an automated pipeline with these stages:

1. **Setup**: Configures project/branch names and generates K8s manifests from templates
2. **Maven**: Builds the application (`mvn -B package`)
3. **Docker**: Builds and pushes image to `registry.container-registry:5000`
4. **Kubernetes**: Deploys to namespace `{project}-{branch}` and applies webhook config

**Jenkins Build Environment:**
- Maven 3.9 with Amazon Corretto 25
- kubectl for K8s deployments
- Docker client connecting to DinD service

### Kubernetes Deployment

The webhook runs as a deployment with:
- 1 replica
- 100m CPU request
- Health probes on `/actuator/health/liveness` and `/actuator/health/readiness`
- Service exposed on port 8085
- Prometheus metrics scraping enabled

**Environment variables:**
- `JASYPT_ENCRYPTOR_PASSWORD`: Loaded from secret
- `BRANCH`: Current branch name
- `SPRING_PROFILES_ACTIVE`: Set to `k8s`
- `POD_IP`: Pod's IP address

## JVM Configuration

The Dockerfile configures the JVM with:
- **Garbage Collector**: Shenandoah GC with compact object headers
- **Memory**: 2GB max heap (`-Xmx2g`)
- **CPU**: 2 active processors
- **Metaspace**: 25MB initial size with aggressive uncommit
- **XML Processing**: Unlimited entity limits
- **Timezone**: PST8PDT

## Testing the Webhook

### Create a Test Pod

```bash
# Ensure namespace has the required label
kubectl label namespace default inject-raven-url=enabled

# Create a test pod
kubectl run test-pod --image=nginx --restart=Never

# Verify the environment variable was injected
kubectl get pod test-pod -o jsonpath='{.spec.containers[0].env[?(@.name=="RAVEN_URLS")].value}'
# Expected: foo

# Clean up
kubectl delete pod test-pod
```

### Health Checks

```bash
# Direct health check
curl -k https://raven-url-webhook:8085/health

# Actuator endpoints
curl http://raven-url-webhook:8090/actuator/health/liveness
curl http://raven-url-webhook:8090/actuator/health/readiness
```

## Security

- **TLS/SSL**: Required for webhook endpoint (port 8085)
- **Keystore**: PKCS12 format with password `changeit`
- **Non-root**: Application runs as non-root user
- **Secrets**: JASYPT encryption for sensitive data
- **Failure Policy**: Set to `Ignore` to prevent pod creation failures

## Namespace Control

Enable webhook for a namespace:
```bash
kubectl label namespace <namespace-name> inject-raven-url=enabled
```

Disable webhook for a namespace:
```bash
kubectl label namespace <namespace-name> inject-raven-url-
```

## Troubleshooting

### Common Issues

**Issue: Exit code 143 (SIGTERM)**
- This is a graceful shutdown signal, typically from timeout or deployment update
- Check if liveness/readiness probes are timing out
- Review the configured timeout values

**Issue: SSL/TLS errors**
- Verify keystore is mounted at `/etc/webhook/certs/keystore.p12`
- Check keystore password matches configuration
- Ensure CA bundle in webhook config is valid

**Issue: Webhook not mutating pods**
- Verify namespace has label `inject-raven-url: enabled`
- Check webhook configuration: `kubectl get mutatingwebhookconfiguration raven-url-injector`
- Review webhook logs: `kubectl logs -l app=${project} -f`
- Verify service exists: `kubectl get svc mutatingwebhook -n mutatingwebhook-main`

### Debugging Commands

```bash
# Check webhook logs
kubectl logs -n mutatingwebhook-main -l app=mutatingwebhook -f

# Describe the webhook configuration
kubectl describe mutatingwebhookconfiguration raven-url-injector

# Check pod status
kubectl get pods -n mutatingwebhook-main

# Test actuator endpoints
kubectl port-forward -n mutatingwebhook-main deployment/mutatingwebhook 8090:8090
curl http://localhost:8090/actuator/health
```

## Configuration

### Modifying the Injected Variable

To change the environment variable name or value, edit `PodMutationService.java`:

```java
private static final String ENV_VAR_NAME = "RAVEN_URLS";
private static final String ENV_VAR_VALUE = "foo";  // Change this
```

Then rebuild and redeploy through the Jenkins pipeline.

### Adjusting Resource Limits

Edit `k8s.yml` to modify:
- CPU/memory requests and limits
- Replica count
- Health probe intervals
- JVM heap size (in Dockerfile)

## Monitoring

The application exposes Prometheus metrics at:
```
http://mutatingwebhook:8085/actuator/prometheus
```

Metrics are automatically scraped based on service annotations.

## License

This is example code for educational purposes.

## Related Files

- **Jenkinsfile**: Complete CI/CD pipeline automation
- **buildPod.yml**: Jenkins build agent configuration
- **mutating-webhook-config.yaml**: K8s webhook registration
- **k8s.yml**: Deployment and service template
- **secrets.yml**: JASYPT secret template
- **Dockerfile**: Container build with optimized JVM settings
