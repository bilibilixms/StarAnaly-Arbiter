package com.staranaly.arbiter.task;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class TaskRunStepStatusConverter implements AttributeConverter<TaskRunStepStatus, String> {

    @Override
    public String convertToDatabaseColumn(TaskRunStepStatus value) {
        return value == null ? null : value.name();
    }

    @Override
    public TaskRunStepStatus convertToEntityAttribute(String value) {
        return value == null ? null : TaskRunStepStatus.valueOf(value);
    }
}
