# Standalone WAR Upload Sample (gradle-warupload-sample)
This sample demonstrates how to upload a WAR file directly to a Liberty server endpoint using the CICS Bundle Gradle Plugin's WAR upload feature. This approach deploys WARs directly to Liberty using the WAR upload REST API and a caller-supplied Liberty `<application>` definition.

## Key Features
- Direct WAR upload to Liberty server (no CICS bundle required)
- Multipart `multipart/form-data` WAR upload with streaming
- Inline `applicationXml` sample configuration by default
- Optional file-based `applicationXmlLocation` support
- Automatic retry with exponential backoff
- HTTP redirect handling
- Basic authentication or JWT Bearer token support
- Progress logging

## Prerequisites
Ensure your Liberty server has the WAR upload feature enabled and configured. You'll need:
- The Liberty server URL endpoint (e.g., `https://your-server:port/com.ibm.cics.wlp.appdeploy/uploadApp`)
- Valid credentials (username/password or JWT Bearer token) with appropriate permissions
- A Liberty `<application ...>` definition, provided inline or from a file

## Using the Sample

### Option 1: Import the Full Sample
1. [Clone the repository](https://github.com/IBM/cics-bundle-gradle.git)
2. Import the sample `samples/gradle-warupload-sample` into your IDE
3. Edit the `libertyWarUpload` configuration in `standalone-warupload-demo/build.gradle`:

```gradle
cicsBundle {
    libertyWarUpload {
        serverUrl = project.findProperty('cicsServerUrl') ?: 'https://your-server:port/com.ibm.cics.wlp.appdeploy/uploadApp'
        userName = project.findProperty('cicsUser') ?: ''
        password = project.findProperty('cicsPassword') ?: ''
        // bearerToken = project.findProperty('cicsToken') ?: ''

        applicationXml = '<application id="standalone-warupload-demo" location="standalone-warupload-demo.war" type="war"><context-root>standalone-warupload</context-root></application>'
        // applicationXmlLocation = 'src/main/resources/application.xml'
    }
}
```

### Option 2: Add to an Existing Gradle WAR Project
If you have an existing Gradle WAR project, add the CICS Bundle plugin and configure the WAR upload:

```gradle
plugins {
    id 'com.ibm.cics.bundle' version '1.0.8'
    id 'war'
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

cicsBundle {
    libertyWarUpload {
        serverUrl = project.findProperty('cicsServerUrl') ?: 'https://your-server:port/com.ibm.cics.wlp.appdeploy/uploadApp'
        userName = project.findProperty('cicsUser') ?: ''
        password = project.findProperty('cicsPassword') ?: ''
        // bearerToken = project.findProperty('cicsToken') ?: ''

        applicationXml = '<application id="myapp" location="myapp.war" type="war"><context-root>/myapp</context-root></application>'
        // applicationXmlLocation = 'src/main/resources/application.xml'
    }
}
```


## Building and Uploading

### Build the WAR file
```bash
./gradlew build
```

### Upload WAR to Liberty
```bash
./gradlew uploadWarToLiberty
```

The upload task will:
1. Build the WAR file (if not already built)
2. Resolve the Liberty `<application ...>` XML from inline configuration or a file
3. Upload the WAR to the configured Liberty server endpoint as a `multipart/form-data` request
4. Send `applicationXml` as a named multipart text part (`text/xml`)
5. Handle HTTP redirects automatically
6. Retry on failure (up to 3 attempts)
7. Display upload progress and server response

### Example Output
```
=== Upload WAR to Liberty ===
Uploading WAR file: standalone-warupload-demo.war
File size: 0.04 MB
Target server: https://your-server:port/com.ibm.cics.wlp.appdeploy/uploadApp
Using Basic Authentication
Response: 200 - OK
Server response: Application uploaded and configured successfully.
✓ WAR file uploaded successfully!
```

## Configuration Options

| Property | Required | Description | Default | Example |
|----------|----------|-------------|---------|---------|
| `serverUrl` | Yes | Liberty server upload endpoint | - | `https://your-server:port/com.ibm.cics.wlp.appdeploy/uploadApp` |
| `applicationXml` | Conditional** | Full Liberty `<application ...>` XML sent inline | - | `<application id="myapp" ...>` |
| `applicationXmlLocation` | Conditional** | Path to a file containing Liberty `<application ...>` XML | - | `src/main/resources/application.xml` |
| `userName` | Conditional* | Authentication username | - | `admin` |
| `password` | Conditional* | Authentication password | - | `password` |
| `bearerToken` | Conditional* | JWT Bearer token | - | `eyJhbGc...` |
| `connectTimeout` | No | Connection timeout in milliseconds | `30000` (30 seconds) | `60000` |
| `readTimeout` | No | Read timeout in milliseconds | `300000` (5 minutes) | `600000` |

*Either `userName`/`password` OR `bearerToken` must be provided. For no-security mode, all authentication fields can be omitted.
**Either `applicationXml` or `applicationXmlLocation` must be provided. If both are set, inline `applicationXml` takes precedence.

### Timeout Configuration

The WAR upload task supports configurable timeout settings to handle different network conditions and large file uploads:

- **`connectTimeout`**: Time in milliseconds to wait for establishing a TCP connection to the Liberty server (default: 30000ms = 30 seconds)
- **`readTimeout`**: Time in milliseconds to wait for the server response after sending the request (default: 300000ms = 5 minutes)

These timeouts can be adjusted based on your network conditions and file sizes. For example, if uploading very large WAR files over slower networks, you may need to increase the `readTimeout`:

```gradle
cicsBundle {
    libertyWarUpload {
        serverUrl = 'https://your-server:port/com.ibm.cics.wlp.appdeploy/uploadApp'
        userName = 'admin'
        password = 'password'
        applicationXml = '<application id="myapp" location="myapp.war" type="war"><context-root>/myapp</context-root></application>'
        
        // Increase timeouts for large files or slow networks
        connectTimeout = 60000   // 60 seconds to establish connection
        readTimeout = 600000     // 10 minutes to complete upload
    }
}
```

### No Security Configuration

For development environments where the CICS server is configured with `SEC=NO` and no security features are enabled in Liberty:

```gradle
cicsBundle {
    libertyWarUpload {
        serverUrl = 'http://localhost:9080/com.ibm.cics.wlp.appdeploy/uploadApp'
        applicationXml = '<application id="myapp" location="myapp.war" type="war"><context-root>/myapp</context-root></application>'
        // No userName, password, or bearerToken - request sent without authentication
        
        // Optional: Configure timeouts
        connectTimeout = 60000   // 60 seconds to establish connection
        readTimeout = 600000     // 10 minutes to complete upload
    }
}
```

**Server Requirements:**
- CICS configured with `SEC=NO` in SIT parameters
- Liberty server.xml without security features (`appSecurity-*`, `cicsts:security-1.0`, JWT features)
- Use plain HTTP (not HTTPS) for simplicity.

## Troubleshooting

### SSL Certificate Errors
If you encounter SSL certificate errors, ensure your Java truststore includes the Liberty server's certificate. For development/testing only, you can disable SSL verification (not recommended for production).

### Authentication Failures
- Verify username/password or bearer token are correct
- Ensure the user has appropriate permissions on the Liberty server
- If both Basic Auth and bearer token are configured, bearer token takes precedence

### Connection Timeouts
- Verify the server URL is correct and accessible
- Check network connectivity and firewall rules
- Ensure the Liberty server is running and the upload endpoint is enabled
- For large files or slow networks, increase the `connectTimeout` and `readTimeout` values in your configuration
- Default timeouts: `connectTimeout = 30000ms` (30 seconds), `readTimeout = 300000ms` (5 minutes)

## What's Next
After successful upload, visit your application at:
```
http://your-server:port/your-context-root
```
