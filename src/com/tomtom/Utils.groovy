package com.tomtom;

import java.nio.file.Paths

@NonCPS
static def getP4Changelist(build) {
    def rawBuild = build.getRawBuild()
    def environment = rawBuild.getEnvironment()
    return environment["P4_CHANGELIST"]
}

@NonCPS
static def getBuildConfig(jobName) {
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
            if (buildConfig['branchName'] == 'develop') {
                buildConfig['nodeType'] = 'fast && nds && italia'
            }
            break;
        case "slow-build":
            buildConfig['testSuite'] = 'jenkins_slow'
            buildConfig['nodeType'] = 'slow && nds'
            if (buildConfig['branchName'] == 'develop') {
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

        case "fast-build-mobile":
            buildConfig['testSuite'] = 'jenkins_fast'
            buildConfig['nodeType'] = 'fast && nds'
            break;
    }

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
static def disableConcurrentBuilds() {
    def rootJobs = ['slow-build', 'fast-build']
    Jenkins.instance.items.stream().filter { rootJob ->
        rootJob.name in rootJobs
    }.each { rootJob ->
        rootJob.items.stream().each { job ->
            job.setConcurrentBuild(false)
        }
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
