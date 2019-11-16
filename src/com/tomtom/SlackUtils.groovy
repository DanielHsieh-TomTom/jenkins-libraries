package com.tomtom

import hudson.model.Result

@NonCPS
static def getSlackConfig(env, currentBuild, rootJob, branchName) {
    def slackConfig = [:]
    slackConfig.user = null

    def channelConfig = [
      "slow-build": [
        "develop": "#navand-jenkins",
        "canary": "#navand-jenkins-builds"
      ],
      "fast-build": [
        "develop": "#navand-jenkins",
        "canary": "#navand-jenkins-builds"
      ]
    ]

    slackConfig.channel = channelConfig[rootJob]?.get(branchName)

    if (slackConfig.channel == null) {
        def buildUser = User.get(currentBuild.rawBuild.getCause(Cause.UserIdCause).getUserId())
        if (!buildUser.getProperty(jenkins.plugins.slack.user.SlackUserProperty.class).disableNotifications) {
            slackConfig.channel = "@${buildUser.getId()}"
        }
    }

    // Check results
    def passed = currentBuild.resultIsBetterOrEqualTo(Result.SUCCESS.toString())
    def previousPassed = currentBuild.previousBuild == null || currentBuild.previousBuild.resultIsBetterOrEqualTo(Result.SUCCESS.toString())

    // Only notify when build failed or status changed
    slackConfig.notify = slackConfig.channel != null && (!passed || passed != previousPassed)

    if (slackConfig.notify) {
        slackConfig.message = "${BuildUtils.getBuildStatus(currentBuild)} - ${URLDecoder.decode(env.JOB_NAME)} #${env.BUILD_NUMBER} (<${env.BUILD_URL}|Open>)"
    }

    return slackConfig
}

@NonCPS
static def encodeForSlack(message) {
    return message.replaceAll(/<a href=\"([^\"]+)\">([^\<]+)\<\/a\>/, '<$1|$2>').replaceAll(/\<\/?br\>/, '\n')
}