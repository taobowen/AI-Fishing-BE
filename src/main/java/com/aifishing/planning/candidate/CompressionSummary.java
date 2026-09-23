package com.aifishing.planning.candidate;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class CompressionSummary {

    private final EnumMap<CandidateCompressionReason, Integer> reasons = new EnumMap<>(CandidateCompressionReason.class);
    private int beforeIdentity;
    private int afterIdentity;
    private int selectedTargetsWithNoPhysicalZone;
    private int zonesSelected;
    private int atomicsSelected;
    private int macroVisitOptions;
    private int macroVisitOptionCap;
    private Map<String, Integer> macroOptionsByRegion = new LinkedHashMap<>();

    public void add(CandidateCompressionReason reason, int count) {
        if (reason == null || count <= 0) {
            return;
        }
        reasons.merge(reason, count, Integer::sum);
    }

    public void merge(CompressionSummary other) {
        if (other == null) {
            return;
        }
        other.reasons.forEach(this::add);
        beforeIdentity += other.beforeIdentity;
        afterIdentity += other.afterIdentity;
        selectedTargetsWithNoPhysicalZone += other.selectedTargetsWithNoPhysicalZone;
        zonesSelected += other.zonesSelected;
        atomicsSelected += other.atomicsSelected;
        macroVisitOptions += other.macroVisitOptions;
        if (other.macroVisitOptionCap > macroVisitOptionCap) {
            macroVisitOptionCap = other.macroVisitOptionCap;
        }
        if (other.macroOptionsByRegion != null && !other.macroOptionsByRegion.isEmpty()) {
            other.macroOptionsByRegion.forEach((key, value) -> macroOptionsByRegion.merge(key, value, Integer::sum));
        }
    }

    public int count(CandidateCompressionReason reason) {
        return reasons.getOrDefault(reason, 0);
    }

    public void setBeforeIdentity(int beforeIdentity) {
        this.beforeIdentity = beforeIdentity;
    }

    public void setAfterIdentity(int afterIdentity) {
        this.afterIdentity = afterIdentity;
    }

    public void setSelectedTargetsWithNoPhysicalZone(int selectedTargetsWithNoPhysicalZone) {
        this.selectedTargetsWithNoPhysicalZone = selectedTargetsWithNoPhysicalZone;
    }

    public void setZonesSelected(int zonesSelected) {
        this.zonesSelected = zonesSelected;
    }

    public void setAtomicsSelected(int atomicsSelected) {
        this.atomicsSelected = atomicsSelected;
    }

    public void setMacroVisitOptions(int macroVisitOptions) {
        this.macroVisitOptions = macroVisitOptions;
    }

    public void setMacroVisitOptionCap(int macroVisitOptionCap) {
        this.macroVisitOptionCap = macroVisitOptionCap;
    }

    public void setMacroOptionsByRegion(Map<String, Integer> macroOptionsByRegion) {
        this.macroOptionsByRegion = macroOptionsByRegion == null ? new LinkedHashMap<>() : new LinkedHashMap<>(macroOptionsByRegion);
    }

    public Map<String, Integer> macroOptionsByRegion() {
        return Map.copyOf(macroOptionsByRegion);
    }

    public int beforeIdentity() {
        return beforeIdentity;
    }

    public int afterIdentity() {
        return afterIdentity;
    }

    public int selectedTargetsWithNoPhysicalZone() {
        return selectedTargetsWithNoPhysicalZone;
    }

    public int zonesSelected() {
        return zonesSelected;
    }

    public int atomicsSelected() {
        return atomicsSelected;
    }

    public int macroVisitOptions() {
        return macroVisitOptions;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("beforeIdentity", beforeIdentity);
        out.put("afterIdentity", afterIdentity);
        out.put("selectedTargetsWithNoPhysicalZone", selectedTargetsWithNoPhysicalZone);
        out.put("zonesSelected", zonesSelected);
        out.put("atomicsSelected", atomicsSelected);
        out.put("macroVisitOptions", macroVisitOptions);
        out.put("macroVisitOptionCap", macroVisitOptionCap);
        if (!macroOptionsByRegion.isEmpty()) {
            out.put("macroOptionsByRegion", new LinkedHashMap<>(macroOptionsByRegion));
        }
        Map<String, Integer> byReason = new LinkedHashMap<>();
        for (CandidateCompressionReason reason : CandidateCompressionReason.values()) {
            int count = count(reason);
            if (count > 0) {
                byReason.put(reason.name(), count);
            }
        }
        out.put("reasons", byReason);
        return out;
    }
}
