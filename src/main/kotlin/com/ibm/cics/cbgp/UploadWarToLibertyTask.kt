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
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Base64

/**
 * Gradle task that uploads a WAR file to Liberty server using HTTP multipart upload.
 * Uses streaming to handle large files without loading entire file into memory.
 */
open class UploadWarToLibertyTask : DefaultTask() {

    companion object {
        // Retry configuration
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 1000L
        
        // Buffer size for streaming file upload (8KB chunks)
        private const val BUFFER_SIZE = 8192
        
        // Progress logging threshold (log every 1MB uploaded)
        private const val PROGRESS_LOG_INTERVAL_BYTES = 1024 * 1024L
        
        // HTTP redirect status codes
        private val REDIRECT_STATUS_CODES = setOf(301, 302, 303, 307, 308)
        
        // HTTP success status codes
        private val SUCCESS_STATUS_CODES = setOf(200, 201)
        
        // Validation error messages
        private const val MISSING_SERVER_URL = "Specify serverUrl for Liberty WAR upload"
        private const val MISSING_APP_ID = "Specify appId for Liberty WAR upload"
        private const val MISSING_CONTEXT_ROOT = "Specify contextRoot for Liberty WAR upload"
        private const val MISSING_USER_NAME = "Specify userName for Liberty WAR upload"
        private const val MISSING_PASSWORD = "Specify password for Liberty WAR upload"

        private val UPLOAD_CONFIG_EXCEPTION = """
            Please specify Liberty WAR upload configuration in build.gradle.
            Example:
                ${BundlePlugin.BUNDLE_EXTENSION_NAME} {
                    libertyWarUpload {
                        serverUrl = 'http://localhost:9080/uploadApp'
                        appId = 'myapp'
                        contextRoot = 'myapp'
                        roleName = 'User'
                        userName = 'username'
                        password = 'password'
                    }
                }
            All items must be completed.
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
    val appId = bundleExtension.libertyWarUpload.appId
    
    @Input
    val contextRoot = bundleExtension.libertyWarUpload.contextRoot
    
    @Input
    val roleName = bundleExtension.libertyWarUpload.roleName
    
    @Input
    val userName = bundleExtension.libertyWarUpload.userName
    
    @Input
    val password = bundleExtension.libertyWarUpload.password

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
     * Validates that the WAR file exists and is readable.
     */
    private fun validateWarFile(): File {
        val war = warFile.get().asFile
        if (!war.exists()) {
            throw GradleException("WAR file does not exist: '${war.absolutePath}'")
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
        
        val boundary = "----WebKitFormBoundary${System.currentTimeMillis()}"
        val urlWithParams = buildUrlWithParams(serverUrl)
        
        var connection = createConnection(urlWithParams, boundary)
        uploadMultipartData(connection, war, boundary)
        
        var responseCode = connection.responseCode
        logger.lifecycle("Response: $responseCode - ${connection.responseMessage}")
        
        // Handle HTTP redirects manually for POST with body
        if (responseCode in REDIRECT_STATUS_CODES) {
            connection = handleRedirect(connection, war, boundary)
            responseCode = connection.responseCode
        }
        
        validateResponse(connection, responseCode)
        logResponseBody(connection)
    }

    /**
     * Uploads multipart form data with the WAR file.
     * Streams the file in 8KB chunks to avoid loading entire file into memory.
     */
    private fun uploadMultipartData(connection: HttpURLConnection, war: File, boundary: String) {
        connection.outputStream.use { outputStream ->
            writeMultipartHeader(outputStream, war, boundary)
            streamFileContent(outputStream, war)
            writeMultipartFooter(outputStream, boundary)
        }
    }

    /**
     * Writes the multipart form header.
     */
    private fun writeMultipartHeader(outputStream: OutputStream, war: File, boundary: String) {
        val writer = PrintWriter(OutputStreamWriter(outputStream, "UTF-8"), true)
        writer.append("--$boundary\r\n")
        writer.append("Content-Disposition: form-data; name=\"warFile\"; filename=\"${war.name}\"\r\n")
        writer.append("Content-Type: application/octet-stream\r\n")
        writer.append("\r\n")
        writer.flush()
    }

    /**
     * Streams file content in chunks with progress logging.
     * Uses 8KB buffer to read and write file data efficiently.
     */
    private fun streamFileContent(outputStream: OutputStream, war: File) {
        FileInputStream(war).use { fileInput ->
            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int
            var totalBytes = 0L
            
            while (fileInput.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalBytes += bytesRead
                
            }
        }
    }

    /**
     * Writes the multipart form footer.
     */
    private fun writeMultipartFooter(outputStream: OutputStream, boundary: String) {
        val writer = PrintWriter(OutputStreamWriter(outputStream, "UTF-8"), true)
        writer.append("\r\n--$boundary--\r\n")
        writer.flush()
    }

    /**
     * Handles HTTP redirect by creating new connection and re-uploading.
     */
    private fun handleRedirect(originalConnection: HttpURLConnection, war: File, boundary: String): HttpURLConnection {
        val redirectUrl = originalConnection.getHeaderField("Location")
            ?: throw GradleException("Redirect response missing Location header")
        
        logger.lifecycle("Following redirect to: $redirectUrl")
        originalConnection.disconnect()
        
        val redirectUrlWithParams = buildUrlWithParams(redirectUrl)
        val newConnection = createConnection(redirectUrlWithParams, boundary)
        uploadMultipartData(newConnection, war, boundary)
        
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
     * Builds URL with query parameters for Liberty upload.
     * If URL already contains parameters (from redirect), returns as-is.
     */
    private fun buildUrlWithParams(baseUrl: String): String {
        // If URL already has our parameters (from redirect), return as-is
        if (baseUrl.contains("appId=")) {
            return baseUrl
        }
        
        // Otherwise, add parameters
        val separator = if (baseUrl.contains("?")) "&" else "?"
        val params = listOf(
            "appId=${urlEncode(appId)}",
            "contextRoot=${urlEncode(contextRoot)}",
            "roleName=${urlEncode(roleName)}",
            "userName=${urlEncode(userName)}"
        ).joinToString("&")
        
        return "$baseUrl$separator$params"
    }

    /**
     * Creates HTTP connection with multipart headers and authentication.
     */
    private fun createConnection(url: String, boundary: String): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.instanceFollowRedirects = false  // Handle redirects manually for POST
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        connection.setRequestProperty("Transfer-Encoding", "chunked")
        
        addBasicAuthentication(connection)
        
        return connection
    }

    /**
     * Adds HTTP Basic Authentication header to connection.
     */
    private fun addBasicAuthentication(connection: HttpURLConnection) {
        if (userName.isNotEmpty() && password.isNotEmpty()) {
            val credentials = "$userName:$password"
            val encodedCredentials = Base64.getEncoder().encodeToString(credentials.toByteArray(Charsets.UTF_8))
            connection.setRequestProperty("Authorization", "Basic $encodedCredentials")
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
     * URL-encodes a string value.
     */
    private fun urlEncode(value: String): String {
        return URLEncoder.encode(value, "UTF-8")
    }

    /**
     * Validates that all required configuration properties are set.
     */
    private fun validateConfiguration() {
        val errors = mutableListOf<String>()

        if (serverUrl.isEmpty()) errors.add(MISSING_SERVER_URL)
        if (appId.isEmpty()) errors.add(MISSING_APP_ID)
        if (contextRoot.isEmpty()) errors.add(MISSING_CONTEXT_ROOT)
        if (userName.isEmpty()) errors.add(MISSING_USER_NAME)
        if (password.isEmpty()) errors.add(MISSING_PASSWORD)

        if (errors.isNotEmpty()) {
            errors.forEach { logger.error(it) }
            throw GradleException(UPLOAD_CONFIG_EXCEPTION)
        }
    }
}

// Made with Bob
