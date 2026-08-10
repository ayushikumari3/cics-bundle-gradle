/*
 * #%L
 * CICS Bundle Gradle Plugin
 * %%
 * Copyright (C) 2026 IBM Corp.
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */
package com.ibm.cics.cbgp

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.*
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

/**
 * Gradle task that uploads a WAR file to Liberty server using multipart/form-data.
 * Sends the WAR binary and applicationXml as separate named parts, streamed without loading into memory.
 */
open class UploadWarToLibertyTask : DefaultTask() {

    companion object {
        // Retry configuration
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 1000L
        
        // Buffer size for streaming file upload (8KB chunks)
        private const val BUFFER_SIZE = 8192
        
        // HTTP redirect status codes
        private val REDIRECT_STATUS_CODES = setOf(301, 302, 303, 307, 308)
        
        // HTTP success status codes
        private val SUCCESS_STATUS_CODES = setOf(200, 201)
        
        // Validation error messages
        private const val MISSING_SERVER_URL = "Specify serverUrl for Liberty WAR upload"
        private const val MISSING_APPLICATION_XML = "Specify applicationXml or applicationXmlLocation for Liberty WAR upload"

        private val UPLOAD_CONFIG_EXCEPTION = """
            Please specify Liberty WAR upload configuration in build.gradle.
            
            Example with Basic Authentication:
                ${BundlePlugin.BUNDLE_EXTENSION_NAME} {
                    libertyWarUpload {
                        serverUrl = 'https://localhost:9080/com.ibm.cics.wlp.appdeploy/uploadApp'
                        applicationXmlLocation = 'src/main/resources/application.xml'
                        userName = 'username'
                        password = 'password'
                    }
                }
            
            Example with JWT Token:
                ${BundlePlugin.BUNDLE_EXTENSION_NAME} {
                    libertyWarUpload {
                        serverUrl = 'https://localhost:9080/com.ibm.cics.wlp.appdeploy/uploadApp'
                        applicationXml = '<application id="myapp" location="myapp.war" type="war">
<context-root>/myapp</context-root>
</application>'
                        bearerToken = 'your-jwt-token'
                    }
                }
            
            Example with No Security (Development Only - requires SEC=NO on server):
                ${BundlePlugin.BUNDLE_EXTENSION_NAME} {
                    libertyWarUpload {
                        serverUrl = 'http://localhost:9080/com.ibm.cics.wlp.appdeploy/uploadApp'
                        applicationXmlLocation = 'src/main/resources/application.xml'
                        // No userName, password, or bearerToken - sends request without authentication
                    }
                }
            """.trimIndent()
    }

    init {
        outputs.upToDateWhen { false }
    }

    @Internal
    val bundleExtension = project.extensions.getByName(BundlePlugin.BUNDLE_EXTENSION_NAME) as BundleExtension
    
    @Input
    val serverUrl = bundleExtension.libertyWarUpload.serverUrl
    
    @Input
    @Optional
    val applicationXml = bundleExtension.libertyWarUpload.applicationXml

    @Input
    @Optional
    val applicationXmlLocation = bundleExtension.libertyWarUpload.applicationXmlLocation

    @Input
    @Optional
    val userName = bundleExtension.libertyWarUpload.userName
    
    @Input
    @Optional
    val password = bundleExtension.libertyWarUpload.password
    
    @Input
    @Optional
    val bearerToken = bundleExtension.libertyWarUpload.bearerToken
    
    @Input
    val connectTimeout = bundleExtension.libertyWarUpload.connectTimeout
    
    @Input
    val readTimeout = bundleExtension.libertyWarUpload.readTimeout

    @InputFile
    val warFile: RegularFileProperty = project.objects.fileProperty()

    @TaskAction
    fun uploadWarToLiberty() {
        logger.lifecycle("=== Upload WAR to Liberty ===")

        validateConfiguration()

        val war = validateWarFile()
        logUploadInfo(war)

        try {
            uploadWarWithRetry(war)
            logger.lifecycle("✓ WAR file uploaded successfully!")
        } catch (e: Exception) {
            throw GradleException("Failed to upload WAR file: ${e.message}", e)
        }
    }

    /**
     * Validates that the WAR file exists and has a .war extension.
     */
    private fun validateWarFile(): File {
        val war = warFile.get().asFile
        if (!war.exists()) {
            throw GradleException("No artifact file found. Run 'gradle build' before 'gradle uploadWarToLiberty'")
        }
        if (!war.name.toLowerCase().endsWith(".war")) {
            val ext = if (war.name.contains(".")) war.name.substring(war.name.lastIndexOf('.')) else "(no extension)"
            throw GradleException("Unsupported file type: $ext. Only .war files are accepted")
        }
        return war
    }

    /**
     * Logs upload information including file name, size, and target server.
     */
    private fun logUploadInfo(war: File) {
        val fileSizeMB = war.length() / (1024.0 * 1024.0)
        logger.lifecycle("Uploading WAR file: ${war.name}")
        logger.lifecycle("File size: %.2f MB".format(fileSizeMB))
        logger.lifecycle("Target server: $serverUrl")
    }

