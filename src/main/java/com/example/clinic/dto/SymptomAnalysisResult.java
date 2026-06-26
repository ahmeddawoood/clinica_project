package com.example.clinic.dto;

public class SymptomAnalysisResult {

    private final String priority;           
    private final boolean urgent;
    private final String suggestedSpecialty;
    private final String urgencyReason;
    private final int confidencePercent;     

    public SymptomAnalysisResult(String priority, boolean urgent,
                                  String suggestedSpecialty, String urgencyReason) {
        this(priority, urgent, suggestedSpecialty, urgencyReason, defaultConfidence(priority));
    }

    public SymptomAnalysisResult(String priority, boolean urgent,
                                  String suggestedSpecialty, String urgencyReason,
                                  int confidencePercent) {
        this.priority = priority;
        this.urgent = urgent;
        this.suggestedSpecialty = suggestedSpecialty;
        this.urgencyReason = urgencyReason;
        this.confidencePercent = confidencePercent;
    }

    private static int defaultConfidence(String priority) {
        return switch (priority) {
            case "CRITICAL" -> 95;
            case "HIGH"     -> 85;
            case "MEDIUM"   -> 70;
            default         -> 60;
        };
    }

    public String getPriority()           { return priority; }
    public boolean isUrgent()             { return urgent; }
    public String getSuggestedSpecialty() { return suggestedSpecialty; }
    public String getUrgencyReason()      { return urgencyReason; }
    public int getConfidencePercent()     { return confidencePercent; }

    
    public String getPriorityBadgeClass() {
        return switch (priority) {
            case "CRITICAL" -> "badge bg-danger";
            case "HIGH"     -> "badge bg-warning text-dark";
            case "MEDIUM"   -> "badge bg-info text-dark";
            default         -> "badge bg-success";
        };
    }
}
