package com.tomtom;

@NonCPS
static def getP4Changelist(build) {
    def rawBuild = build.getRawBuild()
    def environment = rawBuild.getEnvironment()
    return environment["P4_CHANGELIST"]
}

@NonCPS
static def getBuildConfig(jobName) {
    def buildConfig = [:]

    // Determine branch name
    buildConfig['branchName'] = jobName.substring(jobName.indexOf('/') + 1)

    // Determine test-suite and node-type
    switch (jobName.split('/')[0]) {
        case "fast-build":
            if (buildConfig['branchName'] == 'develop') {
                buildConfig['testSuite'] = 'jenkins_main_fast'
            } else {
                buildConfig['testSuite'] = 'jenkins_other_fast'
            }
            buildConfig['nodeType'] = 'fast && ttc'
            break;
        case "slow-build":
            if (buildConfig['branchName'] == 'develop') {
                buildConfig['testSuite'] = 'jenkins_main_slow'
            } else {
                buildConfig['testSuite'] = 'jenkins_other_slow'
            }
            buildConfig['nodeType'] = 'slow && ttc'
            break;
    }

    return buildConfig
}