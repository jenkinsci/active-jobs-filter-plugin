package io.jenkins.plugins.activejobsfilter;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.ListView;
import hudson.model.TopLevelItem;
import hudson.model.View;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.views.ViewJobFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.PatternSyntaxException;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

public class ActiveJobsJobFilter extends ViewJobFilter {

    private int activeDays;
    private JobType jobType;
    private boolean includeMultibranchPrs;
    private String allowRegex;
    private String denyRegex;

    @DataBoundConstructor
    public ActiveJobsJobFilter() {
        this.activeDays = 7;
        this.jobType = JobType.ALL;
        this.includeMultibranchPrs = false;
        this.allowRegex = ".*";
        this.denyRegex = "";
    }

    public int getActiveDays() {
        return activeDays;
    }

    public JobType getJobType() {
        return jobType;
    }

    public boolean isIncludeMultibranchPrs() {
        return includeMultibranchPrs;
    }

    public String getAllowRegex() {
        return allowRegex;
    }

    public String getDenyRegex() {
        return denyRegex;
    }

    @DataBoundSetter
    public void setActiveDays(int activeDays) {
        this.activeDays = Math.max(0, activeDays);
    }

    @DataBoundSetter
    public void setJobType(JobType jobType) {
        this.jobType = jobType == null ? JobType.ALL : jobType;
    }

    @DataBoundSetter
    public void setIncludeMultibranchPrs(boolean includeMultibranchPrs) {
        this.includeMultibranchPrs = includeMultibranchPrs;
    }

    @DataBoundSetter
    public void setAllowRegex(String allowRegex) {
        this.allowRegex = allowRegex == null ? "" : allowRegex;
    }

    @DataBoundSetter
    public void setDenyRegex(String denyRegex) {
        this.denyRegex = denyRegex == null ? "" : denyRegex;
    }

    @Override
    public List<TopLevelItem> filter(List<TopLevelItem> added, List<TopLevelItem> all, View filteringView) {
        boolean includeBranchJobs = filteringView instanceof ListView && ((ListView) filteringView).isRecurse();
        ActiveJobsFilter filter = new ActiveJobsFilter(
                activeDays,
                jobType,
                includeMultibranchPrs,
                includeBranchJobs,
                allowRegex,
                denyRegex
        );
        long now = System.currentTimeMillis();
        List<TopLevelItem> result = new ArrayList<>();
        for (TopLevelItem item : added) {
            if (filter.matches(item, now)) {
                result.add(item);
            }
        }
        return result;
    }

    @Extension
    @Symbol("activeJobsFilter")
    public static class DescriptorImpl extends Descriptor<ViewJobFilter> {
        @Override
        public String getDisplayName() {
            return "Active jobs filter";
        }

        public ListBoxModel doFillJobTypeItems() {
            ListBoxModel items = new ListBoxModel();
            items.add("All", JobType.ALL.name());
            items.add("Pipeline", JobType.PIPELINE.name());
            items.add("Multibranch pipeline", JobType.MULTIBRANCH_PIPELINE.name());
            items.add("Freestyle", JobType.FREESTYLE.name());
            return items;
        }

        public FormValidation doCheckAllowRegex(@QueryParameter String value) {
            return validateRegex(value);
        }

        public FormValidation doCheckDenyRegex(@QueryParameter String value) {
            return validateRegex(value);
        }

        private static FormValidation validateRegex(String value) {
            try {
                ActiveJobsFilter.compileNullable(value);
                return FormValidation.ok();
            } catch (PatternSyntaxException e) {
                return FormValidation.error("Invalid regular expression: " + e.getDescription());
            }
        }
    }
}
