package com.cloudbees.workflow.util;

import java.lang.reflect.Field;

/**
 * @author chi
 */
public class ReflectUtil {
    public static Field field(Class<?> type, String field) {
        Class<?> current = type;
        while (current != null) {
            for (Field declaredField : current.getDeclaredFields()) {
                if (declaredField.getName().equals(field)) {
                    return declaredField;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }
}
