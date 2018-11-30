package com.cloudbees.workflow.listener;

import com.cloudbees.workflow.util.Git;
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

import javax.annotation.CheckForNull;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * @author chi
 */
@Extension
public class PromoteSCMListener extends SCMListener {
    @Override
    public void onCheckout(Run<?, ?> build, SCM scm, FilePath workspace, TaskListener listener, @CheckForNull File changelogFile, @CheckForNull SCMRevisionState pollingBaseline) throws Exception {
        if (isPromoteBuild(build)) {
            return;
        }
        ParametersActionWrapper actionWrapper = new ParametersActionWrapper(build.getAction(ParametersAction.class));
        SCMWrapper scmWrapper = new SCMWrapper(scm);
        String name = scmWrapper.scmName();
        if (name != null) {
            String remote = workspace.getRemote();
            Optional<String> relativeDirectory = scmWrapper.relativeDirectory();
            if (relativeDirectory.isPresent()) {
                remote = relativePath(remote, relativeDirectory.get());
            }
            ParameterValue parameter = new StringParameterValue(name.toUpperCase() + "_GIT_COMMIT", lastCommit(remote));
            actionWrapper.addParameter(parameter);
        }
    }

    private String relativePath(String base, String path) {
        StringBuilder b = new StringBuilder(base);
        if (!base.endsWith(File.separator)) {
            b.append(File.separator);
        }
        if (path.startsWith(File.separator)) {
            b.append(path, File.separator.length(), path.length());
        } else {
            b.append(path);
        }
        return b.toString();
    }

    private boolean isPromoteBuild(Run build) {
        ParametersAction action = build.getAction(ParametersAction.class);
        if (action == null) {
            return false;
        }
        ParameterValue value = action.getParameter(PromoteUtil.PROMOTE_FROM_ENVIRONMENT);
        return value != null;
    }

    private String lastCommit(String workspace) {
        try {
            return Git.lastCommit(Paths.get(workspace));
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
