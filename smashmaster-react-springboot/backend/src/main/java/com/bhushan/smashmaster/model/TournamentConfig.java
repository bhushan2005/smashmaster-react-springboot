package com.bhushan.smashmaster.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "tournament_config")
public class TournamentConfig {
    @Id public Long id;
    public String tournamentName;
    public String tournamentDate;
    @jakarta.persistence.Column(columnDefinition="TEXT") public String courtsJson;
    public int matchDurationMinutes;
    public TournamentConfig() {}
}
