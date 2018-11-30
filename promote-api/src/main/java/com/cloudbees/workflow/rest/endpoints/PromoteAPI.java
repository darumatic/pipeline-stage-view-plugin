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
package com.cloudbees.workflow.rest.endpoints;

import com.cloudbees.workflow.rest.AbstractWorkflowRunActionHandler;
import com.cloudbees.workflow.rest.endpoints.promote.PromoteBuild;
import com.cloudbees.workflow.util.PromoteUtil;
import com.cloudbees.workflow.util.ReflectUtil;
import com.cloudbees.workflow.util.ServeJson;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import hudson.Extension;
import hudson.model.Action;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.Queue;
import hudson.model.Run;
import hudson.model.StringParameterValue;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * API Action handler to return a single WorkflowJob run.
 * <p>
 * Bound to {@code ${{rootURL}/job/<jobname>/<runId>/wfapi/*}}
 * </p>
 *
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
@Extension
public class PromoteAPI extends AbstractWorkflowRunActionHandler {
    private final Logger logger = LoggerFactory.getLogger(PromoteAPI.class);

    @Restricted(DoNotUse.class) // WebMethod
    @ServeJson
    public PromoteBuild doPromote(@QueryParameter String env) {
        if (Strings.isNullOrEmpty(env)) {
            PromoteBuild build = new PromoteBuild();
            build.environments = ImmutableList.of();
            build.message = "invalid env, can't be empty";
            build.success = false;
            return build;
        }

        List<String> environments = ImmutableList.copyOf(Splitter.on(",").trimResults().omitEmptyStrings().split(env));
        for (String environment : environments) {
            if (!PromoteUtil.ENVIRONMENTS.contains(environment)) {
                PromoteBuild build = new PromoteBuild();
                build.environments = ImmutableList.of();
                build.success = false;
                build.message = "invalid env, not support env " + environment;
                return build;
            }
        }

        for (String environment : environments) {
            WorkflowRun currentBuild = getRun();
            if (currentBuild != null) {
                ParametersAction paramAction = currentBuild.getAction(ParametersAction.class);
                if (paramAction == null) {
                    PromoteBuild build = new PromoteBuild();
                    build.environments = ImmutableList.of();
                    build.success = false;
                    build.message = "invalid job, job must be parameterized build";
                    return build;
                }
                parameterizedRebuild(currentBuild, environment);
            }
        }
        PromoteBuild build = new PromoteBuild();
        build.environments = environments;
        build.success = true;
        return build;
    }

    public void parameterizedRebuild(Run currentBuild, String env) {
        Job project = getProject(currentBuild);
        if (project == null) {
            return;
        }
        project.checkPermission(Item.BUILD);
        List<Action> actions = copyBuildCausesAndAddUserCause(currentBuild);
        ParametersAction action = currentBuild.getAction(ParametersAction.class);

        try {
            ParameterValue environment = new StringParameterValue(PromoteUtil.ENVIRONMENT, env);
            ParameterValue fromEnvironment = new StringParameterValue(PromoteUtil.PROMOTE_FROM_ENVIRONMENT, currentBuild.getEnvironment().get(PromoteUtil.ENVIRONMENT));
            ParameterValue fromVersion = new StringParameterValue(PromoteUtil.PROMOTE_FROM_VERSION, currentBuild.getEnvironment().get("BUILD_NUMBER"));
            actions.add(action.createUpdated(Arrays.asList(environment, fromEnvironment, fromVersion)));
        } catch (Exception e) {
            logger.error("failed to copy promotion variables", e);
        }
        Hudson.getInstance().getQueue().schedule((Queue.Task) currentBuild.getParent(), 0, actions);
    }

    @SuppressWarnings("unchecked")
    private void replaceParameter(ParametersAction action, ParameterValue commit) {
        try {
            Field parametersField = ReflectUtil.field(action.getClass(), "parameters");
            parametersField.setAccessible(true);
            List<ParameterValue> current = (List<ParameterValue>) parametersField.get(action);
            current.add(commit);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private List<Action> copyBuildCausesAndAddUserCause(Run<?, ?> fromBuild) {
        List currentBuildCauses = fromBuild.getCauses();

        List<Action> actions = new ArrayList<Action>(currentBuildCauses.size());
        boolean hasUserCause = false;
        for (Object buildCause : currentBuildCauses) {
            if (buildCause instanceof Cause.UserIdCause) {
                hasUserCause = true;
                actions.add(new CauseAction(new Cause.UserIdCause()));
            } else {
                actions.add(new CauseAction((Cause) buildCause));
            }
        }
        if (!hasUserCause) {
            actions.add(new CauseAction(new Cause.UserIdCause()));
        }

        return actions;
    }

    public Job getProject(Run build) {
        if (build != null) {
            return build.getParent();
        }

        Job currentProject = null;
        StaplerRequest request = Stapler.getCurrentRequest();
        if (request != null) {
            currentProject = request.findAncestorObject(Job.class);
        }
        return currentProject;
    }
}
