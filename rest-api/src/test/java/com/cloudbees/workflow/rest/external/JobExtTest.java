package com.cloudbees.workflow.rest.external;

import org.junit.Test;

/**
 * @author chi
 */
public class JobExtTest {
    @Test
    public void branch() {
        String branch = JobExt.branch(null);
        System.out.println(branch);
    }
}