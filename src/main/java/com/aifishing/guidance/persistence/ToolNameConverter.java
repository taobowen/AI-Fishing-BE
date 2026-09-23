package com.aifishing.guidance.persistence;

import com.aifishing.guidance.contracts.ToolName;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class ToolNameConverter implements AttributeConverter<ToolName, String> {

    @Override
    public String convertToDatabaseColumn(ToolName attribute) {
        return attribute == null ? null : attribute.wire();
    }

    @Override
    public ToolName convertToEntityAttribute(String dbData) {
        return ToolName.fromWire(dbData);
    }
}
