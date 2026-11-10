'use strict';

// webdemo 几何与 Android KbdLayout fixture 对拍。
const fs = require('fs');
const path = require('path');
const L = require('../webdemo/js/layout.js');

const fixture = JSON.parse(fs.readFileSync(
  path.join(__dirname, 'fixtures', 'geometry.json'), 'utf8'));

let passed = 0;
let failed = 0;

function eq(label, actual, expected) {
  if (actual === expected) {
    passed++;
  } else {
    failed++;
    console.log('FAIL  ' + label + '  actual=' + actual + ' expected=' + expected);
  }
}

function close(label, actual, expected) {
  if (Math.abs(actual - expected) < 1e-5) {
    passed++;
  } else {
    failed++;
    console.log('FAIL  ' + label + '  actual=' + actual + ' expected=' + expected);
  }
}

for (const c of fixture.cases) {
  const tag = c.W + 'x' + c.H;
  const m = L.compute({ W: c.W, H: c.H, b: c.b });
  eq(tag + ' mode', m.orientation, c.mode);
  eq(tag + ' n', m.n, c.n);
  eq(tag + ' S', m.S, c.S);
  eq(tag + ' K', m.K, c.K);
  eq(tag + ' g', m.g, c.g);
  eq(tag + ' c', m.c, c.c);
  eq(tag + ' m', m.m, c.m);
  eq(tag + ' keyH', m.keyH, c.keyH);
  eq(tag + ' kbW', m.kbW, c.kbW);
  eq(tag + ' kbH', m.kbH, c.kbH);
  eq(tag + ' preeditX', m.pinyinX + m.kbLeft, c.preeditX);
  eq(tag + ' preeditW', m.pinyinW, c.preeditW);
  eq(tag + ' symbolCols', m.symbolCols, c.symbolCols);
  eq(tag + ' rowKeys', m.orientation === 'portrait' ? 10 : m.p, c.rowKeys);
  eq(tag + ' compact', m.compactLabels, c.compact);
  eq(tag + ' overflow', m.overflow, c.overflow);
  eq(tag + ' tBeyond', m.tBeyond, c.tBeyond);
  eq(tag + ' gCapped', m.gCappedByHeight, c.gCapped);
  close(tag + ' kbRatio', m.kbRatio, c.kbRatio);

  const rowKeys = c.rowKeys;
  for (const sample of c.candidates) {
    eq(tag + ' candKeys ' + sample.text,
      L.candKeys(sample.text, rowKeys, m), sample.keys);
    eq(tag + ' candLines ' + sample.text,
      L.candLines(sample.text, rowKeys, m).length, sample.lines);
  }

  const pack = ['中华人民共和国', '你好', '拟好', '你'];
  const rows = L.candidateRows(pack, rowKeys, m);
  eq(tag + ' pack rows', rows.length, c.pack.length);
  for (let i = 0; i < Math.min(rows.length, c.pack.length); i++) {
    eq(tag + ' pack ' + i + ' keys', rows[i].keys, c.pack[i].keys);
    eq(tag + ' pack ' + i + ' slots', rows[i].slots, c.pack[i].slots);
    eq(tag + ' pack ' + i + ' items',
      rows[i].items.map(x => x.keys).join(','), c.pack[i].items.join(','));
  }
}

if (failed) {
  console.log('webdemo geometry: passed=' + passed + ' failed=' + failed);
  process.exit(1);
}
console.log('webdemo geometry: passed=' + passed + ' failed=0');
