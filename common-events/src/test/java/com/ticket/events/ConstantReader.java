package com.ticket.events;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

final class ConstantReader {

    private ConstantReader() {
    }

    static List<String> stringConstantsOf(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
            .filter(field -> Modifier.isPublic(field.getModifiers()))
            .filter(field -> Modifier.isStatic(field.getModifiers()))
            .filter(field -> field.getType() == String.class)
            .map(ConstantReader::valueOf)
            .toList();
    }

    private static String valueOf(Field field) {
        try {
            return (String) field.get(null);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot read " + field.getName(), e);
        }
    }
}
