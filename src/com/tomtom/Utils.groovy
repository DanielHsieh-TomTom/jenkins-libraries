package com.tomtom;

import java.nio.file.Paths
import hudson.BulkChange;
import hudson.tasks.LogRotator;
import hudson.model.Result;

@NonCPS
static def getP4Changelist(build) {
    def rawBuild = build.getRawBuild()
    def environment = rawBuild.getEnvironment()
    return environment["P4_CHANGELIST"]
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
static def getBuildConfig(env, jobName) {
    def buildConfig = [:]

    if (jobName == "custom-fast-build" || jobName == "custom-slow-build") {
        buildConfig['rootJob'] = jobName
        buildConfig['branchName'] = "${env.BRANCH}-custom"
    } else {
        // Determine root-job name
        buildConfig['rootJob'] = URLDecoder.decode(jobName.substring(0, jobName.indexOf('/')))

        // Determine branch name
        buildConfig['branchName'] = URLDecoder.decode(jobName.substring(jobName.indexOf('/') + 1))
    }

    // Determine suite, node-type, timeout and emulator-count
    buildConfig['emulatorCount'] = 0
    buildConfig['timeout'] = 1
    switch (buildConfig['rootJob']) {
        case "fast-build":
        case "custom-fast-build":
            buildConfig['testSuite'] = 'runFastSuite'
            buildConfig['nodeType'] = 'navtest-fast && italia'
            break;
        case "slow-build":
        case "custom-slow-build":
            buildConfig['testSuite'] = 'runSlowSuite'
            buildConfig['nodeType'] = 'navtest-slow && italia'
            buildConfig['timeout'] = 4
            break;
        case "emulator-build":
            buildConfig['testSuite'] = 'runEmulatorSuite'
            buildConfig['nodeType'] = 'emulator'
            buildConfig['emulatorCount'] = 5
            break;
    }

    return buildConfig
}

@NonCPS
static def getSlackConfig(env, currentBuild, rootJob, branchName) {
    def slackConfig = [:]
    slackConfig['mode'] = 'never'

    // Determine the mode and credentialId
    switch (rootJob) {
        case "fast-build":
            switch (branchName) {
                case "canary":
                    slackConfig['mode'] = 'on-change-or-failure'
                    slackConfig['credentialId'] = 'slack_token_private'
                    break;
                case "develop":
                    slackConfig['mode'] = 'on-change-or-failure'
                    slackConfig['credentialId'] = 'slack_token'
                    break;
            }
            break;
        case "slow-build":
            switch (branchName) {
                case "canary":
                    slackConfig['mode'] = 'on-change-or-failure'
                    slackConfig['credentialId'] = 'slack_token_private'
                    break;
                case "develop":
                    slackConfig['mode'] = 'on-change-or-failure'
                    slackConfig['credentialId'] = 'slack_token'
                    break;
            }
            break;
    }

    // Check results
    def passed = currentBuild.resultIsBetterOrEqualTo(hudson.model.Result.SUCCESS.toString())
    def previousPassed = currentBuild.previousBuild == null || currentBuild.previousBuild.resultIsBetterOrEqualTo(hudson.model.Result.SUCCESS.toString())

    // Determine whether to notify
    slackConfig['notify'] = false
    switch (slackConfig['mode']) {
        case "on-change":
            slackConfig['notify'] = passed != previousPassed
            break;
        case "on-failure":
            slackConfig['notify'] = !passed
            break;
        case "on-change-or-failure":
            slackConfig['notify'] = passed != previousPassed || !passed
            break;
        case "never":
            slackConfig['notify'] = false
            break;
    }

    if (slackConfig['notify']) {
        // Determine message
        if (!passed && previousPassed) {
            slackConfig['message'] = "Build failed - ${env.JOB_NAME} ${env.BUILD_NUMBER} (<${env.BUILD_URL}|Open>)"
        } else if (passed && !previousPassed) {
            slackConfig['message'] = "Build back to normal - ${env.JOB_NAME} ${env.BUILD_NUMBER} (<${env.BUILD_URL}|Open>)"
        } else if (!passed && !previousPassed) {
            slackConfig['message'] = "Build still failing - ${env.JOB_NAME} ${env.BUILD_NUMBER} (<${env.BUILD_URL}|Open>)"
        } else {
            slackConfig['message'] = "Build passed - ${env.JOB_NAME} ${env.BUILD_NUMBER} (<${env.BUILD_URL}|Open>)"
        }
    }

    return slackConfig
}

@NonCPS
private static def getCredentials(credentialId) {
    def cred = com.cloudbees.plugins.credentials.CredentialsProvider.lookupCredentials(
            com.cloudbees.plugins.credentials.common.StandardUsernameCredentials.class,
            Jenkins.instance,
            null,
            null
    ).find { cred -> cred.id == credentialId };

    def credentials = null
    if (cred != null) {
        credentials = [:]
        credentials['username'] = cred.username
        credentials['password'] = cred.password
    }
    return credentials
}

@NonCPS
private static def getBasicAuthenticationHeaderValue(credentialId) {
    def credentials = getCredentials(credentialId)

    def headerValue = null
    if (credentials != null) {
        def s = credentials['username'] + ':' + credentials['password']
        headerValue = 'Basic ' + s.bytes.encodeBase64().toString()
    }
    return headerValue
}

@NonCPS
static def doHttpGetWithBasicAuthentication(urlString, credentialId) {
    URL url = new URL(urlString);
    URLConnection conn = url.openConnection();
    conn.setRequestProperty("Authorization", getBasicAuthenticationHeaderValue(credentialId));
    String line
    StringBuilder builder = new StringBuilder();
    def reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))
    while ((line = reader.readLine()) != null) {
        builder.append(line).append("\n");
    }
    reader.close()
    return builder.toString();
}

