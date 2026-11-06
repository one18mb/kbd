(function () {
  'use strict';

  // 中文符号映射：对齐 PC 搜狗输入法的“中文标点”方案。
  // 只把常用中文标点转为中文形态，其余保持半角；k/l 上滑保持 {}。
  const zhPunct = {
    '!': '！',
    '@': '@',
    '#': '#',
    '$': '￥',
    '%': '%',
    '^': '……',
    '&': '&',
    '*': '×',
    '(': '（',
    ')': '）',
    '~': '～',
    '`': '`',
    ':': '：',
    ';': '；',
    '"': '“',
    "'": '‘',
    '|': '|',
    '\\': '、',
    '+': '+',
    '=': '=',
    '_': '——',
    '-': '-',
    '{': '{',
    '}': '}',
    '[': '【',
    ']': '】',
    '<': '《',
    '>': '》',
    '?': '？',
    ',': '，',
    '.': '。',
    '/': '/'
  };

  const keys = {};
  function add(id, def) {
    keys[id] = Object.assign({ id: id }, def);
  }
  function z(label) {
    return zhPunct[label] != null ? zhPunct[label] : label;
  }
  function slide(label) {
    return label ? { en: label, zh: z(label) } : null;
  }

  const r1 = [
    ['q', '1', '!'], ['w', '2', '@'], ['e', '3', '#'], ['r', '4', '$'], ['t', '5', '%'],
    ['y', '6', '^'], ['u', '7', '&'], ['i', '8', '*'], ['o', '9', '('], ['p', '0', ')']
  ];
  r1.forEach(function (k) {
    add(k[0], { kind: 'letter', main: { en: k[0], zh: k[0] }, up: slide(k[1]), down: slide(k[2]) });
  });

  add('a', { kind: 'letter', main: { en: 'a', zh: 'a' }, up: { en: 'Tab', zh: 'Tab' }, down: null });
  add('s', { kind: 'letter', main: { en: 's', zh: 's' }, up: slide('~'), down: slide('`') });
  add('d', { kind: 'letter', main: { en: 'd', zh: 'd' }, up: slide(':'), down: slide(';') });
  add('f', { kind: 'letter', main: { en: 'f', zh: 'f' }, up: slide('"'), down: slide("'") });
  add('g', { kind: 'letter', main: { en: 'g', zh: 'g' }, up: slide('|'), down: slide('\\') });
  add('h', { kind: 'letter', main: { en: 'h', zh: 'h' }, up: slide('+'), down: slide('=') });
  add('j', { kind: 'letter', main: { en: 'j', zh: 'j' }, up: slide('_'), down: slide('-') });
  add('k', { kind: 'letter', main: { en: 'k', zh: 'k' }, up: slide('{'), down: slide('[') });
  add('l', { kind: 'letter', main: { en: 'l', zh: 'l' }, up: slide('}'), down: slide(']') });

  add('z', { kind: 'letter', main: { en: 'z', zh: 'z' }, up: slide('Esc'), down: null });
  add('x', { kind: 'letter', main: { en: 'x', zh: 'x' }, up: slide('<'), down: null });
  add('c', { kind: 'letter', main: { en: 'c', zh: 'c' }, up: slide('>'), down: null });
  add('v', { kind: 'letter', main: { en: 'v', zh: 'v' }, up: slide('?'), down: null });
  add('b', { kind: 'letter', main: { en: 'b', zh: 'b' }, up: slide(','), down: null });
  add('n', { kind: 'letter', main: { en: 'n', zh: 'n' }, up: slide('.'), down: null });
  add('m', { kind: 'letter', main: { en: 'm', zh: 'm' }, up: slide('/'), down: null });

  add('arrowUp', { kind: 'nav', dir: 'up', main: { en: '↑', zh: '↑' }, repeat: true });
  add('arrowDown', { kind: 'nav', dir: 'down', main: { en: '↓', zh: '↓' }, repeat: true });
  add('arrowLeft', { kind: 'nav', dir: 'left', main: { en: '←', zh: '←' }, repeat: true });
  add('arrowRight', { kind: 'nav', dir: 'right', main: { en: '→', zh: '→' }, repeat: true });

  add('shift', { kind: 'modifier', mod: 'shift', main: { en: 'Shft', zh: 'Shft' }, long: 'lock' });
  add('ctrl', { kind: 'modifier', mod: 'ctrl', main: { en: 'Ctrl', zh: 'Ctrl' }, long: 'lock' });
  add('alt', { kind: 'modifier', mod: 'alt', main: { en: 'Alt', zh: 'Alt' }, long: 'lock' });
  add('lang', { kind: 'lang', main: { en: '英', zh: '中' }, long: 'symbol' });
  add('space', { kind: 'space', main: { en: '空格', zh: '空格' }, long: 'escape', double: true });
  add('backspace', { kind: 'backspace', main: { en: '删除', zh: '删除' }, long: 'repeat', repeat: true, double: true });
  add('enter', { kind: 'enter', main: { en: '确认', zh: '确认' }, long: 'hide', double: true });

  const portraitRows = [
    ['q', 'w', 'e', 'r', 't', 'y', 'u', 'i', 'o', 'p'],
    ['arrowUp', 'a', 's', 'd', 'f', 'g', 'h', 'j', 'k', 'l'],
    ['arrowDown', 'z', 'x', 'c', 'v', 'b', 'n', 'm', 'arrowLeft', 'arrowRight'],
    ['shift', 'ctrl', 'alt', 'lang', 'space', 'backspace', 'enter']
  ];

  const outerLeft = ['alt', 'ctrl', 'shift'];
  const outerRight = ['backspace', 'lang', 'enter'];
  const landLeft = [
    ['q', 'w', 'e', 'r', 't'],
    ['arrowUp', 'a', 's', 'd', 'f', 'g'],
    ['arrowDown', 'z', 'x', 'c', 'v']
  ];
  const landRight = [
    ['y', 'u', 'i', 'o', 'p'],
    ['h', 'j', 'k', 'l'],
    ['b', 'n', 'm', 'arrowLeft', 'arrowRight']
  ];

  // Mock 拼音词库：只用于原型，不作为最终词库。
  const pinyin = [
    { p: 'ni', pre: 'ni', c: ['你', '尼', '拟', '妮', '泥', '倪', '逆', '匿'] },
    { p: 'hao', pre: 'hao', c: ['好', '号', '浩', '豪', '郝', '耗', '皓', '嚎'] },
    { p: 'nihao', pre: "ni'hao", c: ['你好', '尼好', '拟好', '你号', '妮豪', '你浩', '泥好', '妮好'] },
    { p: 'wo', pre: 'wo', c: ['我', '窝', '握', '卧', '沃', '蜗', '斡'] },
    { p: 'shi', pre: 'shi', c: ['是', '时', '事', '十', '使', '实', '市', '式'] },
    { p: 'de', pre: 'de', c: ['的', '得', '地', '德', '底', '滴', '弟', '第'] },
    { p: 'zhong', pre: 'zhong', c: ['中', '种', '钟', '忠', '终', '众', '重', '肿'] },
    { p: 'guo', pre: 'guo', c: ['国', '过', '果', '郭', '锅', '帼', '裹'] },
    { p: 'zhongguo', pre: "zhong'guo", c: ['中国', '种过', '中过', '重国', '钟国', '中国'] },
    { p: 'shijie', pre: "shi'jie", c: ['世界', '时节', '视界', '师姐', '十界'] },
    { p: 'xiexie', pre: "xie'xie", c: ['谢谢', '歇歇', '些些', '写写', '泄泄'] },
    { p: 'zaijian', pre: "zai'jian", c: ['再见', '在建', '在见', '载肩', '再建'] },
    { p: 'mingtian', pre: "ming'tian", c: ['明天', '命天', '鸣天', '名天'] },
    { p: 'jintian', pre: "jin'tian", c: ['今天', '金天', '尽天', '近天'] },
    { p: 'zuotian', pre: "zuo'tian", c: ['昨天', '作天', '左天', '坐天'] },
    { p: 'shouji', pre: "shou'ji", c: ['手机', '手记', '守己', '首季'] },
    { p: 'shurufa', pre: "shu'ru'fa", c: ['输入法', '数如法', '书入法'] },
    { p: 'pengyou', pre: "peng'you", c: ['朋友', '鹏友', '朋有', '捧友'] },
    { p: 'laoshi', pre: "lao'shi", c: ['老师', '老实', '老式', '牢实'] },
    { p: 'xuexi', pre: "xue'xi", c: ['学习', '学系', '薛西', '雪洗'] },
    { p: 'gongzuo', pre: "gong'zuo", c: ['工作', '公做', '工做', '功作'] },
    { p: 'gongxi', pre: "gong'xi", c: ['恭喜', '公喜', '工细', '供吸'] },
    { p: 'shengri', pre: "sheng'ri", c: ['生日', '升起', '省日', '声日'] },
    { p: 'kuaile', pre: "kuai'le", c: ['快乐', '快了', '块了', '筷乐'] },
    { p: 'yinyue', pre: "yin'yue", c: ['音乐', '因月', '音月', '印月'] },
    { p: 'dianying', pre: "dian'ying", c: ['电影', '点影', '电硬', '殿影'] },
    { p: 'dianhua', pre: "dian'hua", c: ['电话', '点话', '电花', '殿华'] },
    { p: 'wenti', pre: "wen'ti", c: ['问题', '文体', '温体', '闻题'] },
    { p: 'weixin', pre: "wei'xin", c: ['微信', '维新', '唯心', '未信'] },
    { p: 'zhongwen', pre: "zhong'wen", c: ['中文', '种文', '钟文', '重文'] },
    { p: 'beijing', pre: "bei'jing", c: ['北京', '背景', '北经', '杯井'] },
    { p: 'shanghai', pre: "shang'hai", c: ['上海', '商海', '伤海', '尚海'] },
    { p: 'guangzhou', pre: "guang'zhou", c: ['广州', '光州', '广周', '逛州'] },
    { p: 'shenzhen', pre: "shen'zhen", c: ['深圳', '深镇', '神针', '审真'] },
    { p: 'diannao', pre: "dian'nao", c: ['电脑', '点脑', '电闹', '殿脑'] },
    { p: 'chengxu', pre: "cheng'xu", c: ['程序', '成续', '城需', '乘虚'] },
    { p: 'kaifa', pre: "kai'fa", c: ['开发', '开法', '凯发', '开罚'] }
  ];

  // 扁平符号表：原型用常用集合，最终按 PC 搜狗“符号大全”全量导入。
  // 页面不按行分组，按当前方向的槽位数自动换行填充。
  const symbols = [
    '，', '。', '、', '；', '：', '？', '！', '“', '”', '‘', '’', '（', '）', '【', '】', '《', '》', '〈', '〉',
    '…', '—', '～', '·', '・', '「', '」', '『', '』', '〔', '〕', '〖', '〗', '〘', '〙', '〚', '〛',
    '＋', '－', '×', '÷', '＝', '≠', '≈', '≤', '≥', '±', '∓', '∑', '∏', '∫', '∮', '√', '∛', '∜',
    '∞', '∝', '∠', '⊥', '∥', '△', '▲', '▽', '▼', '○', '●', '□', '■', '◇', '◆', '※', '§', '¶', '†', '‡',
    '←', '→', '↑', '↓', '↔', '↕', '⇐', '⇒', '⇑', '⇓', '⇔', '↖', '↗', '↘', '↙', '↩', '↪', '⇧', '⇩', '⟵', '⟶', '⟷',
    '￥', '$', '€', '£', '¥', '₩', '₽', '₹', '₿', '℃', '℉', '°', '′', '″', '‰', '‱', '№', '℅', '℗', '™', '©', '®',
    '①', '②', '③', '④', '⑤', '⑥', '⑦', '⑧', '⑨', '⑩', '⑪', '⑫', '⑬', '⑭', '⑮', '⑯', '⑰', '⑱', '⑲', '⑳',
    'Ⅰ', 'Ⅱ', 'Ⅲ', 'Ⅳ', 'Ⅴ', 'Ⅵ', 'Ⅶ', 'Ⅷ', 'Ⅸ', 'Ⅹ', 'Ⅺ', 'Ⅻ', 'ⅰ', 'ⅱ', 'ⅲ', 'ⅳ', 'ⅴ', 'ⅵ', 'ⅶ', 'ⅷ', 'ⅸ', 'ⅹ',
    'α', 'β', 'γ', 'δ', 'ε', 'ζ', 'η', 'θ', 'ι', 'κ', 'λ', 'μ', 'ν', 'ξ', 'ο', 'π', 'ρ', 'σ', 'τ', 'υ',
    'φ', 'χ', 'ψ', 'ω', 'Α', 'Β', 'Γ', 'Δ', 'Ε', 'Ζ', 'Η', 'Θ', 'Ι', 'Κ', 'Λ', 'Μ', 'Ν', 'Ξ', 'Ο', 'Π',
    'Ρ', 'Σ', 'Τ', 'Υ', 'Φ', 'Χ', 'Ψ', 'Ω',
    '⁰', '¹', '²', '³', '⁴', '⁵', '⁶', '⁷', '⁸', '⁹', '⁺', '⁻', '⁼', '⁽', '⁾', 'ⁿ', 'ⁱ',
    '₀', '₁', '₂', '₃', '₄', '₅', '₆', '₇', '₈', '₉', '₊', '₋', '₌', '₍', '₎',
    '½', '⅓', '⅔', '¼', '¾', '⅛', '⅜', '⅝', '⅞', '⅟', '％', '＠', '＃', '＆', '＊', '＋', '－', '＝', '／', '＼',
    '─', '━', '│', '┃', '┌', '┐', '└', '┘', '├', '┤', '┬', '┴', '┼', '╭', '╮', '╯', '╰',
    '╔', '╗', '╚', '╝', '╠', '╣', '╦', '╩', '╬', '═', '║', '╒', '╓', '╕', '╖', '╘', '╙', '╛', '╜',
    '★', '☆', '☀', '☁', '☂', '☃', '☎', '☏', '☑', '☒', '☓', '☕', '☘', '☠', '☢', '☣', '☮', '☯', '☸', '☹', '☺', '☻', '☼',
    '♠', '♥', '♦', '♣', '♤', '♡', '♢', '♧', '♪', '♫', '♬', '♭', '♮', '♯', '♀', '♂', '⚥', '⚧',
    '✓', '✔', '✗', '✘', '✚', '✛', '✜', '✝', '✞', '✟', '✠', '✡', '✢', '✣', '✤', '✥', '✦', '✧', '✨', '✩', '✪', '✫', '✬', '✭', '✮', '✯', '✰',
    '⚠', '⚡', '⚙', '⚖', '⚔', '⚰', '⚱', '⚽', '⚾', '⛳', '⛵', '⛺', '⛽', '✈', '✉', '✂', '✃', '✄', '✆', '✇',
    '♔', '♕', '♖', '♗', '♘', '♙', '♚', '♛', '♜', '♝', '♞', '♟',
    'ⓐ', 'ⓑ', 'ⓒ', 'ⓓ', 'ⓔ', 'ⓕ', 'ⓖ', 'ⓗ', 'ⓘ', 'ⓙ', 'ⓚ', 'ⓛ', 'ⓜ', 'ⓝ', 'ⓞ', 'ⓟ', 'ⓠ', 'ⓡ', 'ⓢ', 'ⓣ', 'ⓤ', 'ⓥ', 'ⓦ', 'ⓧ', 'ⓨ', 'ⓩ',
    'Ⓐ', 'Ⓑ', 'Ⓒ', 'Ⓓ', 'Ⓔ', 'Ⓕ', 'Ⓖ', 'Ⓗ', 'Ⓘ', 'Ⓙ', 'Ⓚ', 'Ⓛ', 'Ⓜ', 'Ⓝ', 'Ⓞ', 'Ⓟ', 'Ⓠ', 'Ⓡ', 'Ⓢ', 'Ⓣ', 'Ⓤ', 'Ⓥ', 'Ⓦ', 'Ⓧ', 'Ⓨ', 'Ⓩ'
  ];

  const escapeCommands = {
    n: { label: '换行', type: 'text', value: '\n' },
    t: { label: '制表', type: 'text', value: '\t' },
    s: { label: '设置', type: 'action', value: 'settings' },
    j: { label: '简繁', type: 'action', value: 'variant' }
  };

  window.KBD = {
    zhPunct: zhPunct,
    keys: keys,
    portraitRows: portraitRows,
    outerLeft: outerLeft,
    outerRight: outerRight,
    landLeft: landLeft,
    landRight: landRight,
    pinyin: pinyin,
    symbols: symbols,
    escapeCommands: escapeCommands
  };
})();
