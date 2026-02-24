package com.ibm.cics.cbgp

import org.gradle.api.Action

open class BundleExtension {

	val build: BundleBuildExtension = BundleBuildExtension()
	val deploy: BundleDeployExtension = BundleDeployExtension()
	val libertyWarUpload: LibertyWarUploadExtension = LibertyWarUploadExtension()

	fun build(action: Action<in BundleBuildExtension>) {
		action.execute(build)
	}

	fun deploy(action: Action<in BundleDeployExtension>) {
		action.execute(deploy)
	}
	
	fun libertyWarUpload(action: Action<in LibertyWarUploadExtension>) {
		action.execute(libertyWarUpload)
	}
}