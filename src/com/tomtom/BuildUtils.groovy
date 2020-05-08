package com.tomtom

import hudson.model.Result
import java.nio.file.Paths

@NonCPS
static def addArtifactLink(manager, label, artifactLink, artifactName) {
    manager.createSummary("clipboard.png").appendText("${label}: <a href=\"${artifactLink}\" target=\"_blank\">${artifactName}</a>", false, false, false, "#333333")
}

@NonCPS
static def addNodeNameToBuildSummary(nodeName, manager) {
    manager.createSummary("computer.png").appendText("Node: ${nodeName}", false, false, false, "#333333")
}

@NonCPS
static def getBuildConfig(env, jobName) {
    def buildConfig = [:]

    // Determine root-job and branch-name
    if (jobName == "custom-build" || jobName == "custom-fast-build" || jobName == "custom-slow-build") {
        buildConfig.rootJob = jobName
        buildConfig.branchName = "${env.BRANCH}-custom"
    } else if (jobName == "japan-map-regression") {
        buildConfig.rootJob = jobName
        buildConfig.branchName = "japan-map-regression"
    } else {
        buildConfig.rootJob = URLDecoder.decode(jobName.substring(0, jobName.indexOf('/')))
        buildConfig.branchName = URLDecoder.decode(jobName.substring(jobName.indexOf('/') + 1))
    }

    buildConfig.emulatorCount = 0
    buildConfig.timeout = 1
    buildConfig.runBuild = true
    buildConfig.runFastSuite = true
    switch (buildConfig.rootJob) {
        case "fast-build":
            buildConfig.nodeType = "navtest-fast"
            break;
        case "custom-fast-build":
            buildConfig.nodeType = "navtest-slow"
            break;
        case "custom-build":
            buildConfig.nodeType = "navtest-slow"
            buildConfig.runFastSuite = false
            break;
        case "slow-build":
        case "custom-slow-build":
            buildConfig.nodeType = "navtest-slow && emulator"
            buildConfig.deviceSuite = "runSlowSuite"
            buildConfig.timeout = 2
            buildConfig.emulatorCount = 25
            break;
        case "japan-map-regression":
            buildConfig.nodeType = "navtest-slow && emulator"
            buildConfig.runFastSuite = false
            buildConfig.deviceSuite = "runJapanSuite"
            buildConfig.timeout = 2
            buildConfig.emulatorCount = 10
            break;
        case "italia-build":
            buildConfig.nodeType = "navtest-fast && italia"
            buildConfig.runBuild = false
            buildConfig.runFastSuite = false
            buildConfig.deviceSuite = "runArmOnlySuite"
            break;
        case "korea-build":
            buildConfig.nodeType = "korea"
            buildConfig.deviceSuite = "runKoreaSuite"
            break;
    }

    return buildConfig
}

@NonCPS
static def getBuildStatus(currentBuild) {
    // Check results
    def passed = currentBuild.resultIsBetterOrEqualTo(Result.SUCCESS.toString())
    def previousPassed = currentBuild.previousBuild == null || currentBuild.previousBuild.resultIsBetterOrEqualTo(Result.SUCCESS.toString())

    // Determine message
    def message = ""
    if (!passed && previousPassed) {
        message = "Build failed"
    } else if (passed && !previousPassed) {
        message = "Build back to normal"
    } else if (!passed && !previousPassed) {
        message = "Build still failing"
    } else {
        message = "Build passed"
    }
    return message
}

@NonCPS
static def getRevisionHash(build, scm) {
    if (scm.buildChooser instanceof jenkins.plugins.git.AbstractGitSCMSource.SpecificRevisionBuildChooser) {
        return scm.buildChooser.revision.sha1String
    }

    def buildData = build.rawBuild.actions.stream().find { action ->
        action instanceof hudson.plugins.git.util.BuildData
    }
    if (buildData != null) {
        return buildData.lastBuiltRevision.sha1String
    }
    return null
}

@NonCPS
static def getWorkspace(env, rootJob) {
    return Paths.get(new File(env.WORKSPACE).getParent(), rootJob).toString()
}