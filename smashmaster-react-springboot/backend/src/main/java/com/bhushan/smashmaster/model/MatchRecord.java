package com.bhushan.smashmaster.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "matches")
public class MatchRecord {
    @Id public String id;
    public int matchNumber;
    public int round;
    public String court;
    public String startTime;
    public String endTime;
    public String team1Player1;
    public String team1Player2;
    public String team2Player1;
    public String team2Player2;
    public Integer score1;
    public Integer score2;
    public boolean completed;
    public boolean extraMatch;
    public long createdAtEpochMs;
    public MatchRecord() {}
    public MatchRecord(String id,int number,int round,String court,String start,String end,String a,String b,String c,String d,boolean extra) {
        this.id=id; this.matchNumber=number; this.round=round; this.court=court; this.startTime=start; this.endTime=end;
        this.team1Player1=a; this.team1Player2=b; this.team2Player1=c; this.team2Player2=d; this.extraMatch=extra;
        this.createdAtEpochMs=System.currentTimeMillis();
    }
}
