# Envoy Sidecar Authentication

## Overview

The Envoy sidecar pattern externalizes authentication and authorization from application code. Engineers write pure business logic while Envoy handles security at the infrastructure layer.

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                          Kubernetes Pod                          │
│                                                                  │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │                      Envoy Sidecar                        │  │
│  │                                                           │  │
│  │  1. Receive request with Bearer token                     │  │
│  │  2. Call auth service (gRPC)                              │  │
│  │  3. Add user context headers                              │  │
│  │  4. Forward to backend via mTLS                           │  │
│  │                                                           │  │
│  └──────────────────────────┬────────────────────────────────┘  │
│                             │ mTLS (localhost:8090)              │
│                             ▼                                    │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │                    Backend Application                    │  │
│  │                                                           │  │
│  │  - No token validation code                               │  │
│  │  - Trusts X-User-Id, X-User-Roles headers                 │  │
│  │  - Pure business logic                                    │  │
│  │  - Validates client certificate from Envoy                │  │
│  │                                                           │  │
│  └───────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
                             │
                             │ gRPC
                             ▼
              ┌───────────────────────────────┐
              │        Auth Service           │
              │                               │
              │  - JWT validation             │
              │  - PAT lookup                 │
              │  - Role resolution            │
              └───────────────────────────────┘
```

> **Security Note**: The connection between Envoy sidecar and backend uses mTLS. This ensures only the authorized Envoy proxy can communicate with the application, preventing pod-internal attacks where a compromised process might bypass Envoy by connecting directly to the backend port.

## Key Benefit: Pure Application Code

Without Envoy, authentication is embedded in application code:

```kotlin
// ❌ BEFORE: Auth logic in application
@RestController
class WorldController(
    private val jwtService: JwtService,
    private val userService: UserService
) {
    @GetMapping("/worlds/{id}")
    fun getWorld(
        @PathVariable id: String,
        @RequestHeader("Authorization") authHeader: String
    ): World {
        // Manual token validation
        val token = authHeader.removePrefix("Bearer ")
        val claims = jwtService.validateAndParse(token)
            ?: throw UnauthorizedException("Invalid token")

        // Manual user lookup
        val user = userService.findById(claims.userId)
            ?: throw UnauthorizedException("User not found")

        // Check permissions
        if (!user.hasPermission("worlds:read")) {
            throw ForbiddenException("Insufficient permissions")
        }

        return worldService.findById(id)
    }
}
```

With Envoy, application code trusts pre-validated headers:

```kotlin
// ✅ AFTER: Pure business logic
@RestController
class WorldController(private val worldService: WorldService) {

    @GetMapping("/worlds/{id}")
    fun getWorld(
        @PathVariable id: String,
        @RequestHeader("X-User-Id") userId: String,
        @RequestHeader("X-User-Roles") roles: String
    ): World {
        // Headers already validated by Envoy
        return worldService.findById(id)
    }
}
```

## Envoy Configuration

### Basic Configuration

```yaml
# envoy.yaml
static_resources:
  listeners:
  - name: listener_0
    address:
      socket_address:
        address: 0.0.0.0
        port_value: 8080
    filter_chains:
    - filters:
      - name: envoy.filters.network.http_connection_manager
        typed_config:
          "@type": type.googleapis.com/envoy.extensions.filters.network.http_connection_manager.v3.HttpConnectionManager
          stat_prefix: ingress_http
          route_config:
            name: local_route
            virtual_hosts:
            - name: backend_service
              domains: ["*"]
              routes:
              - match:
                  prefix: "/"
                route:
                  cluster: backend_cluster
          http_filters:
          # External authorization filter
          - name: envoy.filters.http.ext_authz
            typed_config:
              "@type": type.googleapis.com/envoy.extensions.filters.http.ext_authz.v3.ExtAuthz
              transport_api_version: V3
              grpc_service:
                envoy_grpc:
                  cluster_name: auth_service
                timeout: 0.5s
              # Headers to send to auth service
              allowed_headers:
                patterns:
                - exact: authorization
                - exact: x-forwarded-for
              # Headers to forward from auth service response
              allowed_upstream_headers:
                patterns:
                - exact: x-user-id
                - exact: x-user-email
                - exact: x-user-roles
                - exact: x-user-name
                - exact: x-authenticated
          - name: envoy.filters.http.router

  clusters:
  # Backend application
  - name: backend_cluster
    connect_timeout: 5s
    type: LOGICAL_DNS
    load_assignment:
      cluster_name: backend_cluster
      endpoints:
      - lb_endpoints:
        - endpoint:
            address:
              socket_address:
                address: localhost  # Same pod
                port_value: 8090

  # Auth service
  - name: auth_service
    connect_timeout: 1s
    type: LOGICAL_DNS
    typed_extension_protocol_options:
      envoy.extensions.upstreams.http.v3.HttpProtocolOptions:
        "@type": type.googleapis.com/envoy.extensions.upstreams.http.v3.HttpProtocolOptions
        explicit_http_config:
          http2_protocol_options: {}
    load_assignment:
      cluster_name: auth_service
      endpoints:
      - lb_endpoints:
        - endpoint:
            address:
              socket_address:
                address: auth-service
                port_value: 9000
