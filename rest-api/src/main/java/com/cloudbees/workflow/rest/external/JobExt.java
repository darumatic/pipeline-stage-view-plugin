/*
 * The MIT License
 *
 * Copyright (c) 2013-2016, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.cloudbees.workflow.rest.external;

import com.cloudbees.workflow.rest.endpoints.JobAPI;
import com.cloudbees.workflow.rest.hal.Link;
import com.cloudbees.workflow.rest.hal.Links;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.common.collect.Lists;
import hudson.EnvVars;
import hudson.model.Run;
import hudson.scm.ChangeLogSet;
import hudson.scm.RepositoryBrowser;
import hudson.scm.SCM;
import hudson.util.LogTaskListener;
import hudson.util.RunList;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class JobExt {
    /**
     * Max number of runs per page. Pagination not yet supported.
     */
    public static final int MAX_RUNS_PER_JOB = Integer.getInteger(JobExt.class.getName() + ".maxRunsPerJob", 10);

    private JobLinks _links;
    private String name;
    private int runCount;

    public JobLinks get_links() {
        return _links;
    }

    public void set_links(JobLinks _links) {
        this._links = _links;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getRunCount() {
        return runCount;
    }

    public void setRunCount(int runCount) {
        this.runCount = runCount;
    }

    public static final class JobLinks extends Links {
        private Link runs;

        public Link getRuns() {
            return runs;
        }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        public void setRuns(Link runs) {
            this.runs = runs;
        }
    }

    public static JobExt create(WorkflowJob job) {
        JobExt jobExt = new JobExt();

        jobExt.set_links((JobLinks) new JobLinks().initSelf(JobAPI.getDescribeUrl(job)));
        jobExt.get_links().setRuns(Link.newLink(JobAPI.getRunsUrl(job)));
        jobExt.setName(job.getName());
        jobExt.setRunCount(countRuns(job));

        return jobExt;
    }

    private static int countRuns(WorkflowJob job) {
        int count = 0;

        // RunList.size() is deprecated, so iterating to count them.
        RunList<WorkflowRun> runs = job.getBuilds();
        for (WorkflowRun run : runs) {
            count++;
        }

        return count;
    }

    @Deprecated
    public static List<RunExt> create(List<WorkflowRun> runs) {
        return create(runs, null);
    }

    @Deprecated
    public static List<RunExt> create(List<WorkflowRun> runs, String since) {
        return create(runs, since, false);
    }

    public static List<RunExt> create(List<WorkflowRun> runs, String since, boolean fullStages) {
        if (since != null) {
            since = since.trim();
            if (since.length() == 0) {
                since = null;
            }
        }

        List<RunExt> runsExt = new ArrayList<RunExt>();
        for (int i = 0; i < runs.size(); i++) {
            WorkflowRun run = runs.get(i);
            RunExt runExt = (fullStages) ? RunExt.create(run) : RunExt.create(run).createWrapper();
            runExt.setJobName(jobName(run));
            runExt.setChangeSet(lastChangeSet(runs, i));
            runExt.setChangeSets(getChangeSets(run).stream().map(changeSet -> ChangeSetExt.create(changeSet, run)).collect(Collectors.toList()));
            try {
                EnvVars environment = run.getEnvironment(new LogTaskListener(null, Level.INFO));
                runExt.setEnvironment(environment.get("ENVIRONMENT"));
                runExt.setPromoteFromEnvironment(environment.get("PROMOTE_FROM_ENVIRONMENT"));
                runExt.setPromoteFromVersion(environment.get("PROMOTE_FROM_VERSION"));
            } catch (IOException | InterruptedException e) {
            }

            String branch = branch(scm(run));
            if (branch == null) {
                branch = branch(run.getExecution());
            }
            if (branch != null) {
                if (branch.startsWith("$")) {
                    branch = branch.substring(1);
                    if (branch.startsWith("{") && branch.endsWith("}")) {
                        branch = branch.substring(1, branch.length() - 1);
                    }
                    try {
                        EnvVars environment = run.getEnvironment(new LogTaskListener(null, Level.INFO));
                        branch = environment.get(branch);
                    } catch (Exception e) {
                        branch = null;
                    }
                } else {
                    branch = branch.replaceAll("\\*/", "");
                }
            }
            runExt.setBranch(branch);

            runsExt.add(runExt);
            if (since != null && runExt.getName().equals(since)) {
                break;
            } else if (runsExt.size() > MAX_RUNS_PER_JOB) {
                // We don't yet support pagination, so no point
                // returning a huge list of runs.
                break;
            }
        }
        return runsExt;
    }

    private static String jobName(WorkflowRun run) {
        try {
            Field projectField = Run.class.getDeclaredField("project");
            projectField.setAccessible(true);
            WorkflowJob job = (WorkflowJob) projectField.get(run);
            return job.getName();
        } catch (Exception e) {
            return null;
        }
    }

    private static SCM scm(WorkflowRun run) {
        try {
            Field checkoutsFields = run.getClass().getDeclaredField("checkouts");
            checkoutsFields.setAccessible(true);
            List<Object> checkoutList = (List<Object>) checkoutsFields.get(run);
            if (!checkoutList.isEmpty()) {
                Object checkout = checkoutList.get(0);
                Field scmField = checkout.getClass().getDeclaredField("scm");
                scmField.setAccessible(true);
                return (SCM) scmField.get(checkout);
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String branch(SCM scm) {
        try {
            if (scm == null) {
                return null;
            }
            Field branchesField = scm.getClass().getDeclaredField("branches");
            branchesField.setAccessible(true);
            List<Object> configs = (List<Object>) branchesField.get(scm);
            if (!configs.isEmpty()) {
                Object branchSpec = configs.get(0);
                Field nameField = branchSpec.getClass().getDeclaredField("name");
                nameField.setAccessible(true);
                return (String) nameField.get(branchSpec);
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    public static String branch(FlowExecution execution) {
        String script = script(execution);
        if (script == null) {
            return null;
        }
        int start = script.indexOf("branches:");
        if (start > 0) {
            start = script.indexOf("[[", start);

            if (start > 0) {
                int end = script.indexOf("]]", start);
                if (end > start) {
                    String branches = script.substring(start + 2, end);
                    Pattern pattern = Pattern.compile("[^\']+\'(.*)\'.*");

                    Matcher matcher = pattern.matcher(branches);
                    if (matcher.matches()) {
                        return matcher.group(1);
                    }
                }
            }
        }
        return null;
    }

    private static String script(FlowExecution execution) {
        try {
            Field scriptField = execution.getClass().getDeclaredField("script");
            scriptField.setAccessible(true);
            return (String) scriptField.get(execution);
        } catch (Exception e) {
            return null;
        }
    }

    private static ChangeSetExt lastChangeSet(List<WorkflowRun> runs, int currentIndex) {
        WorkflowRun run = runs.get(currentIndex);
        if (isPromotedVersion(run)) {
            int version = promotedVersion(run);
            for (int i = 0; i < runs.size(); i++) {
                WorkflowRun workflowRun = runs.get(i);
                if (workflowRun.getNumber() == version) {
                    return lastChangeSet(runs, i);
                }
            }
        }

        List<ChangeLogSet<? extends ChangeLogSet.Entry>> changeSets = getChangeSets(run);
        if (changeSets.isEmpty()) {
            for (int i = currentIndex + 1; i < runs.size(); i++) {
                changeSets = getChangeSets(runs.get(i));
                if (!changeSets.isEmpty()) {
                    ChangeLogSet<? extends ChangeLogSet.Entry> entries = changeSets.get(0);
                    return ChangeSetExt.create(entries, run);
                }
            }
            return null;
        } else {
            return ChangeSetExt.create(changeSets.get(0), run);
        }
    }

    private static List<ChangeLogSet<? extends ChangeLogSet.Entry>> getChangeSets(WorkflowRun run) {
        List<ChangeLogSet<? extends ChangeLogSet.Entry>> changeSets = run.getChangeSets();
        List<ChangeLogSet<? extends ChangeLogSet.Entry>> filtered = Lists.newArrayList();
        for (ChangeLogSet<? extends ChangeLogSet.Entry> changeSet : changeSets) {
            if (!isExcluded(changeSet)) {
                filtered.add(changeSet);
            }
        }
        return filtered;
    }

    @SuppressWarnings("unchecked")
    private static boolean isExcluded(ChangeLogSet<? extends ChangeLogSet.Entry> changeLogSet) {
        if (changeLogSet.isEmptySet()) {
            return true;
        }
        RepositoryBrowser<ChangeLogSet.Entry> repoBrowser = (RepositoryBrowser<ChangeLogSet.Entry>) changeLogSet.getBrowser();
        if (repoBrowser == null) {
            return false;
        }
        try {
            URL changeSetLink = repoBrowser.getChangeSetLink(changeLogSet.iterator().next());
            if (changeSetLink == null) {
                return false;
            }
            String url = changeSetLink.toString();
            if (url == null) {
                return false;
            }
            return url.contains("/jenkins-project-config") || url.contains("/k8s-scripts");
        } catch (IOException e) {
            return false;
        }
    }

    private static int promotedVersion(WorkflowRun run) {
        try {
            EnvVars environment = run.getEnvironment(new LogTaskListener(null, Level.INFO));
            Object promoteFromVersion = environment.get("PROMOTE_FROM_VERSION");
            return Integer.parseInt(promoteFromVersion.toString());
        } catch (Exception e) {
            return 0;
        }
    }

    private static boolean isPromotedVersion(WorkflowRun run) {
        try {
            EnvVars environment = run.getEnvironment(new LogTaskListener(null, Level.INFO));
            return environment.containsKey("PROMOTE_FROM_VERSION");
        } catch (Exception e) {
            return false;
        }
    }
}
