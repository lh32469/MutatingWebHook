# Raven URL Mutating Webhook - Spring Boot Implementation

This is a Spring Boot 3.5 (Java 25) implementation of a Kubernetes mutating webhook that automatically injects the environment variable `RAVEN_URLS=foo` into all containers in Kubernetes pods.

## Technology Stack

- **Spring Boot**: 3.5.0-SNAPSHOT
- **Java**: 25
- **Kubernetes Client**: 21.0.1
- **Build Tool**: Maven

## Project Structure

```
.
├── src/
│   ├── main/
│   │   ├── java/com/example/ravenwebhook/
│   │   │   ├── RavenWebhookApplication.java       # Main application class
│   │   │   ├── controller/
│   │   │   │   └── WebhookController.java         # REST controller for webhook endpoints
│   │   │   ├── model/
│   │   │   │   ├── AdmissionReview.java           # Kubernetes AdmissionReview model
│   │   │   │   └── PatchOperation.java            # JSON Patch operation model
│   │   │   └── service/
│   │   │       └── PodMutationService.java        # Business logic for pod mutation
│   │   └── resources/
│   │       └── application.yaml                   # Spring Boot configuration
│   └── test/
│       └── java/com/example/ravenwebhook/
│           └── service/
│               └── PodMutationServiceTest.java    # Unit tests
├── pom.xml                                        # Maven build configuration
├── Dockerfile-spring                              # Docker build file
├── generate-certs-spring.sh                       # Certificate generation script
├── webhook-deployment-spring.yaml                 # Kubernetes deployment manifest
└── mutating-webhook-config.yaml                   # MutatingWebhookConfiguration
```

## Prerequisites

- JDK 25 (or JDK 21+ for compatibility)
- Maven 3.9+
- Docker
- Kubernetes cluster (1.16+)
- kubectl configured to access your cluster
- openssl (for certificate generation)

## Building the Application

### Local Build

```bash
# Build with Maven
mvn clean package

# Run locally (requires keystore)
java -jar target/raven-webhook-1.0.0.jar
```

### Docker Build

```bash
# Build the Docker image
docker build -f Dockerfile-spring -t your-registry/raven-url-webhook-spring:latest .

# Push to your registry
docker push your-registry/raven-url-webhook-spring:latest
```

Update `webhook-deployment-spring.yaml` with your actual image registry.

## Deployment Guide

### 1. Generate TLS Certificates

The webhook requires TLS certificates in PKCS12 format for Spring Boot:

```bash
chmod +x generate-certs-spring.sh
./generate-certs-spring.sh
```

This script will:
- Generate a CA certificate and server certificate
- Convert certificates to PKCS12 keystore format
- Create a Kubernetes secret with the certificates
- Output the CA bundle for the webhook configuration

### 2. Update the Webhook Configuration

Copy the CA bundle output from the previous step and update the `caBundle` field in `mutating-webhook-config.yaml`:

```yaml
clientConfig:
  caBundle: <PASTE_CA_BUNDLE_HERE>
```

### 3. Deploy the Webhook Server

```bash
# Deploy the webhook
kubectl apply -f webhook-deployment-spring.yaml

# Wait for the deployment to be ready
kubectl wait --for=condition=available --timeout=120s deployment/raven-url-webhook

# Check the logs
kubectl logs -l app=raven-url-webhook -f
```

### 4. Install the MutatingWebhookConfiguration

```bash
kubectl apply -f mutating-webhook-config.yaml
```

## Testing

### Run Unit Tests

```bash
mvn test
```

### Test the Webhook in Kubernetes

Create a test pod to verify the webhook is working:

```bash
# Create a test pod
kubectl run test-pod --image=nginx --restart=Never

# Check if the environment variable was injected
kubectl get pod test-pod -o jsonpath='{.spec.containers[0].env[?(@.name=="RAVEN_URLS")].value}'
# Expected output: foo

# Inspect the full environment variables
kubectl get pod test-pod -o yaml | grep -A 5 "env:"

# Clean up
kubectl delete pod test-pod
```

### Health Check Endpoints

The application exposes Spring Boot Actuator endpoints:

```bash
# Health check
curl -k https://raven-url-webhook.default.svc.cluster.local/health

# Actuator health endpoints
curl -k https://raven-url-webhook.default.svc.cluster.local/actuator/health
curl -k https://raven-url-webhook.default.svc.cluster.local/actuator/health/liveness
curl -k https://raven-url-webhook.default.svc.cluster.local/actuator/health/readiness
```

## Configuration

### Application Properties

The webhook can be configured via `src/main/resources/application.yaml`:

```yaml
server:
  port: 8443                                      # HTTPS port
  ssl:
    enabled: true
    key-store: file:/etc/webhook/certs/keystore.p12
    key-store-password: changeit                  # Keystore password
    key-store-type: PKCS12
    key-alias: webhook

logging:
  level:
    com.example.ravenwebhook: INFO                # Application logging level
```

