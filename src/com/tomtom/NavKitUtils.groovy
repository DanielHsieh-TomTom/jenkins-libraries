package com.tomtom;

import groovy.json.JsonSlurper

@NonCPS
private static def getNavKitVersions(architectures) {
    def authorization = "Basic bmF2a2l0Ok5hdksxdCQ="
    def url = new URL("http://artifactory-ci.tomtomgroup.com/artifactory/api/search/aql")

    def versionLists = architectures.stream().map { architecture ->
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
        connection.setRequestProperty("Content-Type", "text/plain")
        connection.doOutput = true

        def writer = new OutputStreamWriter(connection.outputStream)
        writer.write(body)
        writer.flush()
        writer.close()
        connection.connect()

        def jsonSlurper = new JsonSlurper()
        def json = jsonSlurper.parseText(connection.content.text)

        json.results.stream().map {
            it.path.substring(it.path.lastIndexOf("/") + 1)
        }.collect()
    }.collect()

    return versionLists[0].stream().filter {
        def exists = true
        for (def i = 1;i<versionLists.size;i++) {
            exists = exists && versionLists[i].contains(it)
        }
        exists
    }.collect()
}

@NonCPS
static def getLatestVersion(branch) {
    def releaseBranchPattern = "^rel-([0-9]+)\\.([0-9]+)\$"

    // Determine architecture and version pattern
    def architectures = ["armeabi-v7a", "x86_64"]
    def versionPattern = ""
    switch (branch) {
        case "main":
            versionPattern = "^([0-9]+\\.[0-9]+\\.[0-9]+)\$"
            break;
        case "rel-17.6":
            architectures = ["armeabi-v7a"]
            // fall-through
        case ~/$releaseBranchPattern/:
            def matcher = branch =~ /$releaseBranchPattern/
            versionPattern = "^([0-9]+\\.[0-9]+\\.[0-9]+)-${matcher[0][1]}\\.${matcher[0][2]}\$"
            break;
    }

    // Get all versions
    def versions = getNavKitVersions(architectures)

    // Sort versions based on pattern
    def sortedVersions = versions.stream()
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

    return (sortedVersions.isEmpty()) ? null : sortedVersions.last()
}

@NonCPS
static def doesVersionExist(version) {
    URL url = new URL("http://artifactory-ci.tomtomgroup.com/artifactory/api/search/versions?repos=navapp-releases&g=com.tomtom.navui.navkit&a=NavKitNDS")

    def found = new groovy.json.JsonSlurper().parseText(url.text).results.find { result ->
      result.version == version
    } != null

    return found
}