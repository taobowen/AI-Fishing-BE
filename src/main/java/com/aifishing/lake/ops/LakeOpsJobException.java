package com.aifishing.lake.ops;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class LakeOpsJobException extends RuntimeException {

    private final LakeOpsFailureCode failureCode;
    private final Map<String, Object> result;

    public LakeOpsJobException(LakeOpsFailureCode failureCode, String message) {
        this(failureCode, message, Map.of());
    }

    public LakeOpsJobException(LakeOpsFailureCode failureCode, String message, Map<String, Object> result) {
        super(message);
        this.failureCode = failureCode;
        LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
        if (result != null) {
            result.forEach((key, value) -> {
                if (key != null) {
                    copy.put(key, value);
                }
            });
        }
        this.result = Collections.unmodifiableMap(copy);
    }

    public LakeOpsFailureCode getFailureCode() {
        return failureCode;
    }

    public Map<String, Object> getResult() {
        return result;
    }
}
