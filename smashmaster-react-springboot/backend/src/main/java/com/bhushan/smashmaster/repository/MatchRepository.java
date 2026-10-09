package com.bhushan.smashmaster.repository;
import com.bhushan.smashmaster.model.MatchRecord;
import org.springframework.data.jpa.repository.JpaRepository;
public interface MatchRepository extends JpaRepository<MatchRecord,String> {}
