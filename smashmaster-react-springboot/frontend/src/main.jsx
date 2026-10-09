import React,{useEffect,useRef,useState} from 'react';
import {createRoot} from 'react-dom/client';
import './style.css';
const API=(import.meta.env.VITE_API_URL||'http://localhost:8080').replace(/\/$/,'');

function App(){
 const [players,setPlayers]=useState([]),[matches,setMatches]=useState([]),[name,setName]=useState(''),[gender,setGender]=useState('Male'),[error,setError]=useState(''),[busy,setBusy]=useState(false);
 const [config,setConfig]=useState({tournamentName:'SmashMaster Tournament',tournamentDate:new Date().toISOString().slice(0,10),matchDurationMinutes:8,courts:[]});
 const [leaderboard,setLeaderboard]=useState([]),[history,setHistory]=useState([]),[tab,setTab]=useState('matches');
 const configDirty=useRef(false);
 function changeConfig(updater){configDirty.current=true;setConfig(updater)}
 async function request(path,options={}){const r=await fetch(API+path,{headers:{'Content-Type':'application/json'},...options});const type=r.headers.get('content-type')||'';const data=type.includes('application/json')?await r.json().catch(()=>({})):await r.text();if(!r.ok)throw Error(data.message||data.error||data||'Request failed');return data}
 async function refresh(){try{const [p,m,c,l,h]=await Promise.all([request('/api/players'),request('/api/matches'),request('/api/config'),request('/api/leaderboard'),request('/api/history')]);setPlayers(p);setMatches(m);if(!configDirty.current)setConfig(c);setLeaderboard(l);setHistory(h)}catch(e){setError('Could not connect to the API/database: '+e.message)}}
 useEffect(()=>{refresh();const timer=setInterval(refresh,5000);return()=>clearInterval(timer)},[]);
 async function act(fn){setError('');setBusy(true);try{await fn();await refresh()}catch(e){setError(e.message)}finally{setBusy(false)}}
 async function addPlayer(e){e.preventDefault();await act(async()=>{await request('/api/players',{method:'POST',body:JSON.stringify({name,gender})});setName('')})}
 async function saveConfig(e){e.preventDefault();await act(async()=>{await request('/api/config',{method:'PUT',body:JSON.stringify(config)});configDirty.current=false})}
 async function generate(){await act(async()=>{await request('/api/matches/generate',{method:'POST',body:'{}'})})}
 async function addExtra(){await act(async()=>{await request('/api/matches/extra',{method:'POST',body:'{}'})})}
 async function saveScore(m){const a=document.getElementById('a-'+m.id)?.value,b=document.getElementById('b-'+m.id)?.value;await act(async()=>{await request('/api/matches/'+m.id+'/score',{method:'POST',body:JSON.stringify({score1:Number(a),score2:Number(b)})})})}
 async function reset(){if(!confirm('Archive this tournament, then reset its current players and matches?'))return;await act(async()=>{await request('/api/tournament',{method:'DELETE'})})}
 function updateCourt(i,key,value){changeConfig(c=>({...c,courts:c.courts.map((x,j)=>j===i?{...x,[key]:value}:x)}))}
 function addCourt(){changeConfig(c=>({...c,courts:[...c.courts,{name:`Court ${c.courts.length+1}`,startTime:'17:00',endTime:'19:00'}]}))}
 function removeCourt(i){changeConfig(c=>({...c,courts:c.courts.filter((_,j)=>j!==i)}))}
 const completed=matches.filter(m=>m.completed).length;
 const playerName=id=>players.find(p=>p.id===id)?.name||'Archived player';
 return <main>
  <header><div className="ball">🏸</div><div><h1>SmashMaster</h1><p>React + Spring Boot · PostgreSQL-backed doubles tournament manager</p></div></header>
  {error&&<div className="error" role="alert">{error}</div>}
  <section><h2>Tournament settings</h2><form onSubmit={saveConfig} className="settingsform">
   <label>Tournament name<input value={config.tournamentName||''} onChange={e=>changeConfig(c=>({...c,tournamentName:e.target.value}))} required/></label>
   <label>Date<input type="date" value={config.tournamentDate||''} onChange={e=>changeConfig(c=>({...c,tournamentDate:e.target.value}))} required/></label>
   <label>Match duration (minutes)<input type="number" min="5" max="120" value={config.matchDurationMinutes||8} onChange={e=>changeConfig(c=>({...c,matchDurationMinutes:Number(e.target.value)}))} required/></label>
   <div className="courts"><div className="sectionhead"><b>Courts and available times</b><button type="button" className="secondary" onClick={addCourt}>Add court</button></div>
    {config.courts.map((court,i)=><div className="courtrow" key={i}><label>Court name<input value={court.name} onChange={e=>updateCourt(i,'name',e.target.value)} required/></label><label>Start<input type="time" value={court.startTime} onChange={e=>updateCourt(i,'startTime',e.target.value)} required/></label><label>End<input type="time" value={court.endTime} onChange={e=>updateCourt(i,'endTime',e.target.value)} required/></label><button type="button" className="danger smallbtn" onClick={()=>removeCourt(i)} disabled={config.courts.length===1}>Remove</button></div>)}
   </div><button disabled={busy}>Save settings</button>
  </form></section>
  <section><h2>Player roster <span>{players.length}</span></h2><form onSubmit={addPlayer}><input value={name} onChange={e=>setName(e.target.value)} placeholder="Player name" required/><select value={gender} onChange={e=>setGender(e.target.value)}><option>Male</option><option>Female</option></select><button disabled={busy}>Add player</button></form><ul>{players.map(p=><li key={p.id}>{p.name}<small>{p.gender}</small></li>)}</ul></section>
  <section><div className="sectionhead"><h2>Match schedule</h2><div className="actions"><button onClick={generate} disabled={busy||players.length<4}>Generate fixtures</button><button className="secondary" onClick={addExtra} disabled={busy||matches.length===0}>Add extra matches</button></div></div>
   <div className="stats"><span>Total <b>{matches.length}</b></span><span>Completed <b>{completed}</b></span><span>Remaining <b>{matches.length-completed}</b></span></div>
   {[...matches].sort((a,b)=>Number(a.completed)-Number(b.completed)||a.matchNumber-b.matchNumber).map(m=><article key={m.id} className={m.completed?'done':''}><div className="matchhead"><b>Match {m.matchNumber} · R{m.round}{m.extraMatch?' · Extra':''}</b><span>{m.court} · {m.startTime||'—'}–{m.endTime||'—'}</span></div><p>{playerName(m.team1Player1)} &amp; {playerName(m.team1Player2)} <strong>vs</strong> {playerName(m.team2Player1)} &amp; {playerName(m.team2Player2)}</p>{m.completed?<div className="score">Final score: {m.score1} – {m.score2}</div>:<div className="scoring"><input id={'a-'+m.id} type="number" min="0" max="11" placeholder="0–11"/><b>–</b><input id={'b-'+m.id} type="number" min="0" max="11" placeholder="0–11"/><button onClick={()=>saveScore(m)} disabled={busy}>Save score</button></div>}</article>)}
   {matches.length===0&&<p className="muted">Add at least four players and configure court times, then generate fixtures.</p>}
  </section>
  <nav className="tabs"><button className={tab==='leaderboard'?'selected':'secondary'} onClick={()=>setTab('leaderboard')}>Leaderboard & stats</button><button className={tab==='history'?'selected':'secondary'} onClick={()=>setTab('history')}>Tournament history</button></nav>
  {tab==='leaderboard'&&<section><h2>Leaderboard</h2><div className="tablewrap"><table><thead><tr><th>Player</th><th>Played</th><th>Won</th><th>Lost</th><th>Pts +</th><th>Pts −</th></tr></thead><tbody>{leaderboard.map(r=><tr key={r.playerId}><td>{r.name}</td><td>{r.matches}</td><td>{r.wins}</td><td>{r.losses}</td><td>{r.pointsFor}</td><td>{r.pointsAgainst}</td></tr>)}</tbody></table></div></section>}
  {tab==='history'&&<section><h2>Archived tournaments</h2>{history.length===0?<p className="muted">No archived tournaments yet. Resetting a tournament saves its roster, matches, and statistics here.</p>:history.map(h=><article key={h.id}><b>{h.tournamentName}</b><p>Date: {h.tournamentDate} · Players: {h.playerCount} · Matches: {h.matchCount} · Completed: {h.completedMatchCount}</p><small>Archived: {new Date(h.archivedAtEpochMs).toLocaleString()}</small></article>)}</section>}
  <section><h2>Export</h2><p className="muted">Download tournament records from the backend.</p><div className="actions"><a className="download" href={API+'/api/export/csv'}>Download CSV (Excel-compatible)</a><a className="download" href={API+'/api/export/json'} download="smashmaster-export.json">Download JSON</a><a className="download" href={API+'/api/export/whatsapp'}>Download WhatsApp text</a></div></section>
  <footer><p>Made by <b>Bhushan T.</b> · <a href="mailto:bhushan2005@gmail.com">bhushan2005@gmail.com</a></p><p>💖 Donate via UPI: <b>bhushan2005@okicici</b></p><button className="danger" onClick={reset} disabled={busy}>Archive & reset tournament</button></footer>
 </main>
}
createRoot(document.getElementById('root')).render(<App/>);
