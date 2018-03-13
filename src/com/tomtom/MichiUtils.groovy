package com.tomtom;

import groovy.json.JsonSlurper

@NonCPS
private static def getMichiVersions(architecture) {
    def authorization = "Basic bmF2a2l0Ok5hdksxdCQ="
    def url = new URL("http://artifactory-ci.tomtomgroup.com/artifactory/api/search/aql")

    def path = "com.tomtom.navkit.map/TomTom.NavKit.Map.Sdk.Android.aar/android/${architecture}/release/*"
    def name = "TomTom.NavKit.Map.Sdk.Android.aar-android-${architecture}-release-custom-*"

    def body = """items.find({
        "repo": {"\$eq":"michi-release-local"},
        "path": {"\$match":"$path"},
        "name": {"\$match":"$name"}
    })"""

    def connection = url.openConnection()
    connection.setRequestMethod("POST")
    connection.setRequestProperty("Authorization", authorization)
    connection.doOutput = true

    def writer = new OutputStreamWriter(connection.outputStream)
    writer.write(body)
    writer.flush()
    writer.close()
    connection.connect()

    def jsonSlurper = new JsonSlurper()
    def json = jsonSlurper.parseText(connection.content.text)

    return json.results.stream().map {
            it.path.substring(it.path.lastIndexOf("/") + 1)
        }.collect()
}

@NonCPS
static def getLatestVersion(branch) {
    def releaseBranchPattern = "^rel-([0-9]+)\\.([0-9]+)\$"

    // Determine architecture and version pattern
    def architecture = "x86_64"
    def versionPattern = ""
    switch (branch) {
        case "main":
            versionPattern = "^([0-9]+)\$"
            break;
        case "rel-17.6":
            architecture = "armeabi_v7a"
            versionPattern = "^rel-17\\.6-([0-9]+)\$"
            break;
        case ~/$releaseBranchPattern/:
            def matcher = branch =~ /$releaseBranchPattern/
            versionPattern = "^([0-9]+)-${matcher[0][1]}\\.${matcher[0][2]}\$"
            break;
    }

    // Get all versions
    def versions = getMichiVersions(architecture)

    // Sort versions based on pattern
    def sortedVersions = versions.stream()
        .filter { it ==~ versionPattern }
        .collect().sort { (it =~ /${versionPattern}/)[0][1] }

    return (sortedVersions.isEmpty()) ? null : sortedVersions.last()
}

@NonCPS
static def doesVersionExist(version) {
    URL url = new URL("http://artifactory-ci.tomtomgroup.com/artifactory/api/search/versions?repos=navapp-releases&g=com.tomtom.navui.michi&a=TomTomNavKitMapSdk")

    def found = new groovy.json.JsonSlurper().parseText(url.text).results.find { result ->
      result.version == version
    } != null

    return found
}