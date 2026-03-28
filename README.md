## StreamIngress

A small, HTTP server and reverse proxy built directly on top of raw TCP sockets. StreamIngress can serve static files and forward requests to backend services, with configuration driven by a simple YAML file.

---

### Features

- **HTTP from raw TCP**
  - Listens on a TCP `ServerSocket`.
  - Parses HTTP request line, headers, and body manually.
  - **Virtual threads** for handling concurrent connections.
  - **HTTP keep-alive** support (HTTP/1.1 keep-alive by default, HTTP/1.0 close by default).
  - Per-connection **request limits** and **socket timeouts** to prevent resource exhaustion.

- **Static file server**
  - Serves files from a configured `root` directory.
  - Optional `index` file per location (e.g. `index.html`).
  - Supports `GET` and `HEAD` for static content.
  - Optional **directory listing** (`directoryListingEnabled: true`).
  - Basic security: canonical path resolution + directory traversal protection.
  - Sends `Content-Type`, `Content-Length`, `Last-Modified`, and `Cache-Control` headers.
  - Supports conditional requests with `If-Modified-Since` / `304 Not Modified` for files and directory listings.
  - Proper `Connection` header handling for keep-alive support.

- **Reverse proxy**
  - Forwards requests to configured `proxyUrl`.
  - Rewrites paths (removes location prefix before forwarding).
  - Forwards headers (skipping hop‑by‑hop ones) and request body.
  - Streams backend response headers and body back to the client.
  - Adds `X-Forwarded-For` and `X-Forwarded-Protocol` style headers.
  - Handles timeouts and connection errors with `502` / `504` HTML error responses.
  - Proper `Connection` header handling for keep-alive support.

- **Health checks**
  - Background task to periodically check each backend.
  - Central registry to mark backends healthy/unhealthy.
  - Proxy layer can short‑circuit with `503` if a backend is marked down.

---

### Logging

StreamIngress writes **access** and **error** logs to the paths configured in `config.yaml`:

- **Access log** (`accessLog`):
  - One line per request.
  - Format: `time=<ISO-8601> ip=<client-ip> method=<HTTP-method> path=<request-path> status=<status-code>`.
  - Includes static file responses, directory listings, proxy responses, and health-check related 503/504s.

- **Error log** (`errorLog`):
  - Records server-side errors with full stack traces.
  - Format: `time=<ISO-8601> level=<SEVERITY> ip=<client-ip> method=<HTTP-method> path=<request-path> error=<exception>`.
  - Also used by background components such as the health checker when backend checks fail.

Both log files are automatically created (along with parent directories) if they do not exist.

---

### Configuration

StreamIngress uses a YAML config (currently loaded via Jackson) to define servers and locations.

Example `config.yaml`:

```yaml
accessLog: C:\tiny-server\access.log
errorLog:  C:\tiny-server\error.log

servers:
  - listen: 8090
    serverName: localhost

    locations:
      - path: /
        type: static
        root: C:\tiny-server
        index: index.html

      - path: /images
        type: static
        root: C:\tiny-server

      - path: /assets
        type: static
        root: C:\tiny-server

      - path: /downloads
        type: static
        root: C:\tiny-server
        directoryListingEnabled: true

      - path: /test
        type: proxy
        proxyUrl: http://localhost:3991/

      - path: /test2
        type: proxy
        proxyUrl: http://localhost:3540/
```

Key ideas:

- **`listen`**: TCP port to bind to.
- **`type: static`**: serve files from `root`.
- **`type: proxy`**: forward to `proxyUrl`.
- **`directoryListingEnabled`**: automatically generate a listing for directories.

---

### Running

1. **Build** (Maven):

   ```bash
   mvn clean package
   ```

2. **Run** (from the project directory):

   ```bash
   java -cp target/HttpFromTcp-1.0-SNAPSHOT.jar org.example.Main
   ```

   > Note: currently the config path is hard‑coded in `Main`, so adjust it or refactor to read from a relative path/CLI argument.

   3. **Test**

      - Open `http://localhost:8090/` for static content (based on your config).
      - Call `http://localhost:8090/test/...` or `/test2/...` to hit proxied backends.
      - Requests to unmatched locations return `404 Not Found`.

---

### Roadmap / Ideas

- **Config improvements**
  - Replace Jackson/YAML with a custom DSL (lexer + parser + AST).
  - Support multiple backends per location and load balancing.

- **Static server enhancements**
  - Range requests.
  - Configurable buffer sizes.

- **Proxy enhancements**
  - Better `X-Forwarded-*` handling.
  - Request/response size limits.
  - HTTPS backends and configurable timeouts.

- **Observability**
  - Proper access/error logging with configurable formats.
  - Simple `/status` endpoint with basic metrics.

