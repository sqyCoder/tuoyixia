/*
 * solver.js —— 作业作答页的题型识别与自动填充
 *
 * 适配要点（不同账号、不同课程、不同题型都要能用）：
 *  1. 题目容器：div.questionLi[typeName][data=qid]（老模板可能没有 typeName，用 #answertype{qid} 兜底）
 *  2. 选项不是原生 radio/checkbox，而是 div.answerBg（onclick=addChoice / addMultipleChoice），
 *     真正的选项键在 span.choice{qid}[data] 上，显示顺序会被打乱，必须按 data 匹配，不能按屏幕上的字母。
 *  3. 答案统一落在隐藏域 #answer{qid} 里，填充完回读它做校验。
 *  4. 填空/简答/论述走 UEditor：#answer{qid}（简答）或 #answerEditor{qid}{n}（填空第 n 空）。
 *  5. 带附件上传（insertAttach）、排序题、完形填空、阅读理解等复杂题型一律标记为不支持，不瞎填。
 */
(function () {
  if (window.CRSolver) return;

  var TYPE_NAME = {
    0: '单选题', 1: '多选题', 2: '填空题', 3: '判断题', 4: '简答题', 5: '名词解释',
    6: '论述题', 9: '填空题', 10: '填空题', 13: '排序题', 14: '完形填空',
    15: '阅读理解', 19: '阅读理解', 21: '评价题'
  };

  var SOLVABLE = { '单选题': 1, '多选题': 1, '判断题': 1, '填空题': 1, '简答题': 1, '论述题': 1, '名词解释': 1 };

  function txt(el) {
    if (!el) return '';
    var s = el.innerText || el.textContent || '';
    return s.replace(/\u00a0/g, ' ').replace(/[ \t]+/g, ' ').replace(/\s*\n\s*/g, '\n').trim();
  }

  function val(el) { return el && el.value != null ? String(el.value) : ''; }

  function questions() {
    var list = [].slice.call(document.querySelectorAll('div.questionLi'));
    if (!list.length) {
      list = [].slice.call(document.querySelectorAll('div[id^="question"]')).filter(function (el) {
        return el.getAttribute('data');
      });
    }
    // 学习页内嵌测验（#ZyBottom 里的 .singleQuesId）—— 章节测验常走这套
    if (!list.length) {
      list = [].slice.call(document.querySelectorAll('.singleQuesId'));
    }
    return list;
  }

  function qidOf(q) {
    var d = q.getAttribute('data');
    if (d) return d;
    var m = /(\d{3,})/.exec(q.id || '');
    if (m) return m[1];
    return (q.id || '').replace(/^question/, '');
  }

  function typeOf(q, qid) {
    var name = (q.getAttribute('typeName') || '').trim();
    if (!name) {
      var t = val(document.getElementById('answertype' + qid));
      name = TYPE_NAME[parseInt(t, 10)] || ('类型' + t);
    }
    return name;
  }

  /* 选项：span.choice{qid}[data] + 兄弟节点里的 .answer_p 文本 */
  function optionsOf(q, qid) {
    var out = [];
    var spans = q.querySelectorAll('span[data][class*="choice"]');
    if (!spans.length) spans = q.querySelectorAll('.answerBg span[data]');
    if (!spans.length) spans = q.querySelectorAll('input[type="radio"], input[type="checkbox"]');
    [].slice.call(spans).forEach(function (sp) {
      var isInput = (sp.tagName || '').toUpperCase() === 'INPUT';
      var box = isInput
        ? (sp.closest ? sp.closest('.answerBg, label, li') : sp.parentElement)
        : (sp.closest ? sp.closest('.answerBg') : sp.parentElement);
      var key = isInput ? (sp.getAttribute('value') || '') : (sp.getAttribute('data') || '');
      var label = isInput ? '' : txt(sp);
      var body = box ? box.querySelector('.answer_p') : null;
      var text = body ? txt(body) : (isInput ? txt(box) : '');
      if (!text) {
        text = txt(box).replace(new RegExp('^' + String(label).replace(/[.*+?^${}()|[\]\\]/g, '\\$&')), '').trim();
      }
      var selected = isInput ? !!sp.checked : /check_answer/.test(sp.className || '');
      out.push({ key: key, label: label, text: text, sel: selected, input: isInput });
    });
    return out;
  }

  function blankCount(qid) {
    var n = parseInt(val(document.querySelector('input[name="tiankongsize' + qid + '"]')), 10);
    if (n > 0) return n;
    var eds = document.querySelectorAll('[id^="answerEditor' + qid + '"]');
    return eds.length || 1;
  }

  function hasAttachment(q) {
    return !!q.querySelector('.attach-iframe, iframe[module="insertAttach"], input[type="file"], .editor-iframe');
  }

  function editorsReady(qid, type) { return true; }

  /* ------------------------------------------------------------ scan */
  function scan() {
    var qs = questions();
    var out = [];
    for (var i = 0; i < qs.length; i++) {
      var q = qs[i], qid = qidOf(q);
      if (!qid) continue;
      var typeName = typeOf(q, qid);
      var stemBox = q.querySelector('.mark_name') || q.querySelector('.Zy_TItle') || q;
      var stem = txt(stemBox).replace(/^\d+[.、]\s*/, '').replace('(' + typeName + ')', '').trim();
      var opts = optionsOf(q, qid);
      var item = {
        i: out.length + 1,
        qid: qid,
        typeName: typeName,
        stem: stem.length > 600 ? stem.slice(0, 600) + '…' : stem,
        options: opts,
        blanks: (typeName === '填空题' ? blankCount(qid) : 0),
        solvable: !!SOLVABLE[typeName],
        reason: ''
      };
      if (!item.solvable) item.reason = '题型「' + typeName + '」暂不支持';
      else if (hasAttachment(q)) { item.solvable = false; item.reason = '这道题需要上传附件'; }
      else if ((typeName === '单选题' || typeName === '多选题' || typeName === '判断题') && !opts.length) {
        item.solvable = false; item.reason = '没解析到选项';
      } else if (typeName === '填空题' && !document.querySelector('[id^="answerEditor' + qid + '"]') && !blankCount(qid)) {
        item.solvable = false; item.reason = '没找到填空输入框';
      }
      out.push(item);
    }
    var solvable = out.filter(function (x) { return x.solvable; }).length;
    return {
      ok: out.length > 0,
      total: out.length,
      solvable: solvable,
      questions: out,
      url: location.href
    };
  }

  /* ------------------------------------------------------------ fill */
  function pickBox(qid, key) {
    var sp = document.querySelector('span.choice' + qid + '[data="' + key + '"]');
    if (!sp) sp = document.querySelector('.choice' + qid + '[data="' + key + '"]');
    if (!sp) {
      var root = document.querySelector('.singleQuesId[data="' + qid + '"]') ||
                 document.getElementById('question' + qid);
      if (root) {
        sp = root.querySelector('[data="' + key + '"]') ||
             root.querySelector('input[value="' + key + '"]');
      }
    }
    if (!sp) return null;
    if ((sp.tagName || '').toUpperCase() === 'INPUT') {
      return sp.closest ? (sp.closest('label, .answerBg, li') || sp) : sp;
    }
    return sp.closest ? (sp.closest('.answerBg') || sp.parentElement) : sp.parentElement;
  }

  function isSelected(box) {
    if (!box) return false;
    var input = box.querySelector ? box.querySelector('input[type="radio"], input[type="checkbox"]') : null;
    if (input) return !!input.checked;
    var sp = box.querySelector ? box.querySelector('[class*="check_answer"]') : null;
    return !!sp;
  }

  function clickBox(box) {
    if (!box) return;
    var input = box.querySelector ? box.querySelector('input[type="radio"], input[type="checkbox"]') : null;
    try { (input || box).click(); } catch (e) { /* ignore */ }
  }

  function findKeyOption(opts, want) {
    var letters = 'ABCDEFGHIJKLMNOP';
    var w = String(want || '').toUpperCase().replace(/[^A-Z]/g, '');
    // ① 先按 data 精确匹配（选项键就是页面内部的字母）
    var byKey = opts.filter(function (o) { return o.key && o.key.toUpperCase() === w; });
    if (byKey.length) return byKey[0].key;
    // ② 按显示字母（有些模板 data 与显示一致）
    var byLabel = opts.filter(function (o) { return o.label && o.label.toUpperCase() === w; });
    if (byLabel.length) return byLabel[0].key;
    // ③ 单字母时按顺序取第 n 个选项
    if (w.length === 1) {
      var idx = letters.indexOf(w);
      if (idx >= 0 && idx < opts.length) return opts[idx].key;
    }
    return '';
  }

  function setEditorText(id, text) {
    try {
      if (window.UE && UE.getEditor) {
        var ed = UE.getEditor(id);
        if (ed && ed.setContent) {
          ed.setContent(String(text));
          if (ed.sync) ed.sync();
          return true;
        }
      }
    } catch (e) { /* UEditor 没初始化好，走 textarea 兜底 */ }
    var ta = document.getElementById(id);
    if (ta) {
      ta.value = String(text);
      try { ta.dispatchEvent(new Event('input', { bubbles: true })); } catch (e) {}
      try { ta.dispatchEvent(new Event('change', { bubbles: true })); } catch (e) {}
      try { if (window.jQuery) window.jQuery(ta).trigger('input').trigger('change'); } catch (e) {}
      return true;
    }
    return false;
  }

  function escapeHtml(s) {
    return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  }

  function judgeKey(opts, answer) {
    var a = String(answer || '').trim();
    var truthy = /^(对|正确|是|√|true|t|yes|y|1)$/i.test(a);
    var falsy = /^(错|错误|否|×|false|f|no|n|0)$/i.test(a);
    if (!truthy && !falsy) return '';
    for (var i = 0; i < opts.length; i++) {
      var o = opts[i];
      var t = (o.text || '') + ' ' + (o.label || '');
      var k = (o.key || '').toLowerCase();
      if (truthy && (/^(对|正确|是|√|true|t)$/i.test(t.trim()) || k === 'true')) return o.key;
      if (falsy && (/^(错|错误|否|×|false|f)$/i.test((o.text || '').trim()) || k === 'false')) return o.key;
    }
    return '';
  }

  function fillOne(item, answer) {
    var qid = item.qid;
    var typeName = item.typeName;
    var a = answer == null ? '' : String(answer).trim();
    if (!a) return { ok: false, why: 'AI 没给答案' };

    if (typeName === '单选题' || typeName === '多选题') {
      var keys = [];
      // ① 只有「纯字母答案」才按字母解析，避免把选项原文里的 A/B 抽出来
      var pure = /^[A-Pa-p](\s*[,，、;；/／]\s*[A-Pa-p])*$/.test(a);
      if (pure) {
        keys = a.toUpperCase().match(/[A-P]/g) || [];
      }
      // ② 否则按选项文本匹配（取最长命中的选项，避免短词误命中）
      if (!keys.length && item.options.length) {
        var best = null, bestLen = 0;
        for (var i = 0; i < item.options.length; i++) {
          var t = item.options[i].text || '';
          if (t && a.indexOf(t) >= 0 && t.length > bestLen) {
            best = item.options[i]; bestLen = t.length;
          }
        }
        if (best) keys = [best.key];
      }
      if (!keys.length) return { ok: false, why: '答案里没有可识别的选项' };
      var done = 0;
      keys.forEach(function (k) {
        var real = findKeyOption(item.options, k);
        if (!real) return;
        var box = pickBox(qid, real);
        if (!box) return;
        if (!isSelected(box)) clickBox(box);
        if (isSelected(box)) done++;
      });
      // 多选题必须全部选中才算成功，否则会「半对提交」
      if (done === keys.length && done > 0) return { ok: true, filled: done };
      return { ok: false, why: '只选中 ' + done + '/' + keys.length + ' 个选项' };
    }

    if (typeName === '判断题') {
      var key = judgeKey(item.options, a);
      if (!key) return { ok: false, why: '答案不是对/错' };
      var b = pickBox(qid, key);
      if (!b) return { ok: false, why: '没找到对/错选项' };
      if (!isSelected(b)) clickBox(b);
      return isSelected(b) ? { ok: true, filled: 1 } : { ok: false, why: '没点中对/错' };
    }

    if (typeName === '填空题') {
      var parts = a.split(/#|｜|\|/).map(function (s) { return s.trim(); }).filter(function (s) { return s.length; });
      if (!parts.length) parts = [a];
      var okCount = 0;
      var n = item.blanks || parts.length;
      for (var j = 1; j <= Math.max(n, parts.length); j++) {
        var content = parts[j - 1] != null ? parts[j - 1] : '';
        if (!content) continue;
        if (setEditorText('answerEditor' + qid + j, escapeHtml(content))) okCount++;
      }
      return okCount ? { ok: true, filled: okCount } : { ok: false, why: '没写进填空框' };
    }

    if (typeName === '简答题' || typeName === '论述题' || typeName === '名词解释') {
      var html = a.split(/\n+/).map(function (p) { return '<p>' + escapeHtml(p) + '</p>'; }).join('');
      var wrote = setEditorText('answer' + qid, html) || setEditorText('answerEditor' + qid, html);
      return wrote ? { ok: true, filled: 1 } : { ok: false, why: '没写进答题框' };
    }

    return { ok: false, why: '不支持的题型' };
  }

  function readBack(qid) {
    var el = document.getElementById('answer' + qid);
    return el ? String(el.value || '') : '';
  }

  function fill(payload) {
    var items = payload && payload.questions ? payload.questions : [];
    var answers = (payload && payload.answers) || [];
    var map = {};
    answers.forEach(function (a) { if (a && a.i != null) map[a.i] = a.answer; });

    var results = [];
    items.forEach(function (item) {
      if (!item.solvable) {
        results.push({ i: item.i, ok: false, why: item.reason || '不支持' });
        return;
      }
      var r;
      try { r = fillOne(item, map[item.i]); } catch (e) { r = { ok: false, why: String(e && e.message || e) }; }
      r.i = item.i;
      r.qid = item.qid;
      r.answer = map[item.i] || '';
      r.state = readBack(item.qid);
      results.push(r);
    });

    return {
      ok: true,
      results: results,
      filled: results.filter(function (x) { return x.ok; }).length
    };
  }

  function states() {
    var qs = questions();
    return qs.map(function (q) {
      var qid = qidOf(q);
      return { qid: qid, state: readBack(qid) };
    });
  }

  function submit() {
    var btn = document.querySelector('[onclick*="submitValidate"]');
    if (!btn) btn = document.querySelector('[onclick*="submitWork"]');
    if (!btn) btn = document.querySelector('#submitBtn, .completeBtn');
    if (!btn) return JSON.stringify({ ok: false, why: '没找到提交按钮' });
    btn.click();

    // 超星的「确定提交」弹层有时 1 秒后才出现，轮询 10 次把它点掉
    var tries = 0;
    var timer = setInterval(function () {
      tries++;
      var sels = ['.layui-layer-btn0', '.popDiv .btn', '#confirmBtn', '.sureBtn',
                  '.ui-dialog-buttonpane button'];
      for (var i = 0; i < sels.length; i++) {
        var c = document.querySelector(sels[i]);
        if (!c) continue;
        try {
          var win = (c.ownerDocument && c.ownerDocument.defaultView) || window;
          var st = win.getComputedStyle(c);
          if (st && st.display !== 'none' && st.visibility !== 'hidden') {
            c.click();
            clearInterval(timer);
            return;
          }
        } catch (e) { /* ignore */ }
      }
      if (tries >= 10) clearInterval(timer);
    }, 300);

    return JSON.stringify({ ok: true, pending: true });
  }

  window.CRSolver = {
    scan: function () { try { return JSON.stringify(scan()); } catch (e) { return JSON.stringify({ ok: false, err: String(e) }); } },
    fill: function (json) {
      try {
        var payload = typeof json === 'string' ? JSON.parse(json) : json;
        return JSON.stringify(fill(payload));
      } catch (e) { return JSON.stringify({ ok: false, err: String(e) }); }
    },
    states: function () { try { return JSON.stringify({ ok: true, list: states() }); } catch (e) { return JSON.stringify({ ok: false, err: String(e) }); } },
    submit: function () { try { return submit(); } catch (e) { return JSON.stringify({ ok: false, err: String(e) }); } }
  };
})();
