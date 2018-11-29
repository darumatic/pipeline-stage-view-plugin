package com.cloudbees.workflow.util;

import org.junit.Test;

import java.lang.reflect.Field;

import static org.junit.Assert.assertNotNull;

/**
 * @author chi
 */
public class ReflectUtilTest {
    @Test
    public void field() {
        Field name = ReflectUtil.field(B.class, "name");
        assertNotNull(name);
    }

    static class A {
        public String name;
    }

    static class B extends A {

    }
}