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
     * Application ID for the WAR deployment
     */
    var appId = ""
    
    /**
     * Context root for the deployed application
     */
    var contextRoot = ""
    
    /**
     * Role name for the deployment (default: "User")
     */
    var roleName = "User"
    
    /**
     * Username for authentication
     */
    var userName = ""
    
    /**
     * Password for authentication
     */
    var password = ""
}