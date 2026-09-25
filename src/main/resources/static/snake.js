// The browser only paces and draws: POST /snake/step asks the model and applies the move server-side, and the
// position travels back and forth, so the server keeps no game state.
(() => {
  const WIDTH = 15, HEIGHT = 10, FRAME_MS = 150, DEATH_MS = 1200, LOG = 8;
  const players = JSON.parse(document.getElementById('players').textContent);
  const SEMANTICS = ['full', 'simplified', 'formatted', 'grid'];

  async function post(url, body) {
    const res = await fetch(url, { method: 'POST', body: body && JSON.stringify(body), headers: { 'Content-Type': 'application/json' } });
    if (!res.ok) throw new Error(await res.text());
    return res.json();
  }
  const sleep = ms => new Promise(r => setTimeout(r, ms));

  Vue.createApp({
    data: () => ({
      players,
      player: players[0]?.id,
      semantics: SEMANTICS[0],
      semanticsOptions: SEMANTICS,
      directions: ['up', 'down', 'left', 'right'],
      running: false, game: null, turn: null, log: [], error: null, n: 0,
      stats: Object.fromEntries(players.flatMap(p => SEMANTICS.map(sem => [p.id + ':' + sem,
        { label: p.label, semantics: sem, moves: 0, apples: 0, deaths: 0, errors: 0, usd: 0, millis: 0 }]))),
    }),
    computed: {
      cells() {
        const cells = new Array(WIDTH * HEIGHT).fill('');
        const at = c => c.y * WIDTH + c.x;
        cells[at(this.game.apple)] = 'apple';
        this.game.snake.forEach((c, i) => { cells[at(c)] = i ? 'body' : 'head'; });
        return cells;
      },
      key() { return this.player + ':' + this.semantics; },
      // Only the combinations played so far, plus the one selected, so the table stays short.
      rows() { return Object.entries(this.stats).filter(([k, s]) => k === this.key || s.moves + s.errors); },
    },
    async mounted() {
      try { this.game = await post('/snake/new'); } catch (e) { this.error = e.message; }
    },
    methods: {
      pct: p => p === undefined || p === null ? '–' : Math.round(p * 100) + '%',
      usd: x => '$' + Number(x).toFixed(7).replace(/\.?0+$/, ''),
      toggle() {
        this.running = !this.running;
        if (this.running) this.loop();
      },
      async loop() {
        this.error = null;
        while (this.running) {
          const started = performance.now();
          try {
            await this.tick();
          } catch (e) {
            this.error = e.message;
            this.running = false;
          }
          await sleep((this.game.over ? DEATH_MS : FRAME_MS) - (performance.now() - started));
        }
      },
      async tick() {
        if (this.game.over) {
          this.game = await post('/snake/new');
          return;
        }
        const s = this.stats[this.key], label = s.label + ' · ' + s.semantics;
        const turn = await post('/snake/step?' + new URLSearchParams({ player: this.player, semantics: this.semantics }), this.game);
        s.usd += Number(turn.usd);
        s.millis += turn.millis;
        if (turn.error) s.errors++;
        else {
          s.moves++;
          if (turn.game.apples > this.game.apples) s.apples++;
          if (turn.game.over) s.deaths++;
        }
        this.turn = turn;
        this.game = turn.game;
        this.log.unshift({ n: this.n++, label, error: turn.error, millis: turn.millis, confidence: turn.confidence,
          move: turn.move && turn.move.toLowerCase() });
        this.log.length = Math.min(this.log.length, LOG);
      },
    },
  }).mount('#snake');
})();
