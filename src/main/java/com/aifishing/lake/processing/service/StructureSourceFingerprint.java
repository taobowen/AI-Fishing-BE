package com.aifishing.lake.processing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

@Component
public class StructureSourceFingerprint {

    private final ObjectMapper mapper;

    public StructureSourceFingerprint(ObjectMapper objectMapper) {
        this.mapper = objectMapper.copy()
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
                .configure(SerializationFeature.INDENT_OUTPUT, false);
    }

    public String id(Map<String, Object> snapshot) {
        try {
            byte[] canonical = mapper.writeValueAsBytes(snapshot == null ? Map.of() : snapshot);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical);
            return "src-" + HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to fingerprint structure source snapshot", ex);
        }
    }
}
