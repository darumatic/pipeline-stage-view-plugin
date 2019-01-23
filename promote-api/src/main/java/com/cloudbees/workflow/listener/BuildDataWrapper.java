package com.cloudbees.workflow.listener;

import com.cloudbees.workflow.util.ReflectUtil;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author chi
 */
public class BuildDataWrapper {
    private final Logger logger = LoggerFactory.getLogger(BuildDataWrapper.class);

    private static final Pattern REVISION_PATTERN = Pattern.compile("Build #(\\d+) of Revision ([0-9a-z]+) \\((.*)\\)");
    private final Object buildData;

    public BuildDataWrapper(Object buildData) {
        this.buildData = buildData;
    }

    public List<Revision> revisions() {
        List<Revision> revisions = Lists.newArrayList();
        try {
            Field buildField = ReflectUtil.field(buildData.getClass(), "buildsByBranchName");
            buildField.setAccessible(true);
            Map current = (Map) buildField.get(buildData);
            for (Object value : current.values()) {
                String message = value.toString();
                Matcher matcher = REVISION_PATTERN.matcher(message);
                if (matcher.matches()) {
                    Revision revision = new Revision();
                    revision.number = Integer.parseInt(matcher.group(1));
                    revision.commitHash = matcher.group(2);
//                    "refs/remotes/git1/master" -> "Build #163 of Revision 819bb72310f3675848d7fd5dc833aadd4a57db72 (refs/remotes/git1/master)"
                    String branch = matcher.group(3);
                    if (branch.startsWith("refs/remotes/")) {
                        branch = branch.substring("refs/remotes/".length());
                    }
                    String[] names = branch.split("/");
                    if (names.length == 2) {
                        revision.repo = names[0];
                        revision.branch = names[1];
                        revisions.add(revision);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("failed to add parameter", e);
        }
        return revisions;
    }

    public static boolean isBuildData(Object object) {
        return object.getClass().getName().equals("hudson.plugins.git.util.BuildData");
    }

    public static class Revision {
        public int number;
        public String branch;
        public String repo;
        public String commitHash;

        @Override
        public String toString() {
            return Objects.toStringHelper(this)
                .add("number", number)
                .add("branch", branch)
                .add("repo", repo)
                .add("commitHash", commitHash)
                .toString();
        }
    }
}
