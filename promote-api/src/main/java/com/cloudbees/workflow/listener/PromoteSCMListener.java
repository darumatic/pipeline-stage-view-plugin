package com.cloudbees.workflow.listener;

import com.cloudbees.workflow.util.Git;
import com.cloudbees.workflow.util.ReflectUtil;
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
import java.lang.reflect.Field;
import java.nio.file.Paths;
import java.util.List;

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
        ParametersAction action = build.getAction(ParametersAction.class);
        String name = scmName(scm);
        if (name != null) {
            ParameterValue commit = new StringParameterValue(name.toUpperCase() + "_GIT_COMMIT", lastCommit(workspace.getRemote()));
            appendParameter(action, commit);
        }
    }

    private String scmName(SCM scm) {
        try {
            Field userRemoteConfigsField = ReflectUtil.field(scm.getClass(), "userRemoteConfigs");
            userRemoteConfigsField.setAccessible(true);
            Object userRemoteConfig = ((List) userRemoteConfigsField.get(scm)).get(0);
            Field nameField = ReflectUtil.field(userRemoteConfig.getClass(), "name");
            nameField.setAccessible(true);
            Object name = nameField.get(userRemoteConfig);

            if (name == null) {
                Field urlField = ReflectUtil.field(userRemoteConfig.getClass(), "url");
                urlField.setAccessible(true);
                String url = (String) urlField.get(userRemoteConfig);
                return projectName(url);
            }
            return (String) name;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private String projectName(String url) {
        if (url == null) {
            return null;
        }
        int p = url.lastIndexOf("/");
        if (p > 0) {
            String name = url.substring(p + 1);
            return name.replaceAll(".git", "");
        }
        return null;
    }

    private boolean isPromoteBuild(Run build) {
        ParametersAction action = build.getAction(ParametersAction.class);
        if (action == null) {
            return false;
        }
        ParameterValue value = action.getParameter("PROMOTE_FROM_ENVIRONMENT");
        return value != null;
    }

    @SuppressWarnings("unchecked")
    private void appendParameter(ParametersAction action, ParameterValue commit) {
        try {
            Field parametersField = ReflectUtil.field(action.getClass(), "parameters");
            parametersField.setAccessible(true);
            List<ParameterValue> current = (List<ParameterValue>) parametersField.get(action);
            current.add(commit);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String lastCommit(String workspace) {
        try {
            return Git.lastCommit(Paths.get(workspace));
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