    /**
     * Uploads WAR file with exponential backoff retry logic.
     */
    private fun uploadWarWithRetry(war: File) {
        var lastException: Exception? = null
        
        for (attempt in 1..MAX_RETRY_ATTEMPTS) {
            try {
                uploadWarFile(war)
                return // Success
            } catch (e: Exception) {
                lastException = e
                if (attempt < MAX_RETRY_ATTEMPTS) {
                    val delayMs = RETRY_DELAY_MS * attempt
                    logger.warn("Upload attempt $attempt failed: ${e.message}. Retrying in ${delayMs}ms...")
                    Thread.sleep(delayMs)
                }
            }
        }
        
        throw GradleException("Upload failed after $MAX_RETRY_ATTEMPTS attempts", lastException)
    }

    /**
     * Uploads WAR file using multipart/form-data with streaming.
     * Handles HTTP redirects manually for POST requests.
     */
    private fun uploadWarFile(war: File) {
        val boundary = "----WebKitFormBoundary" + System.currentTimeMillis()
        val appXml = resolveApplicationXml()
        
        var connection = createConnection(serverUrl, boundary)
	    writeMultipartBody(connection, boundary, appXml, war)
	
	    var responseCode = connection.responseCode
	    logger.lifecycle("Response: $responseCode - ${connection.responseMessage}")
	
	    if (responseCode in REDIRECT_STATUS_CODES) {
	        connection = handleRedirect(connection, boundary, appXml, war)
	        responseCode = connection.responseCode
	    }
	
	    validateResponse(connection, responseCode)
	    logResponseBody(connection)
    }

    /**
	 * Writes the multipart/form-data body to the connection output stream.
	 * Sends two parts: "applicationXml" (text/xml) and "warFile" (application/octet-stream).
	 * Streams the WAR binary in 8KB chunks to avoid loading it into memory.
	 */
    private fun writeMultipartBody(connection: HttpURLConnection, boundary: String, appXml: String, war: File) {
    
	    connection.outputStream.use { out ->
	    
	    	// --- Part 1: applicationXml ---
	        out.write("--$boundary\r\n".toByteArray())
	        out.write("Content-Disposition: form-data; name=\"applicationXml\"\r\n".toByteArray())
	        out.write("Content-Type: text/xml; charset=UTF-8\r\n".toByteArray())
	        out.write("\r\n".toByteArray())
	        out.write(appXml.toByteArray(Charsets.UTF_8))
	        out.write("\r\n".toByteArray())
	
	        // --- Part 2: warFile ---
	        out.write("--$boundary\r\n".toByteArray())
	        out.write("Content-Disposition: form-data; name=\"warFile\"; filename=\"${war.name}\"\r\n".toByteArray())
	        out.write("Content-Type: application/octet-stream\r\n".toByteArray())
	        out.write("\r\n".toByteArray())
	
	        FileInputStream(war).use { fileInput ->
	            val buffer = ByteArray(BUFFER_SIZE)
	            var bytesRead: Int
	            var totalBytes = 0L
	            var lastLoggedMB = 0L
	
	            while (fileInput.read(buffer).also { bytesRead = it } != -1) {
	                out.write(buffer, 0, bytesRead)
	                totalBytes += bytesRead
	
	                val currentMB = totalBytes / (1024 * 1024)
	                if (currentMB - lastLoggedMB >= 100) {
	                    logger.lifecycle("Uploaded: ${currentMB}MB")
	                    lastLoggedMB = currentMB
	                }
	            }
	            logger.lifecycle("Total uploaded: %.2f MB".format(totalBytes / (1024.0 * 1024.0)))
	        }
	
	       	out.write("\r\n".toByteArray())
	
	        out.write("--$boundary--\r\n".toByteArray())
	       }
    }
    
    /**
     * Handles HTTP redirect by creating new connection and re-uploading.
     */
    private fun handleRedirect(
        originalConnection: HttpURLConnection,
        boundary: String,
        appXml: String,
        war: File
    ): HttpURLConnection {
        val redirectUrl = originalConnection.getHeaderField("Location")
            ?: throw GradleException("Redirect response missing Location header")
        
        logger.lifecycle("Following redirect to: $redirectUrl")
        originalConnection.disconnect()
        
        val newConnection = createConnection(redirectUrl, boundary)
    	writeMultipartBody(newConnection, boundary, appXml, war)
        
        logger.lifecycle("Redirect response: ${newConnection.responseCode} - ${newConnection.responseMessage}")
        return newConnection
    }

    /**
     * Validates the HTTP response code.
     */
    private fun validateResponse(connection: HttpURLConnection, responseCode: Int) {
        if (responseCode !in SUCCESS_STATUS_CODES) {
            throw GradleException("Upload failed with code $responseCode: ${getErrorMessage(connection)}")
        }
    }

    /**
	 * Creates HTTP connection configured for multipart/form-data upload with authentication.
	 */
    private fun createConnection(url: String, boundary: String): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.instanceFollowRedirects = false  // Handle redirects manually for POST
        
