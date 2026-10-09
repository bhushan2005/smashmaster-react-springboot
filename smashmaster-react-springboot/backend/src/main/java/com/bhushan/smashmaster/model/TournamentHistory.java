package com.bhushan.smashmaster.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;

@Entity
@Table(name = "tournament_history")
public class TournamentHistory {
    @Id public String id;
    public String tournamentName;
    public String tournamentDate;
    public long archivedAtEpochMs;
    public int playerCount;
    public int matchCount;
    public int completedMatchCount;
    @Column(columnDefinition="TEXT") public String statisticsJson;
    @Column(columnDefinition="TEXT") public String rosterJson;
    @Column(columnDefinition="TEXT") public String matchesJson;
    public TournamentHistory() {}
}
