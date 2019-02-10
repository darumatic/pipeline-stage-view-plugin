package com.cloudbees.workflow.listener;

import com.cloudbees.workflow.util.PromoteUtil;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.Run;
import hudson.model.StringParameterValue;
import hudson.model.TaskListener;
import hudson.model.listeners.SCMListener;
import hudson.scm.SCM;
import hudson.scm.SCMRevisionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckForNull;
import java.io.File;

/**
 * @author chi
 */
@Extension
public class PromoteSCMListener extends SCMListener {
    private final Logger logger = LoggerFactory.getLogger(PromoteSCMListener.class);

    @Override
    public void onCheckout(Run<?, ?> build, SCM scm, FilePath workspace, TaskListener listener, @CheckForNull File changelogFile, @CheckForNull SCMRevisionState pollingBaseline) throws Exception {
        ParametersAction parametersAction = build.getAction(ParametersAction.class);
        if (parametersAction == null || isPromoteBuild(parametersAction)) {
            return;
        }
        ParametersActionWrapper action = new ParametersActionWrapper(parametersAction);
        SCMWrapper scmWrapper = new SCMWrapper(scm);
        String name = scmWrapper.scmName();
        if (name != null) {
            String key = name.toUpperCase() + "_BRANCH";
            if (!action.hasParameter(key)) {
                logger.info("{}={}", key, scmWrapper.branch());
                action.addParameter(new StringParameterValue(key, scmWrapper.branch()));
            }
        }
    }

    private boolean isPromoteBuild(ParametersAction action) {
        ParameterValue value = action.getParameter(PromoteUtil.PROMOTE_FROM_ENVIRONMENT);
        return value != null;
    }
}
