package com.tomtom;

import groovy.json.JsonSlurper

class BuildTriggerManager {

    final ROOT_JOBS = ['slow-build', 'fast-build']

    enum BranchType {
        TOP(0, true, JobType.SLOW, JobType.FAST),
        INTEGRATION(1, false, JobType.FAST),
        TASK(2, false, JobType.FAST)

        private final int priority
        private final boolean requiresOwnBuilds
        private final JobType[] jobTypes

        BranchType(final int priority, final boolean requiresOwnBuilds, final JobType... jobTypes) {
            this.priority = priority
            this.requiresOwnBuilds = requiresOwnBuilds
            this.jobTypes = jobTypes
        }
    }

    enum JobType {
        FAST('fast-build'),
        SLOW('slow-build')

        static Map<String, JobType> sMap = new HashMap<>()
        static {
            values().each { jobType ->
                sMap.put(jobType.jobName, jobType)
            }
        }

        private final String jobName

        JobType(String jobName) {
            this.jobName = jobName
        }

        @NonCPS
        public static JobType fromJobName(final String jobName) {
            return sMap.get(jobName)
        }
    }

    /**
     * Returns the current commit of the given branch, or null if the branch was removed
     * @param branchName The name of the branch
     */
    @NonCPS
    private def getCommit(String branchName) {
        def url = "https://bitbucket.tomtomgroup.com/rest/api/1.0/projects/NAVAPP/repos/navui-main/branches?filterText=${branchName}"
        def json = Utils.doHttpGetWithBasicAuthentication(url, 'svc_navuibuild')
        def jsonSlurper = new JsonSlurper()
        def object = jsonSlurper.parseText(json)
        def branch = object.values.stream().find { it['displayId'] == branchName }
        return (branch != null) ? branch['latestCommit'] : null
    }

    @NonCPS
    private def getBuilds(String commit) {
        def buildInfoList = []
        ROOT_JOBS.each { rootJob ->
            Jenkins.instance.getJob(rootJob).items.each { job ->
                def buildInfo = [rootJob, URLDecoder.decode(job.name)]

                job.builds.each { build ->
                    def buildCommit = build.actions.stream().find { action ->
                        action instanceof jenkins.scm.api.SCMRevisionAction
                    }.revision.hash

                    if (buildCommit == commit) {
                        buildInfoList << buildInfo
                    }
                }
            }
        }
        return buildInfoList
    }

    @NonCPS
    private static BranchType getBranchType(String branchName) {
        switch (branchName) {
            case ~/(develop|master|rel-[0-9]{2}\.[0-9]|navkit-canary)$/:
                return BranchType.TOP
            case ~/(?i)^((NAVAPP|US)-[0-9]+\/)?(integration|bugfix).*$/:
                return BranchType.INTEGRATION
            case ~/(?i)^(NAVAPP|US)-[0-9]+\/\1-[0-9].*$/:
                return BranchType.TASK
            default:
                return null
        }
    }

    @NonCPS
    private boolean hasSuitableBuild(String commit, String branchName, JobType jobType) {
        def builds = getBuilds(commit)
        boolean requiresExactMatch = getBranchType(branchName).requiresOwnBuilds

        boolean buildFound = false
        builds.each { buildInfo ->
            def buildJobType = JobType.fromJobName(buildInfo[0])
            def buildBranchName = buildInfo[1]

            if (buildJobType == jobType) {
                buildFound = !requiresExactMatch || buildBranchName == branchName
            }
        }
        return buildFound
    }


    @NonCPS
    def triggerRequiredBuilds(currentBuild) {
        def log = ''

        // Determine all branches to consider for build triggering
        def branches = []
        ROOT_JOBS.each { rootJobName ->
            def rootJob = Jenkins.instance.getItem(rootJobName)
            rootJob.items.stream().filter { job ->
                getBranchType(URLDecoder.decode(job.name)) != null
            }.each { job ->
                branches << URLDecoder.decode(job.name)
            }
        }

        // Map the commits to the branches, with the branches sorted by priority
        def commitToBranchesMap = [:]
        branches.unique().sort { branch ->
            getBranchType(branch).priority
        }.each { branch ->
            def commit = getCommit(branch)
            if (commit != null) {
                if (!(commit in commitToBranchesMap)) {
                    commitToBranchesMap[commit] = []
                }
                commitToBranchesMap[commit] << branch
            }
        }

        commitToBranchesMap.each { commit, commitBranches ->
            log += "\nCommit: ${commit}\n"
            log += "Branches: ${commitBranches}\n"

            def existingJobTypes = []

            for (final String branchName : commitBranches) {
                def branchType = getBranchType(branchName)

                log += "{\n"
                log += "  Branch: ${branchName},\n"
                log += "  BranchType: ${branchType},\n"
                log += "  JobTypes: [\n"

                branchType.jobTypes.each { jobType ->
                    final boolean alreadyTriggered = jobType in existingJobTypes
                    final boolean hasSuitableBuild = hasSuitableBuild(commit, branchName, jobType)
                    def job = Jenkins.instance.getItem(jobType.jobName).getItem(branchName)

                    log += "    {\n"
                    log += "      Job: ${job.fullName},\n"
                    log += "      JobType: ${jobType},\n"
                    log += "      RequiresOwnBuilds: ${branchType.requiresOwnBuilds},\n"
                    log += "      HasSuitableBuild: ${hasSuitableBuild},\n"
                    log += "      AlreadyTriggered: ${alreadyTriggered},\n"
                    log += "      Building: ${job.isBuilding()},\n"
                    log += "      InQueue: ${job.isInQueue()},\n"

                    boolean trigger = false
                    if (!hasSuitableBuild && (!alreadyTriggered || branchType.requiresOwnBuilds)) {
                        if (!job.isBuilding() && !job.isInQueue()) {
                            trigger = true
                        }
                        existingJobTypes << jobType
                    }

                    log += "      Triggering: ${trigger}\n"
                    log += "    },\n"

                    if (trigger) {
                        job.scheduleBuild(new Cause.UpstreamCause(currentBuild.build()))
                    }
                }
                log += "  ]\n"
                log += "},\n"
            }
        }

        return log
    }
}
