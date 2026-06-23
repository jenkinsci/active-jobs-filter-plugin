package io.jenkins.plugins.activejobsfilter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.FreeStyleProject;
import hudson.model.JobProperty;
import hudson.model.TopLevelItem;
import hudson.scm.NullSCM;
import hudson.util.FormValidation;
import java.lang.reflect.Constructor;
import java.util.List;
import jenkins.branch.Branch;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.mixin.ChangeRequestSCMHead;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
public class ActiveJobsFilterTest {

    @Test
    public void shouldIncludeRecentlyBuiltFreestyleJob(JenkinsRule j) throws Exception {
        FreeStyleProject job = j.createFreeStyleProject("active-freestyle");
        j.buildAndAssertSuccess(job);

        ActiveJobsFilter filter = new ActiveJobsFilter(1, JobType.ALL, false, false, ".*", "");
        assertTrue(filter.matches((TopLevelItem) job, System.currentTimeMillis()));
    }

    @Test
    public void shouldFilterByAllowDenyRegex(JenkinsRule j) throws Exception {
        FreeStyleProject keep = j.createFreeStyleProject("team-a-keep");
        FreeStyleProject drop = j.createFreeStyleProject("team-b-drop");
        j.buildAndAssertSuccess(keep);
        j.buildAndAssertSuccess(drop);

        ActiveJobsFilter filter = new ActiveJobsFilter(1, JobType.ALL, false, false, "team-.*", ".*drop");
        assertTrue(filter.matches((TopLevelItem) keep, System.currentTimeMillis()));
        assertFalse(filter.matches((TopLevelItem) drop, System.currentTimeMillis()));
    }

    @Test
    public void shouldFilterByJobType(JenkinsRule j) throws Exception {
        FreeStyleProject freestyle = j.createFreeStyleProject("freestyle");
        j.buildAndAssertSuccess(freestyle);

        ActiveJobsFilter filter = new ActiveJobsFilter(1, JobType.PIPELINE, false, false, ".*", "");
        assertFalse(filter.matches((TopLevelItem) freestyle, System.currentTimeMillis()));
    }

    @Test
    public void shouldOnlyIncludeFreestyleJobsForFreestyleType(JenkinsRule j) throws Exception {
        FreeStyleProject freestyle = j.createFreeStyleProject("only-freestyle");
        j.buildAndAssertSuccess(freestyle);

        WorkflowJob pipeline = j.jenkins.createProject(WorkflowJob.class, "not-freestyle");
        pipeline.setDefinition(new CpsFlowDefinition("def x = 1\n", true));
        j.buildAndAssertSuccess(pipeline);

        ActiveJobsFilter filter = new ActiveJobsFilter(1, JobType.FREESTYLE, false, false, ".*", "");

        assertTrue(filter.matches((TopLevelItem) freestyle, System.currentTimeMillis()));
        assertFalse(filter.matches((TopLevelItem) pipeline, System.currentTimeMillis()));
    }

    @Test
    public void activeDaysZeroIncludesAnyStartedBuild(JenkinsRule j) throws Exception {
        FreeStyleProject job = j.createFreeStyleProject("old-but-started");
        j.buildAndAssertSuccess(job);

        long farFuture = System.currentTimeMillis() + 365L * 24L * 60L * 60L * 1000L;
        ActiveJobsFilter filter = new ActiveJobsFilter(0, JobType.ALL, false, false, ".*", "");

        assertTrue(filter.matches((TopLevelItem) job, farFuture));
    }

    @Test
    public void shouldExcludeBuildsOutsideCutoff(JenkinsRule j) throws Exception {
        FreeStyleProject job = j.createFreeStyleProject("stale");
        j.buildAndAssertSuccess(job);

        long twoDaysFromNow = System.currentTimeMillis() + 2L * 24L * 60L * 60L * 1000L;
        ActiveJobsFilter filter = new ActiveJobsFilter(1, JobType.ALL, false, false, ".*", "");

        assertFalse(filter.matches((TopLevelItem) job, twoDaysFromNow));
    }

    @Test
    public void invalidRegexDoesNotBreakFiltering(JenkinsRule j) throws Exception {
        FreeStyleProject job = j.createFreeStyleProject("regex-safe");
        j.buildAndAssertSuccess(job);

        ActiveJobsFilter filter = new ActiveJobsFilter(1, JobType.ALL, false, false, "(", "");

        assertFalse(filter.matches((TopLevelItem) job, System.currentTimeMillis()));
    }

    @Test
    public void descriptorRejectsInvalidRegex(JenkinsRule j) {
        ActiveJobsJobFilter.DescriptorImpl descriptor = new ActiveJobsJobFilter.DescriptorImpl();

        assertEquals(FormValidation.Kind.OK, descriptor.doCheckAllowRegex(null, ".*").kind);
        assertEquals(FormValidation.Kind.ERROR, descriptor.doCheckAllowRegex(null, "(").kind);
        assertEquals(FormValidation.Kind.ERROR, descriptor.doCheckDenyRegex(null, "[").kind);
    }

    @Test
    public void multibranchGroupUsesLatestBranchOrPrBuild(JenkinsRule j) throws Exception {
        WorkflowJob branch = j.jenkins.createProject(WorkflowJob.class, "mb-branch");
        branch.setDefinition(new CpsFlowDefinition("def x = 1\n", true));
        addBranchProperty(branch, new SCMHead("main"));
        // No branch build.

        WorkflowJob pr = j.jenkins.createProject(WorkflowJob.class, "mb-pr");
        pr.setDefinition(new CpsFlowDefinition("def y = 2\n", true));
        addBranchProperty(pr, new TestChangeRequestHead("PR-1", new SCMHead("main")));
        j.buildAndAssertSuccess(pr);

        WorkflowMultiBranchProject group = new TestWorkflowMultiBranchProject(j.jenkins, "mb-group", List.of(branch, pr));

        ActiveJobsFilter excludePrFilter = new ActiveJobsFilter(1, JobType.ALL, false, false, ".*", "");
        ActiveJobsFilter includePrFilter = new ActiveJobsFilter(1, JobType.ALL, true, false, ".*", "");

        assertFalse(excludePrFilter.matches((TopLevelItem) group, System.currentTimeMillis()));
        assertTrue(includePrFilter.matches((TopLevelItem) group, System.currentTimeMillis()));
    }

    private static void addBranchProperty(WorkflowJob job, SCMHead head) throws Exception {
        Branch branch = new Branch("test-source", head, new NullSCM(), List.of());
        Class<?> propClass = Class.forName("org.jenkinsci.plugins.workflow.multibranch.BranchJobProperty");
        Constructor<?> ctor = propClass.getDeclaredConstructor(Branch.class);
        ctor.setAccessible(true);
        JobProperty<?> property = (JobProperty<?>) ctor.newInstance(branch);
        job.addProperty(property);
    }

    private static final class TestWorkflowMultiBranchProject extends WorkflowMultiBranchProject {
        private final List<WorkflowJob> items;

        TestWorkflowMultiBranchProject(hudson.model.ItemGroup parent, String name, List<WorkflowJob> items) {
            super(parent, name);
            this.items = items;
        }

        @Override
        public List<WorkflowJob> getItems() {
            return items;
        }
    }

    private static final class TestChangeRequestHead extends SCMHead implements ChangeRequestSCMHead {
        private final String id;
        private final SCMHead target;

        TestChangeRequestHead(String id, SCMHead target) {
            super(id);
            this.id = id;
            this.target = target;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public SCMHead getTarget() {
            return target;
        }
    }
}
