package io.jenkins.plugins.activejobsfilter;

import hudson.model.FreeStyleProject;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TopLevelItem;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import jenkins.branch.Branch;
import jenkins.scm.api.mixin.ChangeRequestSCMHead;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.multibranch.BranchJobProperty;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;

public class ActiveJobsFilter {

    private final int activeDays;
    private final JobType jobType;
    private final boolean includeMultibranchPrs;
    private final boolean includeBranchJobs;
    private final Pattern includePattern;
    private final Pattern excludePattern;
    private final boolean hasInvalidRegex;

    public ActiveJobsFilter(
            int activeDays,
            JobType jobType,
            boolean includeMultibranchPrs,
            boolean includeBranchJobs,
            String includeRegex,
            String excludeRegex) {
        this.activeDays = Math.max(0, activeDays);
        this.jobType = jobType == null ? JobType.ALL : jobType;
        this.includeMultibranchPrs = includeMultibranchPrs;
        this.includeBranchJobs = includeBranchJobs;

        Pattern compiledInclude = null;
        Pattern compiledExclude = null;
        boolean invalidRegex = false;
        try {
            compiledInclude = compileNullable(includeRegex);
            compiledExclude = compileNullable(excludeRegex);
        } catch (PatternSyntaxException e) {
            invalidRegex = true;
        }
        this.includePattern = compiledInclude;
        this.excludePattern = compiledExclude;
        this.hasInvalidRegex = invalidRegex;
    }

    public boolean matches(Job<?, ?> job, long nowMillis) {
        if (hasInvalidRegex) {
            return false;
        }
        // By default list the multibranch project item instead of branch children.
        // If recurse is enabled on the view, branch jobs are allowed.
        if (!includeBranchJobs && isBranchJob(job)) {
            return false;
        }
        if (!isTypeSelected(job)) {
            return false;
        }
        return matchesConcreteJob(job, nowMillis);
    }

    public boolean matches(TopLevelItem item, long nowMillis) {
        if (hasInvalidRegex) {
            return false;
        }
        if (item instanceof Job<?, ?>) {
            return matches((Job<?, ?>) item, nowMillis);
        }
        if (!(item instanceof WorkflowMultiBranchProject multibranchProject)) {
            return false;
        }
        if (jobType != JobType.ALL && jobType != JobType.MULTIBRANCH_PIPELINE) {
            return false;
        }

        String fullName = item.getFullName();
        if (includePattern != null && !includePattern.matcher(fullName).matches()) {
            return false;
        }
        if (excludePattern != null && excludePattern.matcher(fullName).matches()) {
            return false;
        }

        Long latestStarted = latestStartedForMultibranchProject(multibranchProject);
        if (latestStarted == null || latestStarted <= 0L) {
            return false;
        }
        return isWithinCutoff(latestStarted, nowMillis);
    }

    private boolean matchesConcreteJob(Job<?, ?> job, long nowMillis) {
        if (!isTypeSelected(job)) {
            return false;
        }

        if (!includeMultibranchPrs && isMultibranchPrJob(job)) {
            return false;
        }

        String fullName = job.getFullName();
        if (includePattern != null && !includePattern.matcher(fullName).matches()) {
            return false;
        }
        if (excludePattern != null && excludePattern.matcher(fullName).matches()) {
            return false;
        }

        Run<?, ?> lastBuild = job.getLastBuild();
        if (lastBuild == null) {
            return false;
        }

        long startedMillis = lastBuild.getStartTimeInMillis();
        if (startedMillis <= 0L) {
            return false;
        }

        return isWithinCutoff(startedMillis, nowMillis);
    }

    private boolean isTypeSelected(Job<?, ?> job) {
        if (jobType == JobType.ALL) {
            return true;
        }

        if (job instanceof WorkflowJob) {
            if (isBranchJob(job)) {
                return jobType == JobType.MULTIBRANCH_PIPELINE;
            }
            return jobType == JobType.PIPELINE;
        }

        return jobType == JobType.FREESTYLE && job instanceof FreeStyleProject;
    }

    private static boolean isBranchJob(Job<?, ?> job) {
        if (!(job instanceof WorkflowJob)) {
            return false;
        }
        if (!(job.getParent() instanceof WorkflowMultiBranchProject)) {
            return false;
        }
        return job.getProperty(BranchJobProperty.class) != null;
    }

    private static boolean isMultibranchPrJob(Job<?, ?> job) {
        if (!(job instanceof WorkflowJob)) {
            return false;
        }
        BranchJobProperty branchProperty = job.getProperty(BranchJobProperty.class);
        if (branchProperty == null) {
            return false;
        }
        Branch branch = branchProperty.getBranch();
        return branch != null && branch.getHead() instanceof ChangeRequestSCMHead;
    }

    static Pattern compileNullable(String regex) {
        if (regex == null) {
            return null;
        }
        String trimmed = regex.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return Pattern.compile(trimmed);
    }

    private boolean isWithinCutoff(long startedMillis, long nowMillis) {
        if (activeDays == 0) {
            return true;
        }
        long cutoff = nowMillis - (activeDays * 24L * 60L * 60L * 1000L);
        return startedMillis >= cutoff;
    }

    private Long latestStartedForMultibranchProject(WorkflowMultiBranchProject multibranchProject) {
        long latest = -1L;
        for (WorkflowJob job : multibranchProject.getItems()) {
            if (!includeMultibranchPrs && isMultibranchPrJob(job)) {
                continue;
            }
            Run<?, ?> run = job.getLastBuild();
            if (run == null) {
                continue;
            }
            long started = run.getStartTimeInMillis();
            if (started > latest) {
                latest = started;
            }
        }
        return latest > 0L ? latest : null;
    }
}
