package com.tomtom;

@NonCPS
static def getP4Changelist(build) {
    println "build: ${build}"
    def rawBuild = build.getRawBuild()
    println "rawBuild: ${rawBuild}"
    def environment = rawBuild.getEnvironment()
    println "environment: ${environment}"
    return environment["P4_CHANGELIST"]
}