package com.tomtom;

import groovy.json.JsonSlurper

@NonCPS
static def getArtifactUrls(version) {
    return getArtifactUrlsForJob("main-michi-android", version)
}

@NonCPS
static def getArtifactUrlsForJob(jobName, version) {
    def jobJson = new JsonSlurper().parseText(new URL("http://michi-infinity.tomtomgroup.com:8080/job/${jobName}/api/json").text)
    def versionPattern = "^Michi: CL#${version}, NavKit: .*\$"
    def matrixBuild = jobJson.builds.stream().map { build ->
      new JsonSlurper().parseText(new URL("${build.url}/api/json").text)
    }.find { build ->
      build.description ==~ /${versionPattern}/
    }

    def build = matrixBuild.runs.stream().find { build ->
      build.url.contains('MICHI_GENERATOR=Make,MICHI_MODE=Release,MICHI_NODE=ubuntu_host')
    }

    def aarUrl = null
    def apkUrl = null

    def buildJson = new JsonSlurper().parseText(new URL("${build.url}/api/json").text)
    buildJson.artifacts.stream().each { artifact ->
      if (artifact.fileName == 'TomTomNavKitMapSdk.aar' || artifact.fileName == 'TomTomNavKitMapSdk-release.aar') {
        aarUrl = "${build.url}artifact/${artifact.relativePath}"
      } else if (artifact.fileName == 'TomTomNavKitMapReferenceApp.apk' || artifact.fileName == 'TomTomNavKitMapReferenceApp-release.apk') {
        apkUrl = "${build.url}artifact/${artifact.relativePath}"
      }
    }

    return [aarUrl, apkUrl]
}

@NonCPS
static def getLastSuccessfulVersion() {
    return getLastSuccessfulVersionForJob("main-michi-android")
}

@NonCPS
static def getLastSuccessfulVersionForJob(jobName) {
    def jobJson = new JsonSlurper().parseText(new URL("http://michi-infinity.tomtomgroup.com:8080/job/${jobName}/api/json").text)
    def lastSuccessfulBuildJson = new JsonSlurper().parseText(new URL("${jobJson.lastSuccessfulBuild.url}/api/json").text)

    def matcher = lastSuccessfulBuildJson.description =~ /^Michi: CL#([0-9]+), NavKit: (.*)$/
    if (matcher.size() > 0) {
        return matcher[0][1]
    }
    return null
}

@NonCPS
static def doesVersionExist(version) {
    URL url = new URL("http://artifactory-ci.tomtomgroup.com/artifactory/api/search/versions?repos=navapp-releases&g=com.tomtom.michi&a=TomTomNavKitMapSdk")

    def found = new JsonSlurper().parseText(url.text).results.find { result ->
      result.version == version
    } != null

    return found
}