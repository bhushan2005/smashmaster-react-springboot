package com.bhushan.smashmaster.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "players")
public class Player {
    @Id public String id;
    public String name;
    public String gender;
    public long createdAtEpochMs;
    public Player() {}
    public Player(String id, String name, String gender) {
        this.id=id; this.name=name; this.gender=gender; this.createdAtEpochMs=System.currentTimeMillis();
    }
}