```

## Auth Service Implementation

The auth service implements Envoy's external authorization protocol:

```python
# auth_service.py (Python gRPC)
from envoy.service.auth.v3 import external_auth_pb2_grpc
from envoy.service.auth.v3 import external_auth_pb2
import jwt
import grpc

class AuthService(external_auth_pb2_grpc.AuthorizationServicer):

    def Check(self, request, context):
        # Extract authorization header
        auth_header = None
        for header in request.attributes.request.http.headers:
            if header.key.lower() == "authorization":
                auth_header = header.value
                break

        if not auth_header:
            return self._deny("Missing authorization header")

        # Validate token
        token = auth_header.replace("Bearer ", "")
        try:
            claims = jwt.decode(token, JWT_SECRET, algorithms=["HS256"])
        except jwt.InvalidTokenError as e:
            return self._deny(f"Invalid token: {e}")

        # Return OK with user context headers
        return external_auth_pb2.CheckResponse(
            status=status_pb2.Status(code=0),  # OK
            ok_response=external_auth_pb2.OkHttpResponse(
                headers=[
                    {"key": "x-user-id", "value": claims["sub"]},
                    {"key": "x-user-email", "value": claims["email"]},
                    {"key": "x-user-roles", "value": ",".join(claims["roles"])},
                    {"key": "x-authenticated", "value": "true"},
                ]
            )
        )

    def _deny(self, message):
        return external_auth_pb2.CheckResponse(
            status=status_pb2.Status(code=7),  # PERMISSION_DENIED
            denied_response=external_auth_pb2.DeniedHttpResponse(
                status={"code": 401},
                body=json.dumps({"error": message})
            )
        )
```

## Spring Boot Integration

### Trust Headers from Envoy

```kotlin
// EnvoySidecarAuthenticationFilter.kt
@Component
@ConditionalOnProperty("envoy.auth.enabled", havingValue = "true")
class EnvoySidecarAuthenticationFilter(
    private val trustedProxyIps: List<String> = listOf("127.0.0.1", "::1")
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        // Only trust headers from Envoy sidecar
        if (!isFromTrustedProxy(request)) {
            filterChain.doFilter(request, response)
            return
        }

        val authenticated = request.getHeader("X-Authenticated") == "true"
        if (!authenticated) {
            filterChain.doFilter(request, response)
            return
        }

        // Create authentication from headers
        val userId = request.getHeader("X-User-Id")
        val roles = request.getHeader("X-User-Roles")?.split(",") ?: emptyList()

        val auth = EnvoyAuthentication(
            userId = userId,
            email = request.getHeader("X-User-Email"),
            roles = roles,
            name = request.getHeader("X-User-Name")
        )

        SecurityContextHolder.getContext().authentication = auth
        filterChain.doFilter(request, response)
    }

    private fun isFromTrustedProxy(request: HttpServletRequest): Boolean {
        return trustedProxyIps.contains(request.remoteAddr)
    }
}
```

### Configuration

```yaml
# application.yml
envoy:
  auth:
    enabled: true
    trusted-proxy-ips:
      - 127.0.0.1
      - ::1
