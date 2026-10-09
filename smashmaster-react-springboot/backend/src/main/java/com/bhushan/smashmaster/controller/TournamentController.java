package com.bhushan.smashmaster.controller;

import com.bhushan.smashmaster.model.MatchRecord;
import com.bhushan.smashmaster.model.Player;
import com.bhushan.smashmaster.service.TournamentService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@RequestMapping("/api")
public class TournamentController {
    private final TournamentService service;
    public TournamentController(TournamentService service){this.service=service;}

    @GetMapping("/players") public List<Player> players(){return service.players();}
    @PostMapping("/players") public Player addPlayer(@RequestBody Map<String,String> body){return service.addPlayer(body.get("name"),body.get("gender"));}
    @GetMapping("/matches") public List<MatchRecord> matches(){return service.matches();}
    @PostMapping("/matches/generate") public List<MatchRecord> generate(){return service.generate();}
    @PostMapping("/matches/extra") public List<MatchRecord> extra(){return service.addExtraMatches();}
    @PostMapping("/matches/{id}/score") public MatchRecord score(@PathVariable String id,@RequestBody Map<String,Integer> body){return service.saveScore(id,body.getOrDefault("score1",-1),body.getOrDefault("score2",-1));}
    @GetMapping("/config") public Map<String,Object> config(){return service.config();}
    @PutMapping("/config") public Map<String,Object> saveConfig(@RequestBody Map<String,Object> body){return service.saveConfig(body);}
    @GetMapping("/leaderboard") public List<Map<String,Object>> leaderboard(){return service.leaderboard();}
    @GetMapping("/statistics") public Map<String,Object> statistics(){return service.statistics();}
    @GetMapping("/history") public Object history(){return service.history();}
    @GetMapping("/export/json") public Map<String,Object> exportJson(){return service.export();}
    @GetMapping(value="/export/whatsapp", produces="text/plain; charset=UTF-8") public ResponseEntity<byte[]> exportWhatsApp(){
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=smashmaster-whatsapp.txt").contentType(new MediaType("text","plain",StandardCharsets.UTF_8)).body(service.whatsappText().getBytes(StandardCharsets.UTF_8));
    }
    @GetMapping(value="/export/csv", produces="text/csv") public ResponseEntity<byte[]> exportCsv(){
        StringBuilder csv=new StringBuilder("Match,Round,Court,Start,End,Team 1 Player 1,Team 1 Player 2,Team 2 Player 1,Team 2 Player 2,Score 1,Score 2,Completed,Extra\n");
        Map<String,String> names=new HashMap<>();service.players().forEach(p->names.put(p.id,p.name));
        for(MatchRecord m:service.matches())csv.append(m.matchNumber).append(',').append(m.round).append(',').append(q(m.court)).append(',').append(q(m.startTime)).append(',').append(q(m.endTime)).append(',').append(q(names.get(m.team1Player1))).append(',').append(q(names.get(m.team1Player2))).append(',').append(q(names.get(m.team2Player1))).append(',').append(q(names.get(m.team2Player2))).append(',').append(m.score1==null?"":m.score1).append(',').append(m.score2==null?"":m.score2).append(',').append(m.completed).append(',').append(m.extraMatch).append('\n');
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=smashmaster-matches.csv").contentType(new MediaType("text","csv",StandardCharsets.UTF_8)).body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }
    private String q(String s){return "\""+Objects.toString(s,"").replace("\"","\"\"")+"\"";}
    @DeleteMapping("/tournament") public Map<String,String> reset(){service.reset();return Map.of("message","Tournament archived and reset.");}
    @GetMapping("/health") public Map<String,String> health(){return Map.of("status","ok","database","postgresql");}
    @ExceptionHandler({IllegalArgumentException.class,NoSuchElementException.class}) public ResponseEntity<Map<String,String>> clientError(RuntimeException e){return ResponseEntity.badRequest().body(Map.of("message",Objects.toString(e.getMessage(),"Invalid request.")));}
}
