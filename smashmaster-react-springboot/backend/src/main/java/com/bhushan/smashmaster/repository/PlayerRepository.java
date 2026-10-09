package com.bhushan.smashmaster.repository;
import com.bhushan.smashmaster.model.Player;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface PlayerRepository extends JpaRepository<Player,String> { List<Player> findAllByOrderByNameAsc(); }
