package com.cloudbees.workflow.listener;

import com.google.common.base.Strings;
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.ParametersAction;
import hudson.model.StringParameterValue;
import hudson.model.listeners.RunListener;
import hudson.util.LogTaskListener;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.logging.Level;

/**
 * @author chi
 */
@Extension
public class GitlabRunListener extends RunListener<WorkflowRun> {
    private static final String GIT_BRANCH = "GIT_BRANCH";
    private static final String GITLAB_BRANCH = "gitlabBranch";
    private final Logger logger = LoggerFactory.getLogger(PromoteSaveableListener.class);

    @Override
    public void onInitialize(WorkflowRun run) {
        try {
            EnvVars environment = run.getEnvironment(new LogTaskListener(null, Level.INFO));
            final String gitlabBranch = environment.get(GITLAB_BRANCH);
            if (!Strings.isNullOrEmpty(gitlabBranch)) {
                environment.put(GIT_BRANCH, gitlabBranch);
                ParametersActionWrapper wrapper = new ParametersActionWrapper(run.getAction(ParametersAction.class));
                logger.info("action wrapper, {}, {}", wrapper, wrapper.hasParameter(GIT_BRANCH));
                if (wrapper.hasParameter(GIT_BRANCH)) {
                    wrapper.setParameter(GIT_BRANCH, gitlabBranch);
                } else {
                    wrapper.addParameter(new StringParameterValue(GIT_BRANCH, gitlabBranch));
                }
            }

        } catch (IOException | InterruptedException e) {
            logger.error("failed to get environments", e);
        }
    }
}
