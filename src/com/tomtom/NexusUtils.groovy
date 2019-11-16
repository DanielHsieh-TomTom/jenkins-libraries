package com.tomtom

@NonCPS
static def getPublishingInfo(jobName, buildNumber, commit) {
    def publishInfo = [publish: false]

    if (jobName != "custom-fast-build" && jobName != "custom-slow-build") {
        def rootJob = URLDecoder.decode(jobName.substring(0, jobName.indexOf('/')))
        def branchName = URLDecoder.decode(jobName.substring(jobName.indexOf('/') + 1))

        if (rootJob == 'slow-build') {
            switch (branchName) {
                case "develop":
                    publishInfo['publish'] = true
                    publishInfo['repo'] = 'navapp-staging'
                    publishInfo['version'] = "develop-slow.${buildNumber}-${commit}"
                    break
                case ~/rel-([0-9]{2}\.[0-9])/:
                    publishInfo['publish'] = true
                    publishInfo['repo'] = 'navapp-releases'
                    publishInfo['version'] = "${java.util.regex.Matcher.lastMatcher[0][1]}.${buildNumber}-${commit}"
                    break
            }
        }
    }
    return publishInfo
}