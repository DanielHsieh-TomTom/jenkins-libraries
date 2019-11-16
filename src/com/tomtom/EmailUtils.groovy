package com.tomtom

import hudson.model.Result;
import hudson.tasks.Mailer

@NonCPS
static def getEmailConfig(env, currentBuild) {
    // Determine email address
    def buildUserId = currentBuild.rawBuild.getCause(Cause.UserIdCause).getUserId()
    def buildUser = User.get(buildUserId)
    def emailAddress = buildUser.getProperty(Mailer.UserProperty.class).getAddress()

    def emailConfig = [:]
    emailConfig.from = "NavUI Jenkins <noreply@navui-jenkins.ttg.global>"
    emailConfig.mimeType = "text/html"
    emailConfig.to = emailAddress

    // Check results
    def passed = currentBuild.resultIsBetterOrEqualTo(Result.SUCCESS.toString())
    def previousPassed = currentBuild.previousBuild == null || currentBuild.previousBuild.resultIsBetterOrEqualTo(Result.SUCCESS.toString())

    def status = (passed) ? "PASSED" : "FAILED"
    def statusDetails = BuildUtils.getBuildStatus(currentBuild)
    def jobName = URLDecoder.decode(env.JOB_NAME)
    def buildNumber = env.BUILD_NUMBER

    emailConfig.subject = "[$status] $statusDetails: $jobName #$buildNumber"
    emailConfig.message = "$statusDetails - <a href=\"${env.BUILD_URL}\">$jobName #$buildNumber</a>"

    return emailConfig
}