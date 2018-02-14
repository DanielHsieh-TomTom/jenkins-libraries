package com.tomtom;

import groovy.json.JsonSlurper

@NonCPS
static def getLatestVersion(versionPattern) {
    def searchUrl = new URL("http://artifactory-ci.tomtomgroup.com/artifactory/api/search/artifact?repos=michi-release-local&name=TomTom.NavKit.Map.Sdk.Android.aar")
    def searchJson = new JsonSlurper().parseText(searchUrl.text)

    def artifactFileNamePrefix = "TomTom.NavKit.Map.Sdk.Android.aar-android-armeabi-v7a-release-custom-"

    def versions = searchJson.results.stream()
        .filter { it.uri.contains(artifactFileNamePrefix) }
        .map {
            def uri = it.uri
            def startIndex = uri.lastIndexOf(artifactFileNamePrefix) + artifactFileNamePrefix.length()
            def endIndex = uri.size() - 4
            uri.substring(startIndex, endIndex)
        }.filter { it ==~ /${versionPattern}/ }
    .collect().sort { (it =~ /${versionPattern}/)[0][1] }.reverse()

    return (versions.isEmpty()) ? null : versions[0]
}

@NonCPS
static def doesVersionExist(version) {
    URL url = new URL("http://artifactory-ci.tomtomgroup.com/artifactory/api/search/versions?repos=navapp-releases&g=com.tomtom.navui.michi&a=TomTomNavKitMapSdk")

    def found = new JsonSlurper().parseText(url.text).results.find { result ->
      result.version == version
    } != null

    return found
}