@NonCPS
static def fixMultiBranchJobProperties() {
    def rootJobs = ['slow-build', 'fast-build']
    Jenkins.instance.items.stream().filter { rootJob ->
        rootJob.name in rootJobs
    }.each { rootJob ->
        rootJob.items.stream().each { job ->
            // Disable concurrent builds
            job.setConcurrentBuild(false)

            // Configure the build retention
            configureBuildRetention(job)
        }
    }
}

@NonCPS
private static def configureBuildRetention(job) {
    def setStrategy = true
    def daysToKeep = -1
    def numToKeep = -1
    def artifactDaysToKeep = 60
    def artifactNumToKeep = 10

    def branchName = URLDecoder.decode(job.name)
    switch (branchName) {
        case "develop":
        case ~/rel-[0-9]{2}\.[0-9]/:
            // Keep all builds for develop & release branches
            setStrategy = false
            break;
        default:
            break;
    }

    BulkChange bc = new BulkChange(job);
    try {
        def propertyExists = job.getProperty(BuildDiscarderProperty.class) != null
        if (propertyExists) {
            job.removeProperty(BuildDiscarderProperty.class)
        }
        if (setStrategy) {
            def strategy = new LogRotator(daysToKeep, numToKeep, artifactDaysToKeep, artifactNumToKeep)
            job.addProperty(new BuildDiscarderProperty(strategy))
        }

        bc.commit();
    } finally {
        bc.abort();
    }
}

@NonCPS
static def getWorkspace(env, rootJob) {
    return Paths.get(new File(env.WORKSPACE).getParent(), rootJob).toString()
}

@NonCPS
static def getParameterOfLastSuccessfulBuild(jobName, parameter) {
    return Jenkins.instance.getItem(jobName).getLastSuccessfulBuild().environment[parameter]
}

@NonCPS
static def addNodeNameToBuildSummary(nodeName, manager) {
    manager.createSummary("computer.png").appendText("Node: ${nodeName}", false, false, false, "#333333")
}

@NonCPS
static def addArtifactLink(manager, label, artifactLink, artifactName) {
    manager.createSummary("clipboard.png").appendText("${label}: <a href=\"${artifactLink}\" target=\"_blank\">${artifactName}</a>", false, false, false, "#333333")
}

@NonCPS
static def getDescriptionOfLastSuccessfulBuild(jobName) {
    return Jenkins.instance.getItem(jobName).getLastSuccessfulBuild().description
}

@NonCPS
static def readProperties(content) {
    Properties properties = new Properties()
    properties.load(new StringReader(content))
    return properties
}

@NonCPS
static def setResult(currentBuild, result) {
    currentBuild.build().@result = Result.fromString(result)
}

@NonCPS
static def getCurrentDependencyVersion(dependency, branch) {
    def url = "https://bitbucket.tomtomgroup.com/projects/NAVAPP/repos/navui-main/raw/Build/versions.properties?at=refs%2Fheads%2F${branch}"
    def versionsContent = doHttpGetWithBasicAuthentication(url, 'svc_navuibuild')
    def props = new Properties()
    props.load(new StringReader(versionsContent))
    return props[dependency]
}