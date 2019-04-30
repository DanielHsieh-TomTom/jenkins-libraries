package com.tomtom

import groovy.json.JsonSlurper
import org.apache.http.HttpResponse
import org.apache.http.client.methods.HttpGet
import org.apache.http.conn.ssl.TrustStrategy
import org.apache.http.util.EntityUtils
import org.apache.http.conn.ssl.SSLContextBuilder
import org.apache.http.conn.ssl.SSLConnectionSocketFactory
import org.apache.http.impl.client.HttpClients
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.stream.Collectors

import javax.net.ssl.X509TrustManager
import javax.net.ssl.SSLContext
import javax.net.ssl.HttpsURLConnection

@NonCPS
private static def getVersions(path, name) {
    def authorization = "Basic bmF2a2l0Ok5hdksxdCQ="
    def url = new URL("http://artifactory.navkit-pipeline.tt3.com/artifactory/api/search/aql")

    def body = """items.find({
"repo": { "\$eq": "navkit-maven" },
"path": { "\$match": "$path" },
"name": { "\$match": "$name" }
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

    return json.results.stream().map {
        it.path.substring(it.path.lastIndexOf("/") + 1)
    }.collect()
}

@NonCPS
private static def getNavKitVersions() {
    return getVersions("com/tomtom/navkit/android/NavKitService/*", "NavKitService*.aar")
}

@NonCPS
private static def getMichiVersions() {
    return getVersions("com/tomtom/michi/android/libTomTomNavKitMapSdk/*", "libTomTomNavKitMapSdk*.aar")
}

@NonCPS
private static def getNavClVersions() {
    return getVersions("com/tomtom/navcl/android/TomTomNavKitNavCLSdk/*", "TomTomNavKitNavCLSdk*.aar")
}

@NonCPS
static def getLatestNavKitVersion(branch) {
    def releaseBranchPattern = "^rel-([0-9]+)\\.([0-9]+)\$"

    def versionPattern = ""
    switch (branch) {
        case "main":
            versionPattern = "^([0-9]+\\.[0-9]+\\.[0-9]+)\$"
            break
        case ~/$releaseBranchPattern/:
            def matcher = branch =~ /$releaseBranchPattern/
            versionPattern = "^([0-9]+\\.[0-9]+\\.[0-9]+)-${matcher[0][1]}\\.${matcher[0][2]}\$"
            break
    }

    // Get all versions
    def versions = getNavKitVersions()

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
static def getLatestMichiVersion(branch) {
    def releaseBranchPattern = "^rel-([0-9]+)\\.([0-9]+)\$"

    def versionPattern
    def comparator
    switch (branch) {
        case "main":
            versionPattern = "^([0-9]+)\\.([0-9]+)\$"
            comparator = { a, b ->
                return a.replace('.', '') <=> b.replace('.', '')
            }
            break
        case ~/$releaseBranchPattern/:
            versionPattern = "^${branch}-([0-9]{8}\\.[0-9]{6})\$"
            comparator = { a, b ->
                def aInt = a.substring(8).replace('.', '')
                def bInt = b.substring(8).replace('.', '')
                return a <=> b
            }
            break
        default:
            return null
    }

    // Get all versions
    def versions = getMichiVersions()

    // Sort versions based on pattern
    def sortedVersions = versions.stream()
        .filter { it ==~ versionPattern }
        .collect().sort(comparator)

    return (sortedVersions.isEmpty()) ? null : sortedVersions.last()
}

@NonCPS
static def getLatestNavClVersion(branch) {
    def branchReleasePattern = /^rel-(\d\d\.\d)$/

    def nightlyPattern = /^nightly_([0-9]{4})_([0-9]{2})_([0-9]{2})\.(.*)$/
    def releasePattern = /^(\d\d(?:\.\d)+)\.?[a-z]+_([0-9]{4})_([0-9]{2})_([0-9]{2})\.(.*)$/

    def versions = getNavClVersions()

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