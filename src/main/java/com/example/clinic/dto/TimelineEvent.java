package com.example.clinic.dto;

import java.time.LocalDateTime;

public class TimelineEvent {
    private final LocalDateTime date;
    private final String type;
    private final String title;
    private final String subtitle;
    private final String description;
    private final Long entityId;

    public TimelineEvent(LocalDateTime date, String type, String title,
                         String subtitle, String description, Long entityId) {
        this.date = date;
        this.type = type;
        this.title = title;
        this.subtitle = subtitle;
        this.description = description;
        this.entityId = entityId;
    }

    public LocalDateTime getDate()    { return date; }
    public String getType()           { return type; }
    public String getTitle()          { return title; }
    public String getSubtitle()       { return subtitle; }
    public String getDescription()    { return description; }
    public Long getEntityId()         { return entityId; }
}
