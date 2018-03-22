package com.tomtom;

import java.nio.file.Paths
import hudson.BulkChange;
import hudson.tasks.LogRotator;

@NonCPS
static def getP4Changelist(build) {
    def rawBuild = build.getRawBuild()
    def environment = rawBuild.getEnvironment()
    return environment["P4_CHANGELIST"]
}

@NonCPS
static def getRevisionHashes(build) {
    def hashes = []
    def scmAction = build.getRawBuild().actions.stream().find { action ->
        action instanceof jenkins.scm.api.SCMRevisionAction
    }
    if (scmAction != null) {
        hashes += scmAction.revision.hash
    }
    return hashes
}

@NonCPS
static def getBuildConfig(jobName) {
    return getBuildConfig(jobName, 0)
}

@NonCPS
static def getBuildConfig(jobName, supportedNodeVersion) {
    def buildConfig = [:]

    // Determine root-job name
    buildConfig['rootJob'] = URLDecoder.decode(jobName.substring(0, jobName.indexOf('/')))

    // Determine branch name
    buildConfig['branchName'] = URLDecoder.decode(jobName.substring(jobName.indexOf('/') + 1))

    // Determine test-suite and node-type
    switch (buildConfig['rootJob']) {
        case "fast-build":
            buildConfig['testSuite'] = 'jenkins_fast'
            buildConfig['nodeType'] = 'fast && nds'
            if (buildConfig['branchName'] == 'develop' || buildConfig['branchName'] == 'navkit-canary') {
                buildConfig['nodeType'] = 'fast && nds && italia'
            }
            break;
        case "slow-build":
            buildConfig['testSuite'] = 'jenkins_slow'
            buildConfig['nodeType'] = 'slow && nds'
            if (buildConfig['branchName'] == 'develop' || buildConfig['branchName'] == 'navkit-canary') {
                buildConfig['nodeType'] = 'slow && nds && italia'
            }
            break;

        case "fast-build-asr":
            buildConfig['testSuite'] = 'jenkins_asr_fast'
            buildConfig['nodeType'] = 'asr'
            break;
        case "slow-build-asr":
            buildConfig['testSuite'] = 'jenkins_asr_slow'
            buildConfig['nodeType'] = 'asr'
            break;

        case "fast-build-michi":
            buildConfig['testSuite'] = 'jenkins_michi_fast'
            buildConfig['nodeType'] = 'fast && nds && italia'
            break;
        case "slow-build-michi":
            buildConfig['testSuite'] = 'jenkins_michi_slow'
            buildConfig['nodeType'] = 'slow && nds && italia'
            break;
    }

    // Request a supported node-version
    buildConfig['nodeType'] += " && nodeVersion-${supportedNodeVersion}"

    return buildConfig
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
        builder.append(line);
    }
    reader.close()
    return builder.toString();
}

@NonCPS
static def fixMultiBranchJobProperties() {
    def rootJobs = ['slow-build', 'fast-build', 'fast-build-asr']
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
    def numToKeep = 10
    def artifactDaysToKeep = -1
    def artifactNumToKeep = -1

    def branchName = URLDecoder.decode(job.name)
    switch (branchName) {
        case "develop":
            // Keep develop builds for 100 days
            daysToKeep = 100
            numToKeep = -1
            break
        case ~/rel-[0-9]{2}\.[0-9]/:
            // Keep all builds for release branches
            setStrategy = false
            break;
        case "navkit-canary":
            // Keep 100 builds for the canary branch
            numToKeep = 100
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
