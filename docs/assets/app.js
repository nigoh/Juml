/* Juml documentation site — shared behavior */
(function () {
  // --- scrollspy (sidebar TOC) ---
  var links = Array.prototype.slice.call(document.querySelectorAll('#toc a'));
  if (links.length) {
    var map = {};
    links.forEach(function (a) {
      var id = a.getAttribute('href');
      if (id && id.charAt(0) === '#') {
        var el = document.getElementById(id.slice(1));
        if (el) map[id.slice(1)] = a;
      }
    });
    var ids = Object.keys(map);
    var onScroll = function () {
      var pos = window.scrollY + 140, current = ids[0];
      for (var i = 0; i < ids.length; i++) {
        var el = document.getElementById(ids[i]);
        if (el && el.offsetTop <= pos) current = ids[i];
      }
      links.forEach(function (a) { a.classList.remove('active'); });
      if (map[current]) map[current].classList.add('active');
    };
    window.addEventListener('scroll', onScroll, { passive: true });
    onScroll();
  }

  // --- mobile sidebar toggle ---
  var sb = document.getElementById('sidebar');
  var bd = document.getElementById('backdrop');
  var btn = document.getElementById('menuBtn');
  function toggle(open) {
    if (!sb) return;
    sb.classList.toggle('open', open);
    if (bd) bd.classList.toggle('show', open);
  }
  if (btn) btn.addEventListener('click', function () { toggle(!sb.classList.contains('open')); });
  if (bd) bd.addEventListener('click', function () { toggle(false); });
  document.querySelectorAll('#toc a').forEach(function (a) {
    a.addEventListener('click', function () { if (window.innerWidth <= 880) toggle(false); });
  });

  // --- copy buttons on <pre> ---
  document.querySelectorAll('pre').forEach(function (pre) {
    var b = document.createElement('button');
    b.className = 'copy-btn'; b.type = 'button'; b.textContent = 'copy';
    b.addEventListener('click', function () {
      var code = pre.querySelector('code');
      var text = code ? code.innerText : pre.innerText;
      navigator.clipboard.writeText(text).then(function () {
        b.textContent = 'copied!'; setTimeout(function () { b.textContent = 'copy'; }, 1200);
      });
    });
    pre.appendChild(b);
  });

  // --- generic table filter (any input[data-filter="#tableId"]) ---
  document.querySelectorAll('input[data-filter]').forEach(function (input) {
    var table = document.querySelector(input.getAttribute('data-filter'));
    if (!table) return;
    var countTag = input.getAttribute('data-count') ? document.querySelector(input.getAttribute('data-count')) : null;
    var rows = Array.prototype.slice.call(table.querySelectorAll('tbody tr'));
    function refresh() {
      var q = input.value.trim().toLowerCase(), shown = 0;
      rows.forEach(function (r) {
        var hit = !q || r.textContent.toLowerCase().indexOf(q) !== -1;
        r.classList.toggle('hidden', !hit);
        if (hit) shown++;
      });
      if (countTag) countTag.textContent = shown + ' / ' + rows.length + ' 件';
    }
    input.addEventListener('input', refresh);
    refresh();
  });

  // --- responsive tables: derive per-cell labels from <thead> for mobile cards ---
  document.querySelectorAll('.table-wrap > table').forEach(function (tbl) {
    var ths = tbl.querySelectorAll('thead th');
    if (!ths.length) return;
    var labels = Array.prototype.map.call(ths, function (t) { return t.textContent.trim(); });
    tbl.querySelectorAll('tbody tr').forEach(function (tr) {
      Array.prototype.forEach.call(tr.children, function (td, i) {
        if (labels[i] && !td.hasAttribute('data-label')) td.setAttribute('data-label', labels[i]);
      });
    });
    if (tbl.parentNode) tbl.parentNode.classList.add('cardify');
  });

  // --- tap-to-enlarge for UML diagrams (lightbox) ---
  var figSvgs = document.querySelectorAll('figure.uml-fig .diagram-svg');
  if (figSvgs.length) {
    var lb = document.createElement('div');
    lb.className = 'uml-lightbox';
    lb.innerHTML = '<button class="lb-close" type="button" aria-label="閉じる">×</button><div class="lb-scroll"></div>';
    document.body.appendChild(lb);
    var scroll = lb.querySelector('.lb-scroll');
    function closeLb() { lb.classList.remove('open'); scroll.innerHTML = ''; document.body.style.overflow = ''; }
    lb.addEventListener('click', function (e) {
      if (e.target === lb || e.target === scroll || e.target.classList.contains('lb-close')) closeLb();
    });
    document.addEventListener('keydown', function (e) { if (e.key === 'Escape') closeLb(); });
    figSvgs.forEach(function (svg) {
      svg.addEventListener('click', function () {
        var clone = svg.cloneNode(true);
        var vb = (svg.getAttribute('viewBox') || '0 0 960 600').split(/\s+/);
        var w = parseFloat(vb[2]) || 960;
        // モバイルでは画面幅の約 1.7 倍まで拡大して文字を読めるサイズに。
        // デスクトップは自然幅（横スクロール不要）で表示。
        var target = window.innerWidth <= 880
          ? Math.max(w, Math.round(window.innerWidth * 1.7))
          : w;
        clone.setAttribute('class', 'diagram-svg');
        clone.style.width = target + 'px';
        scroll.innerHTML = '';
        scroll.appendChild(clone);
        lb.classList.add('open');
        document.body.style.overflow = 'hidden';
        scroll.scrollTop = 0; scroll.scrollLeft = 0;
      });
    });
  }

  // --- v2 readability/navigation enhancements (only on <body class="v2">) ---
  if (document.body.classList.contains('v2')) {
    // reading progress bar
    var bar = document.createElement('div');
    bar.className = 'read-progress';
    document.body.appendChild(bar);
    function updateProgress() {
      var h = document.documentElement;
      var max = h.scrollHeight - h.clientHeight;
      bar.style.width = (max > 0 ? (h.scrollTop || document.body.scrollTop) / max * 100 : 0) + '%';
    }
    window.addEventListener('scroll', updateProgress, { passive: true });
    window.addEventListener('resize', updateProgress);
    updateProgress();

    // back-to-top button
    var toTop = document.createElement('button');
    toTop.className = 'to-top'; toTop.type = 'button';
    toTop.setAttribute('aria-label', 'トップへ戻る'); toTop.textContent = '↑';
    toTop.addEventListener('click', function () { window.scrollTo({ top: 0, behavior: 'smooth' }); });
    document.body.appendChild(toTop);
    function toggleToTop() { toTop.classList.toggle('show', (window.scrollY || 0) > 600); }
    window.addEventListener('scroll', toggleToTop, { passive: true });
    toggleToTop();

    // heading permalink anchors (link h2 to its section id, and h3[id] to itself)
    document.querySelectorAll('main.content section[id] > h2').forEach(function (h) {
      addAnchor(h, h.parentNode.id);
    });
    document.querySelectorAll('main.content h3[id]').forEach(function (h) {
      addAnchor(h, h.id);
    });
    function addAnchor(h, id) {
      if (!id || h.querySelector('.anchor')) return;
      var a = document.createElement('a');
      a.className = 'anchor'; a.href = '#' + id; a.textContent = '#';
      a.setAttribute('aria-label', 'このセクションへのリンク');
      h.appendChild(a);
    }

    // category colours: assign a hue per TOC group to its sections + sidebar
    var CAT = ['var(--accent)', 'var(--accent-2)', 'var(--accent-3)', 'var(--warn)'];
    var toc = document.getElementById('toc');
    if (toc) {
      var gi = -1, secCat = {};
      Array.prototype.forEach.call(toc.children, function (el) {
        if (el.classList && el.classList.contains('group')) {
          gi++; el.style.setProperty('--cat', CAT[gi % CAT.length]);
        } else if (el.tagName === 'A') {
          var c = CAT[(gi < 0 ? 0 : gi) % CAT.length];
          el.style.setProperty('--cat', c);
          var href = el.getAttribute('href') || '';
          if (href.charAt(0) === '#') secCat[href.slice(1)] = c;
        }
      });
      Object.keys(secCat).forEach(function (id) {
        var sec = document.getElementById(id);
        if (sec && sec.tagName === 'SECTION') sec.style.setProperty('--cat', secCat[id]);
      });
    }

    // code blocks: tag a language label for the decorative header
    document.querySelectorAll('main.content pre').forEach(function (pre) {
      var t = pre.textContent || '';
      var lang = '';
      if (/@startuml|@enduml/.test(t)) lang = 'puml';
      else if (/(^|\n)\s*(java -jar|\.\/gradlew|sudo |apt-get|cd |git )/.test(t)) lang = 'bash';
      else if (/\b(class |interface |void |public |private |import )/.test(t)) lang = 'java';
      if (lang) pre.setAttribute('data-lang', lang);
    });

    // 2-column tables -> definition list (lighter, more scannable)
    document.querySelectorAll('main.content .table-wrap > table').forEach(function (tbl) {
      if (tbl.querySelectorAll('thead th').length !== 2) return;
      var wrap = tbl.parentNode;
      var dl = document.createElement('dl');
      dl.className = 'deflist';
      tbl.querySelectorAll('tbody tr').forEach(function (tr) {
        if (tr.children.length < 2) return;
        var row = document.createElement('div'); row.className = 'row';
        var dt = document.createElement('dt'); dt.innerHTML = tr.children[0].innerHTML;
        var dd = document.createElement('dd'); dd.innerHTML = tr.children[1].innerHTML;
        row.appendChild(dt); row.appendChild(dd); dl.appendChild(row);
      });
      if (dl.children.length && wrap.parentNode) wrap.parentNode.replaceChild(dl, wrap);
    });
  }

  // --- collapse long inline diagrams into <details> (pages flagged .figcollapse) ---
  if (document.body.classList.contains('figcollapse')) {
    document.querySelectorAll('figure.uml-fig').forEach(function (fig) {
      var cap = fig.querySelector('figcaption');
      var badge = '図', title = '図を表示';
      if (cap) {
        var b = cap.querySelector('b');
        if (b) badge = b.textContent.trim();
        var rest = cap.textContent.replace(/^\s*図\d+\s*/, '').split(/[。.]/)[0].trim();
        if (rest) title = rest;
      }
      var det = document.createElement('details');
      det.className = 'fig';
      var sum = document.createElement('summary');
      sum.innerHTML = '<span class="fig-badge"></span><span class="fig-title"></span>';
      sum.querySelector('.fig-badge').textContent = badge;
      sum.querySelector('.fig-title').textContent = title;
      fig.parentNode.insertBefore(det, fig);
      det.appendChild(sum);
      det.appendChild(fig);
      var legend = det.nextElementSibling;
      if (legend && legend.classList && legend.classList.contains('uml-legend')) {
        det.appendChild(legend);
      }
    });
  }
})();
