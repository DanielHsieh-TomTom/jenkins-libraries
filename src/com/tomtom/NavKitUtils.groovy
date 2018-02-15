package com.tomtom;

import groovy.json.JsonSlurper

@NonCPS
private static def getNavKitVersions(architecture) {
    def authorization = "Basic bmF2a2l0Ok5hdksxdCQ="
    def url = new URL("http://artifactory-ci.tomtomgroup.com/artifactory/api/search/aql")

    def path = "com.tomtom/NavKit.FOR.NDS/android/${architecture}/release/*"
    def name = "NavKit.FOR.NDS-android-${architecture}-release-lib-*"

    def body = """items.find({
        "repo": {"\$eq":"navkit-release-local"},
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

    return json.results.sort {
            -Date.parse("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", it.created).getTime()
        }.stream().map {
            it.path.substring(it.path.lastIndexOf("/") + 1)
        }.collect()
}

@NonCPS
static def getNavKitVersion(branch) {
    def versionPattern = "^([0-9]+\\.[0-9]+\\.[0-9]+)\$"
    def architecture = "x86_64"
    switch (branch) {
        case "main":
            // Use defaults
            break;
        case "rel-17.6":
            versionPattern = "^([0-9]+\\.[0-9]+\\.[0-9]+)-17\\.6\$"
            architecture = "armeabi-v7a"
            break;
        case "rel-18.1":
            versionPattern = "^([0-9]+\\.[0-9]+\\.[0-9]+)-18\\.1\$"
            break;
        default:
            return null;
    }

    def versions = getNavKitVersions(architecture)

    def sortedVersion = versions.stream()
        .filter { it ==~ versionPattern }
        .collect().sort { l, r ->
            def lArray = (l =~ versionPattern)[0][1].split(/\./)
            def rArray = (r =~ versionPattern)[0][1].split(/\./)

            def i = 0
            for (i = 0; i < lArray.size() ; i++) {
                def left = lArray[i].toInteger()
                def right = rArray[i].toInteger()
                if (left != right) {
                    return left <=> right
                }
            }
            return 0
        }

    return sortedVersion.last()
}

@NonCPS
static def doesVersionExist(version) {
    URL url = new URL("http://artifactory-ci.tomtomgroup.com/artifactory/api/search/versions?repos=navapp-releases&g=com.tomtom.navui.navkit&a=NavKitNDS")

    def found = new groovy.json.JsonSlurper().parseText(url.text).results.find { result ->
      result.version == version
    } != null

    return found
}