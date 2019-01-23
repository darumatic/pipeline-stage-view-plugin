package com.cloudbees.workflow.listener;

import hudson.Extension;
import hudson.XmlFile;
import hudson.model.Action;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.Saveable;
import hudson.model.StringParameterValue;
import hudson.model.listeners.SaveableListener;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author chi
 */
@Extension
public class PromoteSaveableListener extends SaveableListener {
    private final Logger logger = LoggerFactory.getLogger(PromoteSaveableListener.class);

    @Override
    public void onChange(Saveable o, XmlFile file) {
        if (o instanceof WorkflowRun) {
            WorkflowRun run = (WorkflowRun) o;
            ParametersActionWrapper wrapper = new ParametersActionWrapper(run.getAction(ParametersAction.class));

            for (Action action : run.getAllActions()) {
                if (BuildDataWrapper.isBuildData(action)) {
                    BuildDataWrapper buildDataWrapper = new BuildDataWrapper(action);
                    logger.info("revisions:{}", buildDataWrapper.revisions());
                    for (BuildDataWrapper.Revision revision : buildDataWrapper.revisions()) {
                        String key = revision.repo.toUpperCase() + "_GIT_COMMIT";
                        if (!wrapper.hasParameter(key)) {
                            if (revision.number == run.number) {
                                ParameterValue branchValue = wrapper.getParameter(revision.repo.toUpperCase() + "_BRANCH");
                                if (branchValue != null && branchValue.getValue() != null && branchValue.getValue().toString().equalsIgnoreCase(revision.branch)) {
                                    wrapper.addParameter(new StringParameterValue(key, revision.commitHash.substring(0, 7)));
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
