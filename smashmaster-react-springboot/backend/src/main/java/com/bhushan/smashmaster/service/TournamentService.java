package com.bhushan.smashmaster.service;

import com.bhushan.smashmaster.model.*;
import com.bhushan.smashmaster.repository.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TournamentService {
    private final PlayerRepository players;
    private final MatchRepository matches;
    private final TournamentConfigRepository configs;
    private final TournamentHistoryRepository history;
    private final ObjectMapper mapper = new ObjectMapper();
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final int DEFAULT_DURATION = 8;

    public TournamentService(PlayerRepository players, MatchRepository matches, TournamentConfigRepository configs, TournamentHistoryRepository history) {
        this.players=players; this.matches=matches; this.configs=configs; this.history=history;
    }

    public synchronized List<Player> players(){ return players.findAllByOrderByNameAsc(); }
    public synchronized List<MatchRecord> matches(){ return matches.findAll().stream().sorted(Comparator.comparingInt(m->m.matchNumber)).collect(Collectors.toList()); }

    public synchronized Player addPlayer(String name,String gender){
        String clean=name==null?"":name.trim().replaceAll("\\s+"," ");
        if(clean.isBlank()) throw new IllegalArgumentException("Player name is required.");
        if(players.findAll().stream().anyMatch(p->p.name.trim().equalsIgnoreCase(clean))) throw new IllegalArgumentException("A player with that name already exists.");
        String g="Female".equalsIgnoreCase(gender)?"Female":"Male";
        return players.save(new Player(UUID.randomUUID().toString(),clean,g));
    }

    public synchronized Map<String,Object> config(){
        TournamentConfig c=configs.findById(1L).orElseGet(this::defaultConfig);
        try {
            return Map.of("tournamentName",safe(c.tournamentName,"SmashMaster Tournament"),"tournamentDate",safe(c.tournamentDate,java.time.LocalDate.now().toString()),
                "courts",mapper.readValue(safe(c.courtsJson,"[]"),new TypeReference<List<Map<String,Object>>>(){}),"matchDurationMinutes",c.matchDurationMinutes>0?c.matchDurationMinutes:DEFAULT_DURATION);
        } catch(Exception e){ throw new IllegalStateException("Could not read tournament settings.",e); }
    }

    @Transactional public synchronized Map<String,Object> saveConfig(Map<String,Object> body){
        TournamentConfig c=configs.findById(1L).orElseGet(this::defaultConfig);
        c.tournamentName=safe(Objects.toString(body.get("tournamentName"),"SmashMaster Tournament").trim(),"SmashMaster Tournament");
        c.tournamentDate=safe(Objects.toString(body.get("tournamentDate"),java.time.LocalDate.now().toString()),java.time.LocalDate.now().toString());
        Object raw=body.get("courts");
        try {
            List<Map<String,Object>> courtList=raw instanceof List<?> ? (List<Map<String,Object>>)raw : defaultCourts();
            if(courtList.isEmpty()) throw new IllegalArgumentException("Configure at least one court.");
            for(Map<String,Object> court:courtList){
                String name=Objects.toString(court.get("name"),"").trim();
                LocalTime start=LocalTime.parse(Objects.toString(court.get("startTime"),"17:00"));
                LocalTime end=LocalTime.parse(Objects.toString(court.get("endTime"),"19:00"));
                if(name.isBlank()||!end.isAfter(start)) throw new IllegalArgumentException("Each court needs a name and an end time after its start time.");
            }
            c.courtsJson=mapper.writeValueAsString(courtList);
        } catch(IllegalArgumentException e){throw e;} catch(Exception e){throw new IllegalArgumentException("Court settings are invalid.");}
        int duration=body.get("matchDurationMinutes") instanceof Number n?n.intValue():DEFAULT_DURATION;
        if(duration<5||duration>120) throw new IllegalArgumentException("Match duration must be between 5 and 120 minutes.");
        c.matchDurationMinutes=duration; configs.save(c); return config();
    }

    private TournamentConfig defaultConfig(){
        TournamentConfig c=new TournamentConfig(); c.id=1L; c.tournamentName="SmashMaster Tournament"; c.tournamentDate=java.time.LocalDate.now().toString();
        c.matchDurationMinutes=DEFAULT_DURATION;
        try{c.courtsJson=mapper.writeValueAsString(defaultCourts());}catch(Exception ignored){c.courtsJson="[]";}
        return configs.save(c);
    }
    private List<Map<String,Object>> defaultCourts(){return new ArrayList<>(List.of(court("Court 1","17:00","19:00"),court("Court 2","17:00","19:00")));}
    private Map<String,Object> court(String name,String start,String end){return new LinkedHashMap<>(Map.of("name",name,"startTime",start,"endTime",end));}
    private String safe(String s,String fallback){return s==null||s.isBlank()?fallback:s;}

    @Transactional public synchronized List<MatchRecord> generate(){
        List<Player> roster=players(); if(roster.size()<4) throw new IllegalArgumentException("Add at least four players before generating matches.");
        matches.deleteAll(); List<Map<String,Object>> courts=(List<Map<String,Object>>)config().get("courts");
        int duration=(int)config().get("matchDurationMinutes");
        List<Candidate> candidates=allCandidates(roster); Collections.shuffle(candidates);
        int target=Math.max(3,Math.min(candidates.size(),roster.size()*2));
        Map<String,Integer> appearances=new HashMap<>(); Map<String,Integer> partnerCount=new HashMap<>();
        Map<String,LocalTime> available=new HashMap<>(); Map<String,Integer> consecutive=new HashMap<>(); Map<String,LocalTime> lastEnd=new HashMap<>();
        Map<String,LocalTime> courtClock=new LinkedHashMap<>(); Map<String,LocalTime> courtEnd=new LinkedHashMap<>();
        for(Map<String,Object> c:courts){String name=Objects.toString(c.get("name"));courtClock.put(name,LocalTime.parse(Objects.toString(c.get("startTime"))));courtEnd.put(name,LocalTime.parse(Objects.toString(c.get("endTime"))));}
        List<MatchRecord> out=new ArrayList<>(); Set<String> usedCandidates=new HashSet<>(); int round=1;
        while(out.size()<target){
            boolean placedInRound=false;
            List<String> courtNames=new ArrayList<>(courtClock.keySet());
            courtNames.sort(Comparator.comparing(courtClock::get));
            for(String court:courtNames){
                LocalTime start=courtClock.get(court); Candidate best=null; LocalTime bestStart=null; int bestScore=Integer.MAX_VALUE;
                for(Candidate cand:candidates){if(usedCandidates.contains(cand.key))continue;
                    LocalTime feasible=start;
                    for(String pid:cand.ids) if(available.containsKey(pid)&&available.get(pid).isAfter(feasible)) feasible=available.get(pid);
                    boolean needsRest=false;
                    for(String pid:cand.ids) if(lastEnd.containsKey(pid)&&lastEnd.get(pid).equals(feasible)&&consecutive.getOrDefault(pid,0)>=2) needsRest=true;
                    if(needsRest) feasible=feasible.plusMinutes(8);
                    if(!feasible.plusMinutes(duration).isAfter(courtEnd.get(court))){
                        int score=0; for(String pid:cand.ids)score+=appearances.getOrDefault(pid,0)*10;
                        score+=partnerCount.getOrDefault(pair(cand.ids[0],cand.ids[1]),0)*5+partnerCount.getOrDefault(pair(cand.ids[2],cand.ids[3]),0)*5;
                        for(String pid:cand.ids) if(lastEnd.containsKey(pid)&&lastEnd.get(pid).equals(feasible)) score+=100;
                        if(score<bestScore){bestScore=score;best=cand;bestStart=feasible;}
                    }
                }
                if(best==null)continue;
                LocalTime end=bestStart.plusMinutes(duration); int no=out.size()+1;
                MatchRecord m=new MatchRecord(UUID.randomUUID().toString(),no,round,court,bestStart.format(TIME),end.format(TIME),best.ids[0],best.ids[1],best.ids[2],best.ids[3],false);
                out.add(m);usedCandidates.add(best.key); courtClock.put(court,end);
                for(String pid:best.ids){
                    boolean immediate=lastEnd.containsKey(pid)&&lastEnd.get(pid).equals(bestStart);
                    consecutive.put(pid,immediate?consecutive.getOrDefault(pid,0)+1:1);
                    appearances.merge(pid,1,Integer::sum);available.put(pid,end);lastEnd.put(pid,end);
                }
                partnerCount.merge(pair(best.ids[0],best.ids[1]),1,Integer::sum);partnerCount.merge(pair(best.ids[2],best.ids[3]),1,Integer::sum);
                placedInRound=true; if(out.size()>=target)break;
            }
            if(!placedInRound)break; round++;
        }
        if(out.isEmpty())throw new IllegalArgumentException("The court time windows are too short to fit a match.");
        return matches.saveAll(out);
    }

    private List<Candidate> allCandidates(List<Player> p){
        List<Candidate> out=new ArrayList<>();
        for(int a=0;a<p.size();a++)for(int b=a+1;b<p.size();b++)for(int c=b+1;c<p.size();c++)for(int d=c+1;d<p.size();d++){
            String[] x={p.get(a).id,p.get(b).id,p.get(c).id,p.get(d).id};
            addCandidate(out,x[0],x[1],x[2],x[3]);addCandidate(out,x[0],x[2],x[1],x[3]);addCandidate(out,x[0],x[3],x[1],x[2]);
        }
        return out;
    }
    private void addCandidate(List<Candidate> out,String a,String b,String c,String d){String key=List.of(pair(a,b),pair(c,d)).stream().sorted().collect(Collectors.joining("|"));out.add(new Candidate(key,new String[]{a,b,c,d}));}
    private String pair(String a,String b){return a.compareTo(b)<0?a+"::"+b:b+"::"+a;}
    private record Candidate(String key,String[] ids){}

    @Transactional public synchronized MatchRecord saveScore(String id,int s1,int s2){
        if(s1<0||s2<0||s1>11||s2>11)throw new IllegalArgumentException("Scores must be between 0 and 11.");
        if((s1==11)==(s2==11))throw new IllegalArgumentException("Exactly one team must have 11 points.");
        MatchRecord m=matches.findById(id).orElseThrow(()->new NoSuchElementException("Match not found."));m.score1=s1;m.score2=s2;m.completed=true;return matches.save(m);
    }

    @Transactional public synchronized List<MatchRecord> addExtraMatches(){
        List<Player> roster=players(); if(roster.size()<4)throw new IllegalArgumentException("Add at least four players first.");
        List<MatchRecord> existing=matches(); if(existing.isEmpty())throw new IllegalArgumentException("Generate the main schedule first.");
        List<Map<String,Object>> courts=(List<Map<String,Object>>)config().get("courts");int duration=(int)config().get("matchDurationMinutes");
        Map<String,Integer> counts=new HashMap<>();for(Player p:roster)counts.put(p.id,0);for(MatchRecord m:existing)for(String id:ids(m))counts.merge(id,1,Integer::sum);
        Set<String> alreadyExtra=new HashSet<>();for(MatchRecord m:existing)if(m.extraMatch)alreadyExtra.addAll(Arrays.asList(ids(m)));
        List<Player> eligible=roster.stream().filter(p->!alreadyExtra.contains(p.id)).sorted(Comparator.comparingInt(p->counts.get(p.id))).toList();
        if(eligible.size()<4)throw new IllegalArgumentException("Each player can receive at most one extra match; not enough eligible players remain.");
        Map<String,LocalTime> clocks=new LinkedHashMap<>(),ends=new LinkedHashMap<>();
        for(Map<String,Object> c:courts){String name=Objects.toString(c.get("name"));LocalTime end=LocalTime.parse(Objects.toString(c.get("endTime")));ends.put(name,end);clocks.put(name,LocalTime.parse(Objects.toString(c.get("startTime"))));}
        for(MatchRecord m:existing){if(m.court!=null&&clocks.containsKey(m.court)&&m.endTime!=null){LocalTime e=LocalTime.parse(m.endTime);if(e.isAfter(clocks.get(m.court)))clocks.put(m.court,e);}}
        Map<String,LocalTime> available=new HashMap<>();for(MatchRecord m:existing)if(m.endTime!=null)for(String id:ids(m)){LocalTime e=LocalTime.parse(m.endTime);if(!available.containsKey(id)||e.isAfter(available.get(id)))available.put(id,e);}
        List<MatchRecord> added=new ArrayList<>();int n=existing.size();
        for(int i=0;i+3<eligible.size();i+=4){List<String> ids=eligible.subList(i,i+4).stream().map(p->p.id).collect(Collectors.toList());String court=clocks.keySet().stream().min(Comparator.comparing(clocks::get)).orElseThrow();LocalTime start=clocks.get(court);for(String id:ids)if(available.containsKey(id)&&available.get(id).isAfter(start))start=available.get(id);LocalTime end=start.plusMinutes(duration);if(end.isAfter(ends.get(court)))throw new IllegalArgumentException("The complete balanced extra-match set does not fit the remaining court schedule.");
            MatchRecord m=new MatchRecord(UUID.randomUUID().toString(),++n,n, court,start.format(TIME),end.format(TIME),ids.get(0),ids.get(1),ids.get(2),ids.get(3),true);added.add(m);clocks.put(court,end);for(String id:ids){available.put(id,end);counts.merge(id,1,Integer::sum);}}
        if(added.isEmpty())throw new IllegalArgumentException("No balanced extra match fits the available court time and player availability.");matches.saveAll(added);return matches();
    }
    private String[] ids(MatchRecord m){return new String[]{m.team1Player1,m.team1Player2,m.team2Player1,m.team2Player2};}

    public synchronized List<Map<String,Object>> leaderboard(){
        Map<String,Map<String,Object>> table=new HashMap<>();
        for(Player p:players()){
            Map<String,Object> row=new LinkedHashMap<>(); row.put("playerId",p.id); row.put("name",p.name);
            row.put("matches",0); row.put("wins",0); row.put("losses",0); row.put("pointsFor",0); row.put("pointsAgainst",0); table.put(p.id,row);
        }
        for(MatchRecord m:matches()){
            if(!m.completed || m.score1==null || m.score2==null) continue;
            boolean team1Won=m.score1>m.score2;
            updateStats(table.get(m.team1Player1),m.score1,m.score2,team1Won);
            updateStats(table.get(m.team1Player2),m.score1,m.score2,team1Won);
            updateStats(table.get(m.team2Player1),m.score2,m.score1,!team1Won);
            updateStats(table.get(m.team2Player2),m.score2,m.score1,!team1Won);
        }
        return table.values().stream()
            .sorted(Comparator.<Map<String,Object>>comparingInt(r->(Integer)r.get("wins")).reversed()
                .thenComparing(Comparator.comparingInt((Map<String,Object> r)->(Integer)r.get("pointsFor")-(Integer)r.get("pointsAgainst")).reversed())
                .thenComparing(r->(String)r.get("name")))
            .collect(Collectors.toList());
    }
    private void updateStats(Map<String,Object> row,int pointsFor,int pointsAgainst,boolean won){
        if(row==null)return;
        row.put("matches",(Integer)row.get("matches")+1);
        row.put("wins",(Integer)row.get("wins")+(won?1:0));
        row.put("losses",(Integer)row.get("losses")+(won?0:1));
        row.put("pointsFor",(Integer)row.get("pointsFor")+pointsFor);
        row.put("pointsAgainst",(Integer)row.get("pointsAgainst")+pointsAgainst);
    }

    public synchronized String whatsappText(){
        Map<String,String> names=new HashMap<>();players().forEach(p->names.put(p.id,p.name));
        Map<String,Object> c=config();StringBuilder out=new StringBuilder();
        out.append("🏸 *").append(Objects.toString(c.get("tournamentName"))).append("*\nDate: ").append(Objects.toString(c.get("tournamentDate"))).append("\n\n");
        for(MatchRecord m:matches()){
            out.append("*Match ").append(m.matchNumber).append("* — ").append(Objects.toString(m.court,"Court")).append(" (").append(Objects.toString(m.startTime,"--:--")).append(")\n");
            out.append(Objects.toString(names.get(m.team1Player1),"Player")).append(" & ").append(Objects.toString(names.get(m.team1Player2),"Player"));
            out.append(" vs ").append(Objects.toString(names.get(m.team2Player1),"Player")).append(" & ").append(Objects.toString(names.get(m.team2Player2),"Player")).append("\n");
            out.append(m.completed?"Score: "+m.score1+"–"+m.score2:"Pending").append("\n\n");
        }
        return out.toString();
    }

    @Transactional public synchronized void reset(){
        List<Player> roster=players();List<MatchRecord> oldMatches=matches();
        if(!roster.isEmpty()||!oldMatches.isEmpty()){
            TournamentHistory h=new TournamentHistory();h.id=UUID.randomUUID().toString();Map<String,Object> c=config();h.tournamentName=Objects.toString(c.get("tournamentName"));h.tournamentDate=Objects.toString(c.get("tournamentDate"));h.archivedAtEpochMs=System.currentTimeMillis();h.playerCount=roster.size();h.matchCount=oldMatches.size();h.completedMatchCount=(int)oldMatches.stream().filter(m->m.completed).count();
            try{h.rosterJson=mapper.writeValueAsString(roster);h.matchesJson=mapper.writeValueAsString(oldMatches);h.statisticsJson=mapper.writeValueAsString(leaderboard());}catch(Exception e){throw new IllegalStateException("Could not archive tournament history.",e);}history.save(h);
        }
        matches.deleteAll();players.deleteAll();
    }
    public List<TournamentHistory> history(){return history.findAll().stream().sorted(Comparator.comparingLong((TournamentHistory h)->h.archivedAtEpochMs).reversed()).collect(Collectors.toList());}
    public Map<String,Object> statistics(){List<MatchRecord> m=matches();return Map.of("players",players().size(),"matches",m.size(),"completedMatches",m.stream().filter(x->x.completed).count(),"remainingMatches",m.stream().filter(x->!x.completed).count(),"leaderboard",leaderboard());}
    public synchronized Map<String,Object> export(){return Map.of("config",config(),"players",players(),"matches",matches(),"statistics",statistics(),"history",history());}
}
