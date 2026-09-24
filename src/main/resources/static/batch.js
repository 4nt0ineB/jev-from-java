// Streams one JSON row per finished message from POST /batch and recomputes every view from the stored rows,
// so moving the threshold never calls Jev again.
(() => {
  const main = document.querySelector('main');
  const ACT = Number(main.dataset.act);
  const UNSURE_FROM = Number(main.dataset.unsureFrom);
  const $ = id => document.getElementById(id);
  const SVG = 'http://www.w3.org/2000/svg';

  let rows = [], total = 0, started = 0, running = false, frame = 0;

  const el = (tag, cls, text) => {
    const e = document.createElement(tag);
    if (cls) e.className = cls;
    if (text !== undefined) e.textContent = text;
    return e;
  };
  const svg = (tag, attrs) => {
    const e = document.createElementNS(SVG, tag);
    Object.entries(attrs).forEach(([k, v]) => e.setAttribute(k, v));
    return e;
  };
  const pct = x => Math.round(x * 100) + '%';
  const answered = () => rows.filter(r => !r.error);
  const hasGold = () => rows.some(r => r.gold);
  // Mirrors Assistant.outcome: understood (intelligible not a clear no) and confident enough.
  const automated = (r, t) => r.intelligible >= UNSURE_FROM && r.confidence >= t;
  const threshold = () => Number($('threshold').value);

  function mark(r, t) {
    if (r.error) return el('span', 'mark ko', '⚠ error');
    if (!automated(r, t)) return el('span', 'mark review', '⧗ review');
    if (!r.gold) return el('span', 'mark ok', '→ auto');
    return r.intent === r.gold ? el('span', 'mark ok', '✓ correct') : el('span', 'mark ko', '✗ wrong');
  }

  async function start(body, url) {
    if (running) return;
    running = true;
    rows = []; total = 0; started = performance.now();
    $('error').hidden = true;
    $('sample').disabled = true;
    schedule();
    try {
      const res = await fetch(url, { method: 'POST', body, headers: { 'Content-Type': 'text/csv; charset=utf-8' } });
      if (!res.ok) throw new Error(await res.text());
      total = Number(res.headers.get('X-Rows'));
      const reader = res.body.pipeThrough(new TextDecoderStream()).getReader();
      let buffer = '';
      for (;;) {
        const { value, done } = await reader.read();
        if (done) break;
        buffer += value;
        let nl;
        while ((nl = buffer.indexOf('\n')) >= 0) {
          const line = buffer.slice(0, nl);
          buffer = buffer.slice(nl + 1);
          if (line) { rows.push(JSON.parse(line)); schedule(); }
        }
      }
    } catch (e) {
      $('error').textContent = e.message;
      $('error').hidden = false;
    } finally {
      running = false;
      $('sample').disabled = false;
      schedule();
    }
  }

  function schedule() {
    if (!frame) frame = requestAnimationFrame(() => { frame = 0; render(); });
  }

  function render() {
    const t = threshold();
    $('thresholdOut').textContent = t.toFixed(2);
    renderHeader();
    renderCurrent(t);
    hasGold() ? renderCalibration(t) : renderProduction(t);
  }

  function renderHeader() {
    const spend = rows.reduce((s, r) => s + r.cost, 0);
    const ms = answered().map(r => r.millis).sort((a, b) => a - b);
    const seconds = (performance.now() - started) / 1000;
    $('spend').textContent = '$' + spend.toFixed(6);
    $('p50').textContent = ms.length ? ms[Math.floor(ms.length / 2)] + ' ms' : '–';
    $('progress').textContent = total
      ? `${rows.length}/${total} rows · ${(rows.length / Math.max(seconds, 0.001)).toFixed(1)} rows/s${running ? '' : ' · done'}`
      : (running ? 'starting…' : 'no run yet');
  }

  function renderCurrent(t) {
    const r = rows[rows.length - 1];
    if (!r) return;
    $('curText').textContent = r.text;
    $('curIntent').textContent = r.error ? '' : r.intent;
    $('curConf').textContent = r.error ? '' : 'confidence ' + pct(r.confidence);
    $('curMark').replaceChildren(mark(r, t));
    $('curGold').textContent = r.gold ? 'gold: ' + r.gold : '';
    $('curHeadline').textContent = r.error || r.headline;
    $('curCommand').textContent = r.command || '';
    $('ticker').replaceChildren(...rows.slice(-6, -1).reverse().map(p => {
      const li = el('li');
      li.append(el('span', '', mark(p, t).textContent[0]), el('span', '', p.text), el('span', '', p.error ? 'error' : p.intent),
        el('span', 'pct', p.error ? '' : pct(p.confidence)));
      return li;
    }));
  }

  function tiles(items) {
    $('tiles').replaceChildren(...items.map(([value, label]) => {
      const tile = el('div', 'tile');
      tile.append(el('b', '', value), el('span', '', label));
      return tile;
    }));
  }

  function renderCalibration(t) {
    const done = answered();
    const auto = done.filter(r => automated(r, t));
    const correct = rs => rs.filter(r => r.intent === r.gold).length;
    $('statsTitle').textContent = 'Calibration · gold labels present';
    tiles([
      [done.length ? pct(correct(done) / done.length) : '–', 'accuracy, all rows'],
      [done.length ? pct(auto.length / done.length) : '–', 'handled automatically'],
      [auto.length ? pct(correct(auto) / auto.length) : '–', 'accuracy when automatic'],
      [String(rows.length - done.length), 'failed calls'],
    ]);

    $('chartTitle').textContent = 'Accuracy vs share automated';
    const points = [];
    for (let i = 0; i <= 100; i++) {
      const cut = i / 100, kept = done.filter(r => automated(r, cut));
      if (kept.length) points.push({ cut, x: kept.length / done.length, y: correct(kept) / kept.length });
    }
    curve(points, t);

    $('listTitle').textContent = 'Top confusions (gold → predicted)';
    const pairs = {};
    done.filter(r => r.intent !== r.gold).forEach(r => {
      const key = r.gold + ' → ' + r.intent;
      pairs[key] = (pairs[key] || 0) + 1;
    });
    list(Object.entries(pairs).sort((a, b) => b[1] - a[1]).slice(0, 8), 'No confusion yet');
  }

  function renderProduction(t) {
    const done = answered();
    const auto = done.filter(r => automated(r, t));
    const review = done.filter(r => !automated(r, t));
    $('statsTitle').textContent = 'Production view · no gold labels';
    tiles([
      [done.length ? pct(auto.length / done.length) : '–', 'handled automatically'],
      [String(review.length), 'sent to a person'],
      [String(done.filter(r => r.intelligible < UNSURE_FROM).length), 'of which not understood'],
      [String(rows.length - done.length), 'failed calls'],
    ]);
    $('chartTitle').textContent = 'Intent confidence, all rows';
    histogram(done.map(r => r.confidence), t);
    $('listTitle').textContent = 'Latest sent to review';
    list(review.slice(-8).reverse().map(r => [r.text, r.intent + ' ' + pct(r.confidence)]), 'Nothing to review');
  }

  function list(entries, empty) {
    $('list').replaceChildren(...(entries.length ? entries : [[empty, '']]).map(([label, n]) => {
      const li = el('li');
      li.append(el('span', '', label), el('span', 'n', String(n)));
      return li;
    }));
  }

  // Drawn at the container's pixel width so text stays at its CSS size instead of scaling with the column.
  const H = 190, M = { top: 8, right: 12, bottom: 36, left: 36 };
  let W = 360, PW = 0;
  const PH = H - M.top - M.bottom;

  function frameSvg(yMin, yLabel, xLabel) {
    W = Math.max(240, $('chart').clientWidth);
    PW = W - M.left - M.right;
    const root = svg('svg', { viewBox: `0 0 ${W} ${H}`, role: 'img' });
    const grid = svg('g', { class: 'grid' }), axis = svg('g', { class: 'axis' });
    for (let i = 0; i <= 4; i++) {
      const v = yMin + (1 - yMin) * i / 4, y = M.top + PH - PH * i / 4;
      grid.append(svg('line', { x1: M.left, x2: W - M.right, y1: y, y2: y }));
      const label = svg('text', { x: M.left - 6, y: y + 4, 'text-anchor': 'end' });
      label.textContent = yLabel(v);
      axis.append(label);
    }
    for (let i = 0; i <= 4; i++) {
      const tick = svg('text', { x: M.left + PW * i / 4, y: M.top + PH + 14, 'text-anchor': 'middle' });
      tick.textContent = pct(i / 4);
      axis.append(tick);
    }
    const x = svg('text', { x: M.left + PW / 2, y: H - 2, 'text-anchor': 'middle' });
    x.textContent = xLabel;
    axis.append(x);
    root.append(grid, axis);
    return root;
  }

  function mount(root) {
    const tip = $('tip');
    $('chart').replaceChildren(root, tip);
  }

  function showTip(evt, text) {
    const box = $('chart').getBoundingClientRect(), tip = $('tip');
    tip.textContent = text;
    tip.style.left = (evt.clientX - box.left) + 'px';
    tip.style.top = (evt.clientY - box.top) + 'px';
    tip.style.display = 'block';
  }

  function curve(points, t) {
    const yMin = Math.min(0.5, Math.floor(Math.min(...points.map(p => p.y), 1) * 10) / 10);
    const px = x => M.left + x * PW, py = y => M.top + PH - (y - yMin) / (1 - yMin) * PH;
    const root = frameSvg(yMin, pct, 'share of rows handled automatically →');
    if (points.length) {
      root.append(svg('path', { class: 'curve', d: points.map((p, i) => (i ? 'L' : 'M') + px(p.x) + ' ' + py(p.y)).join(' ') }));
      const here = points.reduce((best, p) => Math.abs(p.cut - t) < Math.abs(best.cut - t) ? p : best);
      root.append(svg('circle', { class: 'here', cx: px(here.x), cy: py(here.y), r: 5 }));
      const hit = svg('rect', { x: M.left, y: M.top, width: PW, height: PH, fill: 'transparent' });
      hit.addEventListener('mousemove', e => {
        const r = root.getBoundingClientRect(), x = ((e.clientX - r.left) / r.width * W - M.left) / PW;
        const p = points.reduce((best, q) => Math.abs(q.x - x) < Math.abs(best.x - x) ? q : best);
        showTip(e, `from ${p.cut.toFixed(2)}: ${pct(p.x)} automatic, ${pct(p.y)} correct`);
      });
      hit.addEventListener('mouseleave', () => { $('tip').style.display = 'none'; });
      root.append(hit);
    }
    mount(root);
  }

  function histogram(values, t) {
    const bins = new Array(20).fill(0);
    values.forEach(v => bins[Math.min(19, Math.floor(v * 20))]++);
    const top = Math.ceil(Math.max(1, ...bins) / 4) * 4;
    const root = frameSvg(0, v => Math.round(v * top), 'intent confidence →');
    const bw = PW / 20;
    bins.forEach((n, i) => {
      const h = n / top * PH;
      const bar = svg('rect', { class: 'bin' + (i / 20 >= t - 1e-9 ? ' kept' : ''), x: M.left + i * bw + 1, y: M.top + PH - h,
        width: bw - 2, height: Math.max(h, 0), rx: 2 });
      bar.addEventListener('mousemove', e => showTip(e, `${(i / 20).toFixed(2)}–${((i + 1) / 20).toFixed(2)}: ${n} rows`));
      bar.addEventListener('mouseleave', () => { $('tip').style.display = 'none'; });
      root.append(bar);
    });
    const x = M.left + t * PW;
    root.append(svg('line', { class: 'cut', x1: x, x2: x, y1: M.top, y2: M.top + PH }));
    mount(root);
  }

  $('sample').addEventListener('click', () => start(null, '/batch?sample=massive-fr'));
  $('file').addEventListener('change', async e => {
    const file = e.target.files[0];
    if (file) start(await file.text(), '/batch');
    e.target.value = '';
  });
  const drop = $('drop');
  drop.addEventListener('dragover', e => { e.preventDefault(); drop.classList.add('over'); });
  drop.addEventListener('dragleave', () => drop.classList.remove('over'));
  drop.addEventListener('drop', async e => {
    e.preventDefault();
    drop.classList.remove('over');
    const file = e.dataTransfer.files[0];
    if (file) start(await file.text(), '/batch');
  });
  $('threshold').addEventListener('input', schedule);
  window.addEventListener('resize', schedule);
  $('threshold').value = ACT;
  render();
})();
