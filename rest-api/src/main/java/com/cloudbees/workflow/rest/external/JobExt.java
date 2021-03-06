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
import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import hudson.EnvVars;
import hudson.model.Run;
import hudson.scm.ChangeLogSet;
import hudson.scm.RepositoryBrowser;
import hudson.scm.SCM;
import hudson.util.LogTaskListener;
import hudson.util.RunList;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class JobExt {
    private static final Logger LOGGER = LoggerFactory.getLogger(JobExt.class);

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


            ChangeLogSet<? extends ChangeLogSet.Entry> lastChangeLogSet = lastChangeSet(runs, i);
            ChangeSetExt lastChangeSet = lastChangeLogSet == null ? null : ChangeSetExt.create(lastChangeLogSet, (WorkflowRun) lastChangeLogSet.getRun());
            runExt.setChangeSet(lastChangeSet);
            if (lastChangeLogSet != null) {
                runExt.setChangeSets(getChangeSets((WorkflowRun) lastChangeLogSet.getRun()).stream().map(changeSet -> ChangeSetExt.create(changeSet, run)).collect(Collectors.toList()));
            } else {
                runExt.setChangeSets(new ArrayList<>());
            }
            try {
                EnvVars environment = run.getEnvironment(new LogTaskListener(null, Level.INFO));
                runExt.setEnvironment(environment.get("ENVIRONMENT"));
                runExt.setPromoteFromEnvironment(environment.get("PROMOTE_FROM_ENVIRONMENT"));
                runExt.setPromoteFromVersion(environment.get("PROMOTE_FROM_VERSION"));
            } catch (IOException | InterruptedException e) {
            }

            if (lastChangeLogSet == null) {
                List<SCM> scms = scms(run);
                if (!scms.isEmpty()) {
                    runExt.setBranch(branch(scms.get(0), run).replaceAll("\\*/", ""));
                }
            } else {
                Map<String, String> commitSources = commitSources((WorkflowRun) lastChangeLogSet.getRun());
                ChangeSetExt.Commit lastCommit = lastChangeSet.getCommits().get(0);
                String commitURL = commitSources.get(lastCommit.getCommitId());

                List<SCM> scms = scms((WorkflowRun) lastChangeLogSet.getRun());
                for (SCM scm : scms) {
                    String scmURL = url(scm);
                    if (Objects.equals(commitURL, scmURL)) {
                        runExt.setBranch(branch(scm, run).replaceAll("\\*/", ""));
                        break;
                    }
                }
            }

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

    private static List<SCM> scms(WorkflowRun run) {
        try {
            Field checkoutsFields = run.getClass().getDeclaredField("checkouts");
            checkoutsFields.setAccessible(true);
            List<Object> checkoutList = (List<Object>) checkoutsFields.get(run);
            List<SCM> scms = new ArrayList<>();

            for (Object checkout : checkoutList) {
                Field scmField = checkout.getClass().getDeclaredField("scm");
                scmField.setAccessible(true);
                SCM scm = (SCM) scmField.get(checkout);
                String url = url(scm);
                if (url != null && !isCMSExcluded(url)) {
                    scms.add(scm);
                }
            }
            return scms;
        } catch (Exception e) {
            return null;
        }
    }

    private static String branch(SCM scm, WorkflowRun run) {
        try {
            if (scm == null) {
                return null;
            }

            String branch = null;
            Field branchesField = scm.getClass().getDeclaredField("branches");
            branchesField.setAccessible(true);
            List<Object> configs = (List<Object>) branchesField.get(scm);
            if (!configs.isEmpty()) {
                Object branchSpec = configs.get(0);
                Field nameField = branchSpec.getClass().getDeclaredField("name");
                nameField.setAccessible(true);
                branch = (String) nameField.get(branchSpec);
            }

            if (branch != null && branch.startsWith("$")) {
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
            }

            if (branch != null) {
                branch = branch.replaceAll("\\*/", "");
            }

            return branch;
        } catch (Exception e) {
            return null;
        }
    }

    private static String url(SCM scm) {
        try {
            if (scm == null) {
                return null;
            }
            Field userRemoteConfigsField = scm.getClass().getDeclaredField("userRemoteConfigs");
            userRemoteConfigsField.setAccessible(true);
            List<Object> userRemoteConfigs = (List<Object>) userRemoteConfigsField.get(scm);
            if (!userRemoteConfigs.isEmpty()) {
                Object userRemoteConfig = userRemoteConfigs.get(0);
                Field urlField = userRemoteConfig.getClass().getDeclaredField("url");
                urlField.setAccessible(true);
                return (String) urlField.get(userRemoteConfig);
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private static ChangeLogSet<? extends ChangeLogSet.Entry> lastChangeSet(List<WorkflowRun> runs, int currentIndex) {
        try {
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

            EnvVars vars = run.getEnvironment(new LogTaskListener(null, Level.INFO));
            String env = vars.get("ENVIRONMENT");
            List<ChangeLogSet<? extends ChangeLogSet.Entry>> changeSets = getChangeSets(run);
            if (changeSets.isEmpty()) {
                if (run.isBuilding()) {
                    return null;
                }
                for (int i = currentIndex + 1; i < runs.size(); i++) {
                    WorkflowRun previousRun = runs.get(i);
                    EnvVars previousVars = previousRun.getEnvironment(new LogTaskListener(null, Level.INFO));
                    String previousEnv = previousVars.get("ENVIRONMENT");
                    if (Objects.equals(env, previousEnv) && !changeSets.isEmpty()) {
                        return lastChangeSet(changeSets);
                    }
                }
                return null;
            } else {
                return lastChangeSet(changeSets);
            }
        } catch (Exception e) {
            LOGGER.error("failed to get last change set", e);
            return null;
        }
    }

    private static ChangeLogSet<? extends ChangeLogSet.Entry> lastChangeSet(List<ChangeLogSet<? extends ChangeLogSet.Entry>> changeSets) {
        ChangeLogSet<? extends ChangeLogSet.Entry> latestChangeSet = null;
        String latestChangeSetCommitterTime = null;
        for (ChangeLogSet<? extends ChangeLogSet.Entry> changeSet : changeSets) {
            Object[] items = changeSet.getItems();
            if (items.length > 0) {
                Object item = items[items.length - 1];
                try {
                    Field committerTimeField = item.getClass().getDeclaredField("committerTime");
                    committerTimeField.setAccessible(true);
                    String committerTime = (String) committerTimeField.get(item);
                    if (latestChangeSetCommitterTime == null) {
                        latestChangeSetCommitterTime = committerTime;
                        latestChangeSet = changeSet;
                    } else if (latestChangeSetCommitterTime.compareTo(committerTime) < 0) {
                        latestChangeSetCommitterTime = committerTime;
                        latestChangeSet = changeSet;
                    }
                } catch (Exception e) {
                    LOGGER.error("failed to get commit time", e);
                }
            }
        }
        if (latestChangeSet == null) {
            latestChangeSet = changeSets.get(0);
        }
        return latestChangeSet;
    }

    private static List<ChangeLogSet<? extends ChangeLogSet.Entry>> getChangeSets(WorkflowRun run) {
        List<ChangeLogSet<? extends ChangeLogSet.Entry>> changeSets = run.getChangeSets();
        List<ChangeLogSet<? extends ChangeLogSet.Entry>> filtered = Lists.newArrayList();
        Map<String, String> commitSources = commitSources(run);
        for (ChangeLogSet<? extends ChangeLogSet.Entry> changeSet : changeSets) {
            if (!isExcluded(changeSet, commitSources)) {
                filtered.add(changeSet);
            }
        }
        return filtered;
    }

    private static String commitId(ChangeLogSet<? extends ChangeLogSet.Entry> changeLogSet) {
        Object[] items = changeLogSet.getItems();
        if (items == null || items.length == 0) {
            return null;
        }
        Object item = items[0];
        try {
            Field idField = item.getClass().getDeclaredField("id");
            idField.setAccessible(true);
            Object id = idField.get(item);
            if (id == null) {
                return null;
            }
            return id.toString();
        } catch (NoSuchFieldException | IllegalAccessException e) {
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static boolean isExcluded(ChangeLogSet<? extends ChangeLogSet.Entry> changeLogSet, Map<String, String> commitSources) {
        if (changeLogSet.isEmptySet()) {
            return true;
        }
        RepositoryBrowser<ChangeLogSet.Entry> repoBrowser = (RepositoryBrowser<ChangeLogSet.Entry>) changeLogSet.getBrowser();
        if (repoBrowser == null) {
            String commitId = commitId(changeLogSet);
            String url = commitSources.get(commitId);
            if (url == null) {
                return false;
            }
            return isCMSExcluded(url);
        } else {
            try {
                URL changeSetLink = repoBrowser.getChangeSetLink(changeLogSet.iterator().next());
                if (changeSetLink == null) {
                    return false;
                }
                String url = changeSetLink.toString();
                if (url == null) {
                    return false;
                }
                return isCMSExcluded(url);
            } catch (IOException e) {
                return false;
            }
        }
    }

    private static boolean isCMSExcluded(String url) {
        if (url == null) {
            return true;
        }
        return url.contains("/jenkins-project-config") || url.contains("/k8s-scripts");
    }

    private static Map<String, String> commitSources(WorkflowRun run) {
        Map<String, String> commitSources = Maps.newHashMap();
        try {
            Field checkoutsFields = run.getClass().getDeclaredField("checkouts");
            checkoutsFields.setAccessible(true);
            List<Object> checkoutList = (List<Object>) checkoutsFields.get(run);
            for (Object checkout : checkoutList) {
                Field scmField = checkout.getClass().getDeclaredField("scm");
                scmField.setAccessible(true);
                SCM scm = (SCM) scmField.get(checkout);
                String url = url(scm);

                Field changelogFileField = checkout.getClass().getDeclaredField("changelogFile");
                changelogFileField.setAccessible(true);
                Object file = changelogFileField.get(checkout);
                List<String> commits = readCommitLogfile(file);

                for (String commit : commits) {
                    commitSources.put(commit, url);
                }
            }
        } catch (Exception e) {
        }
        return commitSources;
    }

    private static List<String> readCommitLogfile(Object file) {
        List<String> commits = Lists.newArrayList();
        if (file instanceof File) {
            try {
                List<String> lines = Files.readLines((File) file, Charsets.UTF_8);
                for (String line : lines) {
                    if (line.startsWith("commit ")) {
                        commits.add(line.substring("commit ".length()).trim());
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return commits;
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