        // Set timeout configurations
        connection.connectTimeout = connectTimeout  // Time to establish connection
        connection.readTimeout = readTimeout       // Time to wait for response
        
        logger.lifecycle("Timeout configuration - Connect: ${connectTimeout}ms, Read: ${readTimeout}ms")
        
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        
        addAuthentication(connection)
        
        return connection
    }
    
    /**
     * Adds authentication header to connection (Basic Auth or Bearer Token).
     * If no credentials are provided, no Authorization header is added.
     */
    private fun addAuthentication(connection: HttpURLConnection) {
        when {
            bearerToken.isNotEmpty() -> {
                // Use JWT Bearer token
                connection.setRequestProperty("Authorization", "Bearer $bearerToken")
                logger.lifecycle("Using JWT Bearer token authentication")
            }
            userName.isNotEmpty() && password.isNotEmpty() -> {
                // Use Basic Authentication
                val credentials = "$userName:$password"
                val encodedCredentials = Base64.getEncoder().encodeToString(credentials.toByteArray(Charsets.UTF_8))
                connection.setRequestProperty("Authorization", "Basic $encodedCredentials")
                logger.lifecycle("Using Basic Authentication")
            }
            else -> {
                // No credentials provided - send request without authentication
                logger.lifecycle("No authentication - sending request without Authorization header")
            }
        }
    }

    /**
     * Logs the HTTP response body if available.
     */
    private fun logResponseBody(connection: HttpURLConnection) {
        val responseBody = try {
            connection.inputStream?.bufferedReader()?.use { it.readText() } ?: ""
        } catch (e: Exception) {
            ""
        }
        
        if (responseBody.isNotEmpty()) {
            logger.lifecycle("Server response: $responseBody")
        }
    }

    /**
     * Extracts error message from HTTP connection.
     */
    private fun getErrorMessage(connection: HttpURLConnection): String {
        val errorStream = connection.errorStream
        return if (errorStream != null) {
            errorStream.bufferedReader().use { it.readText() }
        } else {
            connection.responseMessage
        }
    }

    /**
     * Validates that all required configuration properties are set.
     */
    private fun validateConfiguration() {
        val errors = mutableListOf<String>()

        if (serverUrl.isEmpty()) errors.add(MISSING_SERVER_URL)

        val hasInlineApplicationXml = applicationXml.isNotBlank()
        val hasApplicationXmlLocation = applicationXmlLocation.isNotBlank()

        if (!hasInlineApplicationXml && !hasApplicationXmlLocation) {
            errors.add(MISSING_APPLICATION_XML)
        }

        if (hasInlineApplicationXml && hasApplicationXmlLocation) {
            logger.warn("Both applicationXml and applicationXmlLocation provided. Inline applicationXml will be used.")
        }

        // Authentication is optional - validate only if credentials are provided
        val hasBasicAuth = userName.isNotEmpty() && password.isNotEmpty()
        val hasBearerToken = bearerToken.isNotEmpty()
        val hasPartialBasicAuth = (userName.isNotEmpty() && password.isEmpty()) || (userName.isEmpty() && password.isNotEmpty())
        
        // Only error on partial Basic Auth if there's no bearerToken (since bearerToken takes precedence)
        if (hasPartialBasicAuth && !hasBearerToken) {
            errors.add("Incomplete Basic Authentication. Provide both userName and password, or use bearerToken, or omit all credentials for no-security mode (SEC=NO).")
        }
        
        if (hasBasicAuth && hasBearerToken) {
            logger.warn("Both Basic Auth and Bearer Token provided. Bearer Token will be used.")
        }
        
        if (hasPartialBasicAuth && hasBearerToken) {
            logger.warn("Partial Basic Auth credentials provided but will be ignored. Bearer Token will be used.")
        }
        
        if (!hasBasicAuth && !hasBearerToken && !hasPartialBasicAuth) {
            logger.warn("No authentication credentials provided. Request will be sent without authentication. Server must be configured with SEC=NO.")
        }

        if (errors.isNotEmpty()) {
            errors.forEach { logger.error(it) }
            throw GradleException(UPLOAD_CONFIG_EXCEPTION)
        }
    }

    private fun resolveApplicationXml(): String {
        if (applicationXml.isNotBlank()) {
            return applicationXml.trim()
        }

        var applicationXmlFile = File(applicationXmlLocation)
        if (!applicationXmlFile.isAbsolute) {
            applicationXmlFile = File(project.projectDir, applicationXmlLocation)
        }

        if (!applicationXmlFile.exists()) {
            throw GradleException("applicationXmlLocation does not exist: '${applicationXmlFile.absolutePath}'")
        }

        if (!applicationXmlFile.isFile()) {
            throw GradleException("applicationXmlLocation is not a file: '${applicationXmlFile.absolutePath}'")
        }

        val xml = StringBuilder()
        try {
            BufferedReader(FileReader(applicationXmlFile, Charsets.UTF_8)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    xml.append(line).append('\n')
                }
            }
        } catch (e: Exception) {
            throw GradleException("Failed to read applicationXmlLocation: '${applicationXmlFile.absolutePath}'", e)
        }

        return xml.toString().trim()
    }
}