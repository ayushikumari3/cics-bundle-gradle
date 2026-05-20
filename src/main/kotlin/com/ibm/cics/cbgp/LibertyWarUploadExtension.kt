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

/**
 * Configuration extension for uploading WAR files directly to Liberty server endpoints.
 * This bypasses CICS bundle creation and deploys WARs directly to Liberty.
 */
open class LibertyWarUploadExtension {
    /**
     * Liberty server endpoint URL (e.g., "http://localhost:9080/uploadApp")
     */
    var serverUrl = ""
    
    /**
     * Full Liberty application XML to send with the WAR upload request.
     * If both applicationXml and applicationXmlLocation are set, applicationXml takes precedence.
     */
    var applicationXml = ""

    /**
     * File path to a Liberty application XML file to send with the WAR upload request.
     */
    var applicationXmlLocation = ""
    
    /**
     * Username for Basic Authentication (optional if using JWT token)
     */
    var userName = ""
    
    /**
     * Password for Basic Authentication (optional if using JWT token)
     */
    var password = ""
    
    /**
     * JWT Bearer token for authentication (alternative to userName/password)
     */
    var bearerToken = ""
    
    /**
     * Connection timeout in milliseconds (default: 30000ms = 30 seconds)
     * Time to wait for establishing TCP connection to the server
     */
    var connectTimeout = 30000
    
    /**
     * Read timeout in milliseconds (default: 300000ms = 5 minutes)
     * Time to wait for server response after sending the request
     */
    var readTimeout = 300000
}