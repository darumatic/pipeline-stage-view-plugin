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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckForNull;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

/**
 * @author chi
 */
@Extension
public class PromoteSCMListener extends SCMListener {
    private final Logger logger = LoggerFactory.getLogger(PromoteSCMListener.class);

    @Override
    public void onCheckout(Run<?, ?> build, SCM scm, FilePath workspace, TaskListener listener, @CheckForNull File changelogFile, @CheckForNull SCMRevisionState pollingBaseline) throws Exception {
        if (isPromoteBuild(build)) {
            return;
        }
        ParametersAction action = build.getAction(ParametersAction.class);
        String name = scmName(scm);
        if (name != null) {
            String remote = workspace.getRemote();
            Optional<String> relativeDirectory = relativeDirectory(scm);
            if (relativeDirectory.isPresent()) {
                remote = relativePath(remote, relativeDirectory.get());
            }
            ParameterValue commit = new StringParameterValue(name.toUpperCase() + "_GIT_COMMIT", lastCommit(remote));
            appendParameter(action, commit);
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

    private Optional<String> relativeDirectory(SCM scm) {
        try {
            Field extensionsField = ReflectUtil.field(scm.getClass(), "extensions");
            extensionsField.setAccessible(true);
            List extensions = ((List) extensionsField.get(scm));

            for (Object extension : extensions) {
                if (extension.getClass().getSimpleName().equals("RelativeTargetDirectory")) {
                    Field relativeTargetDirField = ReflectUtil.field(extension.getClass(), "relativeTargetDir");
                    relativeTargetDirField.setAccessible(true);
                    return Optional.of((String) relativeTargetDirField.get(extension));
                }
            }
            return Optional.empty();
        } catch (Exception e) {
            logger.error("failed to get relative directory", e);
            return Optional.empty();
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
            logger.error("failed to get scm name", e);
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