```

## Headers Reference

| Header | Description | Example |
|--------|-------------|---------|
| `X-User-Id` | Unique user identifier | `user-12345` |
| `X-User-Email` | User's email address | `user@example.com` |
| `X-User-Roles` | Comma-separated roles | `admin,editor` |
| `X-User-Name` | Display name | `John Doe` |
| `X-Auth-Method` | Authentication method used | `jwt` or `pat` |
| `X-Authenticated` | Whether request is authenticated | `true` |

## mTLS: Envoy to Backend

The Envoy sidecar uses mTLS to connect to the backend application. This provides:

1. **Authentication**: Backend verifies Envoy's client certificate
2. **Encryption**: All traffic encrypted even within the pod
3. **Isolation**: Only Envoy can reach the backend port

### Envoy Upstream TLS Configuration

```yaml
# envoy.yaml - backend cluster with mTLS
clusters:
- name: backend_cluster
  connect_timeout: 5s
  type: LOGICAL_DNS
  transport_socket:
    name: envoy.transport_sockets.tls
    typed_config:
      "@type": type.googleapis.com/envoy.extensions.transport_sockets.tls.v3.UpstreamTlsContext
      common_tls_context:
        tls_certificates:
        - certificate_chain:
            filename: /certs/envoy-client.pem
          private_key:
            filename: /certs/envoy-client-key.pem
        validation_context:
          trusted_ca:
            filename: /certs/ca.pem
  load_assignment:
    cluster_name: backend_cluster
    endpoints:
    - lb_endpoints:
      - endpoint:
          address:
            socket_address:
              address: localhost
              port_value: 8090
```

### Spring Boot TLS Configuration

```yaml
# application.yml
server:
  port: 8090
  ssl:
    enabled: true
    client-auth: need  # Require client certificate
    key-store: /certs/backend-keystore.p12
    key-store-password: ${KEYSTORE_PASSWORD}
    key-store-type: PKCS12
    trust-store: /certs/truststore.p12
    trust-store-password: ${TRUSTSTORE_PASSWORD}
    trust-store-type: PKCS12
```

### Certificate Management

Certificates are typically managed by:
- **cert-manager** for automatic issuance and rotation
- **SPIFFE/SPIRE** for workload identity
- **Vault PKI** for enterprise certificate management

```yaml
# Example: cert-manager Certificate resource
apiVersion: cert-manager.io/v1
kind: Certificate
metadata:
  name: envoy-client-cert
spec:
  secretName: envoy-client-tls
  duration: 24h
  renewBefore: 1h
  issuerRef:
    name: internal-ca
    kind: ClusterIssuer
  commonName: envoy-sidecar
  usages:
  - client auth
```

## Kubernetes Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: backend
spec:
  template:
    spec:
      containers:
      # Application container
      - name: app
        image: backend:latest
        ports:
        - containerPort: 8090  # Internal port (mTLS)
        env:
        - name: ENVOY_AUTH_ENABLED
          value: "true"
        - name: KEYSTORE_PASSWORD
          valueFrom:
            secretKeyRef:
              name: backend-tls-passwords
              key: keystore-password
        - name: TRUSTSTORE_PASSWORD
          valueFrom:
            secretKeyRef:
              name: backend-tls-passwords
              key: truststore-password
        volumeMounts:
        - name: backend-certs
          mountPath: /certs
          readOnly: true

      # Envoy sidecar
      - name: envoy
        image: envoyproxy/envoy:v1.28.0
        ports:
        - containerPort: 8080  # External port
        - containerPort: 9901  # Admin
        volumeMounts:
        - name: envoy-config
          mountPath: /etc/envoy
        - name: envoy-certs
          mountPath: /certs
          readOnly: true

      volumes:
      - name: envoy-config
        configMap:
          name: envoy-config
      - name: envoy-certs
        secret:
          secretName: envoy-client-tls
      - name: backend-certs
        secret:
          secretName: backend-server-tls
```

## Benefits Summary

| Concern | Without Envoy | With Envoy |
|---------|---------------|------------|
| Token validation | In every service | Auth service only |
| Auth libraries | Each language SDK | gRPC protocol |
| Role checking | Code changes | Header inspection |
| Token caching | Custom implementation | Envoy/Auth service |
| Observability | Custom logging | Envoy metrics |
| Security updates | Redeploy all services | Update auth service |

## Migration Strategy

1. **Phase 1**: Deploy auth service (no impact to existing services)
2. **Phase 2**: Deploy backend with Envoy sidecar, auth disabled
3. **Phase 3**: Enable sidecar auth for canary deployments
4. **Phase 4**: Full rollout with sidecar enabled
5. **Rollback**: Set `ENVOY_AUTH_ENABLED=false` and redeploy without sidecar