### Environment Variables

You can override configuration using environment variables:

```yaml
env:
  - name: SERVER_PORT
    value: "8443"
  - name: LOGGING_LEVEL_COM_EXAMPLE_RAVENWEBHOOK
    value: "DEBUG"
  - name: SERVER_SSL_KEY_STORE_PASSWORD
    valueFrom:
      secretKeyRef:
        name: raven-url-webhook-certs
        key: keystore-password
```

### Modifying the Injected Environment Variable

To change the injected environment variable, edit `PodMutationService.java`:

```java
private static final String ENV_VAR_NAME = "RAVEN_URLS";
private static final String ENV_VAR_VALUE = "foo";  // Change this value
```

Then rebuild and redeploy:

```bash
mvn clean package
docker build -f Dockerfile-spring -t your-registry/raven-url-webhook-spring:latest .
docker push your-registry/raven-url-webhook-spring:latest
kubectl rollout restart deployment/raven-url-webhook
```

## Namespace Selector

The webhook is configured to inject the environment variable into all pods except those in namespaces labeled with `raven-url-injection=disabled`.

To disable injection in a namespace:

```bash
kubectl label namespace <namespace-name> raven-url-injection=disabled
```

To enable injection again:

```bash
kubectl label namespace <namespace-name> raven-url-injection-
```

## Troubleshooting

### Check Webhook Server Logs

```bash
kubectl logs -l app=raven-url-webhook -f
```

### Check Pod Status

```bash
kubectl get pods -l app=raven-url-webhook
kubectl describe pod -l app=raven-url-webhook
```

### Verify Webhook Configuration

```bash
kubectl get mutatingwebhookconfiguration raven-url-injector -o yaml
```

### Test Webhook Endpoint Directly

```bash
# From within the cluster
kubectl run -it --rm debug --image=curlimages/curl --restart=Never -- \
  curl -k https://raven-url-webhook.default.svc:443/health
```

### Common Issues

**Issue: Pod fails to start with certificate errors**
- Solution: Ensure the keystore is mounted correctly and the password matches

**Issue: Webhook not mutating pods**
- Check webhook configuration is applied: `kubectl get mutatingwebhookconfiguration`
- Verify the service is running: `kubectl get svc raven-url-webhook`
- Check webhook logs for errors

**Issue: Spring Boot version not available**
- Spring Boot 3.5 is currently in snapshot/milestone phase
- For production, use Spring Boot 3.4.x (latest stable) or 3.3.x
- Update `pom.xml` to use a stable version:
  ```xml
  <version>3.4.0</version>
  ```

### Disable the Webhook Temporarily

```bash
kubectl delete mutatingwebhookconfiguration raven-url-injector
```

Re-enable:

```bash
kubectl apply -f mutating-webhook-config.yaml
```

## Performance Considerations

- **Memory**: Spring Boot requires more memory than the Go implementation (256Mi-512Mi recommended)
- **Startup Time**: Spring Boot has a slower startup time (~20-30 seconds)
- **Resource Usage**: Consider adjusting JVM options for optimal performance:
  ```yaml
  env:
    - name: JAVA_OPTS
      value: "-Xmx256m -Xms128m -XX:+UseG1GC"
  ```

## Security Considerations

- The webhook uses `failurePolicy: Ignore`, meaning pod creation will succeed even if the webhook fails
- Certificates are valid for 10 years - consider implementing certificate rotation
- The application runs as a non-root user for security
- Consider using cert-manager for automated certificate management in production
- Spring Boot Actuator endpoints are enabled - restrict access in production if needed

## Development

### Running Locally

To run the webhook locally for development:

1. Generate local certificates in PKCS12 format
2. Update `application.yaml` to point to the local keystore
3. Run the application:
   ```bash
   mvn spring-boot:run
   ```

### Adding New Features

The modular structure makes it easy to extend:
- Add new controllers in `controller/` package
- Add business logic in `service/` package
- Add models in `model/` package
- Add tests in `src/test/java/`

## Uninstallation

```bash
kubectl delete mutatingwebhookconfiguration raven-url-injector
kubectl delete deployment raven-url-webhook
kubectl delete service raven-url-webhook
kubectl delete secret raven-url-webhook-certs
```

## Migration from Go Implementation

If you're migrating from the Go implementation:

1. Both implementations use the same webhook configuration
2. The Spring Boot version requires PKCS12 keystore instead of PEM certificates
3. Use `generate-certs-spring.sh` instead of `generate-certs.sh`
4. Deploy using `webhook-deployment-spring.yaml` instead of `webhook-deployment.yaml`
5. The API and behavior are identical

## License

This is example code for educational purposes.
