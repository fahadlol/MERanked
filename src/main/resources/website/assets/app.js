const MODES = ['Crystal','Mace','Sword','UHC','Spear Mace','Axe','SMP','Diamond Pot','Netherite Pot'];
let activeMode = MODES[0];

async function fetchJson(path) {
  const res = await fetch(path);
  return res.json();
}

async function loadStatus() {
  const status = await fetchJson('/api/status');
  document.getElementById('online').textContent = status.online ?? '0';
  document.getElementById('matches').textContent = status.liveMatches ?? '0';
  const season = await fetchJson('/api/season');
  document.getElementById('season').textContent = '#' + season.id;
  const live = await fetchJson('/api/matches/live');
  const container = document.getElementById('live-matches');
  container.innerHTML = '';
  (live || []).forEach(m => {
    const el = document.createElement('div');
    el.className = 'match-card';
    el.innerHTML = `<strong>${m.gamemode}</strong><br/>${m.arena || 'Arena TBD'}<br/><small>${m.state}</small>`;
    container.appendChild(el);
  });
}

async function loadLeaderboard(mode) {
  const data = await fetchJson('/api/leaderboard/' + encodeURIComponent(mode));
  const body = document.getElementById('leaderboard-body');
  body.innerHTML = '';
  (data.entries || []).forEach(e => {
    const tr = document.createElement('tr');
    tr.className = 'clickable';
    tr.innerHTML = `<td>${e.rank}</td><td>${e.username}</td><td>${e.region}</td><td class="tier">${e.tier}</td><td>${Math.round(e.rating)}</td><td>${e.wins}/${e.losses}</td>`;
    tr.onclick = () => openPlayer(e.username);
    body.appendChild(tr);
  });
}

async function openPlayer(name) {
  const ref = await fetchJson('/api/player/name/' + encodeURIComponent(name));
  if (!ref.uuid) return;
  const [profile, matches] = await Promise.all([
    fetchJson('/api/player/' + ref.uuid),
    fetchJson('/api/player/' + ref.uuid + '/matches')
  ]);
  document.getElementById('modal-name').textContent = profile.username || name;
  document.getElementById('modal-region').textContent = profile.region ? 'Region: ' + profile.region : '';
  const profiles = document.getElementById('modal-profiles');
  profiles.innerHTML = '';
  (profile.profiles || []).forEach(p => {
    const el = document.createElement('div');
    el.className = 'match-card';
    el.innerHTML = `<strong>${p.gamemode}</strong><br/><span class="tier">${p.tier}</span> — ${p.rating}<br/><small>${p.wins}W / ${p.losses}L · ${p.confidence}</small>`;
    profiles.appendChild(el);
  });
  const list = document.getElementById('modal-matches');
  list.innerHTML = '';
  (matches.matches || []).forEach(m => {
    const el = document.createElement('div');
    el.className = 'match-row ' + (m.won ? 'win' : 'loss');
    el.innerHTML = `<span>${m.won ? 'WIN' : 'LOSS'}</span> <span>${m.gamemode}</span> <small>${m.matchId}</small>`;
    list.appendChild(el);
  });
  document.getElementById('player-modal').classList.remove('hidden');
}

function initModal() {
  const modal = document.getElementById('player-modal');
  document.getElementById('modal-close').onclick = () => modal.classList.add('hidden');
  modal.onclick = (e) => { if (e.target === modal) modal.classList.add('hidden'); };
}

function initTabs() {
  const tabs = document.getElementById('gamemode-tabs');
  MODES.forEach(mode => {
    const btn = document.createElement('button');
    btn.textContent = mode;
    if (mode === activeMode) btn.classList.add('active');
    btn.onclick = () => {
      activeMode = mode;
      [...tabs.children].forEach(c => c.classList.remove('active'));
      btn.classList.add('active');
      loadLeaderboard(mode);
    };
    tabs.appendChild(btn);
  });
}

initTabs();
initModal();
loadStatus();
loadLeaderboard(activeMode);
setInterval(loadStatus, 10000);
setInterval(() => loadLeaderboard(activeMode), 30000);
