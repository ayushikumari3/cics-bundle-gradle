# Standalone WAR Upload Sample (gradle-warupload-sample)
This sample demonstrates how to upload a WAR file directly to a Liberty server endpoint using the CICS Bundle Gradle Plugin's WAR upload feature. This approach deploys WARs directly to Liberty using the WAR upload REST API.

## Key Features
- Direct WAR upload to Liberty server (no CICS bundle required)
- HTTP multipart file upload with streaming (handles large files efficiently)
- Automatic retry with exponential backoff
- HTTP redirect handling
- Basic authentication support
- Progress logging

## Prerequisites
Ensure your Liberty server has the WAR upload feature enabled and configured. You'll need:
- The Liberty server URL endpoint (e.g., `http://server:port/com.ibm.cics.wlp.warupload/uploadApp`)
- Valid credentials (username and password) with appropriate permissions
- Application ID and context root for your application

## Using the Sample

### Option 1: Import the Full Sample
1. [Clone the repository](https://github.com/IBM/cics-bundle-gradle.git)
2. Import the sample `samples/gradle-warupload-sample` into your IDE
3. Edit the `libertyWarUpload` configuration in `standalone-warupload-demo/build.gradle`:

```gradle
cicsBundle {
    libertyWarUpload {
        serverUrl = 'http://your-server:port/com.ibm.cics.wlp.warupload/uploadApp'
        appId = 'your-app-id'
        contextRoot = '/your-context-root'
        roleName = 'User'
        userName = 'your-username'
        password = 'your-password'
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
        serverUrl = 'http://your-server:port/com.ibm.cics.wlp.warupload/uploadApp'
        appId = 'your-app-id'
        contextRoot = '/your-context-root'
        roleName = 'User'
        userName = 'your-username'
        password = 'your-password'
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
2. Upload it to the configured Liberty server endpoint
3. Handle HTTP redirects automatically
4. Retry on failure (up to 3 attempts)
5. Display upload progress and server response

### Example Output
```
=== Upload WAR to Liberty ===
Uploading WAR file: standalone-warupload-demo-1.0.0.war
File size: 0.04 MB
Target server: http://server:12372/com.ibm.cics.wlp.warupload/uploadApp
Response: 302 - Found
Following redirect to: https://server:12373/com.ibm.cics.wlp.warupload/uploadApp?appId=...
Redirect response: 200 - OK
Server response: Application uploaded and configured successfully.
✓ WAR file uploaded successfully!
```

## Configuration Options

| Property | Required | Description | Example |
|----------|----------|-------------|---------|
| `serverUrl` | Yes | Liberty server upload endpoint | `http://server:9080/uploadApp` |
| `appId` | Yes | Application identifier | `myapp` |
| `contextRoot` | Yes | Application context root | `/myapp` |
| `roleName` | No | Security role name (default: "User") | `User` |
| `userName` | Yes | Authentication username | `admin` |
| `password` | Yes | Authentication password | `password` |


## Troubleshooting

### SSL Certificate Errors
If you encounter SSL certificate errors, ensure your Java truststore includes the Liberty server's certificate. For development/testing only, you can disable SSL verification (not recommended for production).

### Authentication Failures
- Verify username and password are correct
- Ensure the user has appropriate permissions on the Liberty server
- Check that the `roleName` matches the configured security role

### Connection Timeouts
- Verify the server URL is correct and accessible
- Check network connectivity and firewall rules
- Ensure the Liberty server is running and the upload endpoint is enabled

## What's Next
After successful upload, visit your application at:
```
http://your-server:port/your-context-root
```
