package com.staranaly.arbiter.task;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class TaskStatusConverter implements AttributeConverter<TaskStatus, String> {

    @Override
    public String convertToDatabaseColumn(TaskStatus status) {
        return status == null ? null : status.name();
    }

    @Override
    public TaskStatus convertToEntityAttribute(String value) {
        return value == null ? null : TaskStatus.valueOf(value);
    }
}
