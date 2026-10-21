package com.staranaly.arbiter.task;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class TaskRunStepKindConverter implements AttributeConverter<TaskRunStepKind, String> {

    @Override
    public String convertToDatabaseColumn(TaskRunStepKind value) {
        return value == null ? null : value.name();
    }

    @Override
    public TaskRunStepKind convertToEntityAttribute(String value) {
        return value == null ? null : TaskRunStepKind.valueOf(value);
    }
}
