package com.tomtom;

import hudson.BulkChange;
import hudson.tasks.LogRotator;
import hudson.model.User

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
    def url = new URL(urlString);
    def connection = url.openConnection();
    connection.setConnectTimeout(10 * 1000)
    connection.setReadTimeout(60 * 1000)
    connection.setRequestProperty("Authorization", getBasicAuthenticationHeaderValue(credentialId));

    String line
    StringBuilder builder = new StringBuilder();
    def reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))
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
        case "canary":
            artifactNumToKeep = 50
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
static def getCurrentDependencyVersion(dependency, branch) {
    def url = "https://bitbucket.tomtomgroup.com/projects/NAVAPP/repos/navui-main/raw/Build/versions.properties?at=${branch}"
    def versionsContent = doHttpGetWithBasicAuthentication(url, 'svc_navuibuild')
    def props = new Properties()
    props.load(new StringReader(versionsContent))
    return props[dependency]
}