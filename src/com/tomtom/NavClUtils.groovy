package com.tomtom;

import groovy.json.JsonSlurper

@NonCPS
private static def getNavClVersions(architectures) {
    def authorization = "Basic bmF2a2l0Ok5hdksxdCQ="
    def url = new URL("http://artifactory-ci.tomtomgroup.com/artifactory/api/search/aql")

    def versionLists = architectures.stream().map { architecture ->
        def path = "com.tomtom.navkit.navcl/NavCL.Sdk.Android.aar/android/${architecture}/release/*"
        def name = "NavCL.Sdk.Android.aar-android-${architecture}-release-custom-*"

        def body = """items.find({
"repo": {"\$eq":"nav-client-library-releases"},
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
    def branchReleasePattern = /^rel-(\d\d\.\d)$/

    def nightlyPattern = /^nightly_([0-9]{4})_([0-9]{2})_([0-9]{2})\.(.*)$/
    def releasePattern = /^(\d\d(?:\.\d)+)\.?[a-z]+_([0-9]{4})_([0-9]{2})_([0-9]{2})\.(.*)$/

    def architectures = ['armeabi-v7a']
    if (branch ==~ branchReleasePattern) {
        architectures = ['armeabi-v7a', 'x86_64']
    }

    def versions = getNavClVersions(architectures)

    def sortedVersions = []

    if (branch == 'main') {
        sortedVersions = versions.stream()
            .filter { it ==~ nightlyPattern }
            .collect().sort { l, r ->
                def leftMatcher = l =~ nightlyPattern
                def rightMatcher = r =~ nightlyPattern

                def i = 0
                for (i = 1; i < 4 ; i++) {
                    def left = leftMatcher[0][i].toInteger()
                    def right = rightMatcher[0][i].toInteger()
                    if (left != right) {
                        return left <=> right
                    }
                }
                return 0
            }
    } else if (branch ==~ branchReleasePattern) {
        def majorVersion = (branch =~ branchReleasePattern)[0][1]

        sortedVersions = versions.stream()
            .filter { it ==~ releasePattern && it.startsWith(majorVersion) }
            .collect().sort { l, r ->
                def leftMatcher = l =~ releasePattern
                def rightMatcher = r =~ releasePattern

                def leftVersionParts = leftMatcher[0][1].split(/\./)
                def rightVersionParts = rightMatcher[0][1].split(/\./)

                // Sort based on version
                for (int i=0;i<4;i++) {
                    def leftValue = (leftVersionParts.size() >= i+1) ? leftVersionParts[i] as Integer : 0
                    def rightValue = (rightVersionParts.size() >= i+1) ? rightVersionParts[i] as Integer : 0

                    if (leftValue != rightValue) {
                        return leftValue <=> rightValue
                    }
                }

                // Sort based on date
                for (int i = 2; i < 5 ; i++) {
                    def left = leftMatcher[0][i].toInteger()
                    def right = rightMatcher[0][i].toInteger()
                    if (left != right) {
                        return left <=> right
                    }
                }

                return 0
            }
    }

    return (sortedVersions.isEmpty()) ? null : sortedVersions.last()
}

@NonCPS
static def doesVersionExist(version) {
    URL url = new URL("http://artifactory-ci.tomtomgroup.com/artifactory/api/search/versions?repos=navapp-releases&g=com.tomtom.navui.navcl&a=TomTomNavKitNavCLSdk")

    def found = new groovy.json.JsonSlurper().parseText(url.text).results.find { result ->
      result.version == version
    } != null

    return found
}

@NonCPS
static def getNavKitVersion(navclVersion) {
    def commit = navclVersion[-8..-1]
    def url = "https://bitbucket.tomtomgroup.com/projects/NAVCL/repos/navcl-team/raw/Source/navkit_version.properties?at=${commit}".toURL()
    def props = new Properties()
    props.load(new StringReader(url.getText()))
    return props["navkitVersion"]
}