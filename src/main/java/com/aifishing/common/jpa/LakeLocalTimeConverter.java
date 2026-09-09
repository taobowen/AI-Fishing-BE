package com.aifishing.common.jpa;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.sql.Time;
import java.time.LocalTime;
import java.time.ZoneOffset;

/**
 * Persist lake-local clock times as TIME without applying {@code hibernate.jdbc.time_zone=UTC}.
 * Hibernate's default {@link Time} binding uses 1970-01-01 in the JVM zone (EST in Toronto),
 * then shifts to UTC — evening ends wrap past midnight and fail {@code trips_time_range_chk}.
 */
@Converter
public class LakeLocalTimeConverter implements AttributeConverter<LocalTime, Time> {

    @Override
    public Time convertToDatabaseColumn(LocalTime attribute) {
        if (attribute == null) {
            return null;
        }
        return new Time(attribute.toSecondOfDay() * 1000L);
    }

    @Override
    public LocalTime convertToEntityAttribute(Time dbData) {
        if (dbData == null) {
            return null;
        }
        return java.time.Instant.ofEpochMilli(dbData.getTime()).atOffset(ZoneOffset.UTC).toLocalTime();
    }
}
