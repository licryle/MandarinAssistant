(function () {
  'use strict';

  var prefersReduced = false;
  try { prefersReduced = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches; } catch (e) {}

  function stripWhitespace() {
    document.querySelectorAll('.framer-text').forEach(function (el) {
      Array.prototype.slice.call(el.childNodes).forEach(function (n) {
        if (n.nodeType === 3 && !n.nodeValue.trim() && /\n/.test(n.nodeValue)) {
          n.parentNode.removeChild(n);
        }
      });
    });
  }

  function setupReveal() {
    var spans = Array.prototype.slice.call(document.querySelectorAll('span')).filter(function (s) {
      return /opacity:\s*0\.001/.test(s.getAttribute('style') || '');
    });
    var groups = new Map();
    spans.forEach(function (sp) {
      var p = sp.closest('p, h1, h2, h3, h4, h5, h6');
      if (!p) return;
      if (!groups.has(p)) groups.set(p, []);
      groups.get(p).push(sp);
    });
    if (groups.size === 0) return;
    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (e) {
        if (!e.isIntersecting) return;
        var p = e.target;
        var toks = groups.get(p);
        if (!toks) { io.unobserve(p); return; }
        if (prefersReduced) {
          toks.forEach(function (sp) {
            sp.style.opacity = '1';
            sp.style.filter = 'blur(0px)';
            sp.style.transform = 'translateX(0px) translateY(0px) scale(1)';
            sp.style.transition = 'opacity 0.26s, transform 0.26s, filter 0.26s';
          });
          io.unobserve(p);
          return;
        }
        toks.forEach(function (sp, i) {
          var anim = sp.animate([
            { opacity: 0.001, filter: 'blur(10px)', transform: 'translateX(0px) translateY(10px) scale(1)' },
            { opacity: 1, filter: 'blur(0px)', transform: 'translateX(0px) translateY(0px) scale(1)' }
          ], { duration: 800, delay: i * 50, easing: 'cubic-bezier(0.16, 1, 0.3, 1)', fill: 'forwards' });
          anim.onfinish = function () {
            sp.style.opacity = '1';
            sp.style.filter = 'blur(0px)';
            sp.style.transform = 'translateX(0px) translateY(0px) scale(1)';
            sp.style.transition = 'opacity 0.26s, transform 0.26s, filter 0.26s';
          };
        });
        io.unobserve(p);
      });
    }, { threshold: 0, rootMargin: '0px' });
    groups.forEach(function (v, p) { io.observe(p); });
  }

  function setupTicker() {
    var uls = Array.prototype.slice.call(document.querySelectorAll('ul')).filter(function (ul) {
      return ul.querySelector('.ticker-item');
    });
    uls.forEach(function (ul, idx) {
      var items = Array.prototype.slice.call(ul.children);
      var clones = items.map(function (li) {
        var c = li.cloneNode(true);
        c.classList.remove('ticker-item');
        c.classList.add('clone-item');
        c.setAttribute('aria-hidden', 'true');
        return c;
      });
      clones.forEach(function (c) { ul.appendChild(c); });
      var period = ul.scrollWidth / 2;
      if (!period || isNaN(period)) period = 3208;
      var dir = (idx % 2 === 0) ? -1 : 1;
      ul.style.transition = 'opacity 0.6s ease, transform 0.6s ease';
      ul.style.opacity = '0';
      ul.style.transform = 'translateX(-20px)';
      var started = false;
      function startMarquee() {
        if (started) return;
        started = true;
        ul.style.transition = 'none';
        var duration = Math.max(20000, Math.round(period / 53.4 * 1000));
        var start = performance.now();
        var raf;
        function tick(now) {
          var t = ((now - start) / duration) % 1;
          var x;
          if (dir < 0) {
            x = -t * period;
          } else {
            x = -(1 - t) * period;
          }
          ul.style.transform = 'translateX(' + x + 'px)';
          raf = requestAnimationFrame(tick);
        }
        raf = requestAnimationFrame(tick);
      }
      setTimeout(function () {
        ul.style.opacity = '1';
        ul.style.transform = 'translateX(0px)';
        setTimeout(startMarquee, 600);
      }, 300);
    });
  }

  function setupFaq() {
    var questions = Array.prototype.slice.call(document.querySelectorAll('[name^="Question "]'));
    questions.forEach(function (q) {
      var card = q.querySelector('.framer-5dhLb');
      if (!card) return;
      var answer = card.querySelector('.framer-6bywzt');
      var hBar = card.querySelector('.framer-1z6a0z');
      var vBar = card.querySelector('.framer-1gm2mbo');
      var answerText = card.querySelector('.framer-vtny4s');

      function open() {
        var closedH = card.getBoundingClientRect().height;

        // Freeze current height.
        card.style.transition = 'none';
        card.style.height = closedH + 'px';
        card.style.overflow = 'hidden';
        void card.offsetHeight;

        // Apply open variant.
        card.classList.remove('framer-v-fa4ci');
        card.classList.add('framer-v-rhv0dy');
        card.setAttribute('data-framer-name', 'Opened');

        // Measure natural open height without the temporary height constraint.
        card.style.height = 'auto';
        void card.offsetHeight;
        var openH = card.getBoundingClientRect().height;

        // Restore starting height.
        card.style.height = closedH + 'px';
        void card.offsetHeight;

        if (hBar) {
          hBar.style.transition = 'transform 0.3s ease';
          hBar.style.transform = 'rotate(90deg)';
        }

        if (vBar) {
          vBar.style.transition = 'transform 0.3s ease';
          vBar.style.transform = 'rotate(90deg)';
        }

        if (answerText) {
          answerText.style.setProperty(
            '--extracted-r6o4lv',
            'var(--token-1e3ba35f-81ae-45f1-957d-fdbc8b701e2d, rgba(18, 19, 23, 0.58))'
          );
        }

        if (answer) {
          answer.style.transition = 'none';
          answer.style.opacity = '0';
          answer.style.transform = 'translateY(32px)';
        }

        requestAnimationFrame(function () {
          card.style.transition = 'height 0.4s ease';
          card.style.height = openH + 'px';

          if (answer) {
            answer.style.transition = 'opacity 0.35s ease, transform 0.35s ease';
            answer.style.opacity = '1';
            answer.style.transform = 'none';
          }

          setTimeout(function () {
            card.style.height = '';
            card.style.transition = '';
            card.style.overflow = '';
          }, 420);
        });
      }

      function close() {
        var openH = card.getBoundingClientRect().height;
        card.classList.remove('framer-v-rhv0dy');
        card.classList.add('framer-v-fa4ci');
        var targetH = card.getBoundingClientRect().height;
        card.classList.remove('framer-v-fa4ci');
        card.classList.add('framer-v-rhv0dy');
        card.style.height = openH + 'px';
        card.style.overflow = 'hidden';
        requestAnimationFrame(function () {
          card.style.transition = 'height 0.4s ease';
          card.style.height = targetH + 'px';
          if (answer) {
            answer.style.transition = 'opacity 0.3s ease, transform 0.3s ease';
            answer.style.opacity = '0';
            answer.style.transform = 'translateY(32px)';
          }
          if (hBar) { hBar.style.transition = 'transform 0.3s ease'; hBar.style.transform = 'rotate(-90deg)'; }
          if (vBar) { vBar.style.transition = 'transform 0.3s ease'; vBar.style.transform = 'rotate(-180deg)'; }
          if (answerText) answerText.style.setProperty('--extracted-r6o4lv', 'var(--token-cc359361-be1d-443c-b3d4-114af3dda3d6, rgba(255, 255, 255, 0.8))');
          setTimeout(function () {
            card.classList.remove('framer-v-rhv0dy');
            card.classList.add('framer-v-fa4ci');
            card.setAttribute('data-framer-name', 'Closed');
            card.style.height = '';
            card.style.transition = '';
            card.style.overflow = '';
          }, 420);
        });
      }
      function toggle() {
        if (card.classList.contains('framer-v-rhv0dy')) {
          close();
        } else {
          questions.forEach(function (oq) {
            var oc = oq.querySelector('.framer-5dhLb');
            if (oc && oc !== card && oc.classList.contains('framer-v-rhv0dy')) {
              var oa = oc.querySelector('.framer-6bywzt');
              var oh = oc.querySelector('.framer-1z6a0z');
              var ov = oc.querySelector('.framer-1gm2mbo');
              var ot = oc.querySelector('.framer-vtny4s');
              var openH2 = oc.getBoundingClientRect().height;
              oc.classList.remove('framer-v-rhv0dy');
              oc.classList.add('framer-v-fa4ci');
              var targetH2 = oc.getBoundingClientRect().height;
              oc.classList.remove('framer-v-fa4ci');
              oc.classList.add('framer-v-rhv0dy');
              oc.style.height = openH2 + 'px';
              oc.style.overflow = 'hidden';
              requestAnimationFrame(function () {
                oc.style.transition = 'height 0.4s ease';
                oc.style.height = targetH2 + 'px';
                if (oa) { oa.style.transition = 'opacity 0.3s ease, transform 0.3s ease'; oa.style.opacity = '0'; oa.style.transform = 'translateY(32px)'; }
                if (oh) { oh.style.transition = 'transform 0.3s ease'; oh.style.transform = 'rotate(-90deg)'; }
                if (ov) { ov.style.transition = 'transform 0.3s ease'; ov.style.transform = 'rotate(-180deg)'; }
                if (ot) ot.style.setProperty('--extracted-r6o4lv', 'var(--token-cc359361-be1d-443c-b3d4-114af3dda3d6, rgba(255, 255, 255, 0.8))');
                setTimeout(function () {
                  oc.classList.remove('framer-v-rhv0dy');
                  oc.classList.add('framer-v-fa4ci');
                  oc.setAttribute('data-framer-name', 'Closed');
                  oc.style.height = '';
                  oc.style.transition = '';
                  oc.style.overflow = '';
                }, 420);
              });
            }
          });
          open();
        }
      }
      card.addEventListener('click', toggle);
      card.addEventListener('keydown', function (e) {
        if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); toggle(); }
      });
    });
  }

  var DEFAULT_WORDS = [
    { pinyin: 'n\u01D0 h\u01CEo', hanzi: '\u4F60\u597D', translation: 'Bonjour', level: 'HSK 1', audio: 'audio/ni-hao.webm' },
    { pinyin: 'c\u01CEo m\xE9i', hanzi: '\u8349\u8393', translation: 'Fraise', level: 'HSK 3', audio: 'audio/cao-mei.webm' },
    { pinyin: 'gu\u0101n xi', hanzi: '\u5173\u7CFB', translation: 'Relation', level: 'HSK 3', audio: 'audio/guan-xi.webm' },
    { pinyin: 'ji\u0101 t\xEDng', hanzi: '\u5BB6\u5EAD', translation: 'Famille', level: 'HSK 3', audio: 'audio/jia-ting.webm' },
    { pinyin: 'ji\xE0n sh\xE8', hanzi: '\u5EFA\u8BBE', translation: 'Construction', level: 'HSK 5', audio: 'audio/jian-she.webm' },
    { pinyin: 'li\xE0n x\xED', hanzi: '\u7EC3\u4E60', translation: 'Exercice', level: 'HSK 3', audio: 'audio/lian-xi.webm' }
  ];
  var SET2_WORDS = [
    { pinyin: 'x\u01D0 ji\u01CEn chu\u012B', hanzi: '\u6D17\u526A\u5439', translation: 'Shampoing, coiffure, et s\\u00E8che-cheveux', level: '', audio: 'audio/xi-jian-chui.webm' },
    { pinyin: 'l\u01D0 f\xE0 di\xE0n', hanzi: '\u7406\u53D1\u5E97', translation: 'Salon de coiffure', level: '', audio: 'audio/li-fa-dian.webm' },
    { pinyin: 'ju\u01CEn f\xE0', hanzi: '\u5377\u53D1', translation: 'Friser les cheveux', level: '', audio: 'audio/juan-fa.webm' },
    { pinyin: 'r\u01CEn f\xE0', hanzi: '\u67D3\u53D1', translation: 'Teindre ses cheveux', level: '', audio: 'audio/ran-fa.webm' },
    { pinyin: 'ji\u01CEn d\u0101o', hanzi: '\u526A\u5200', translation: 'ciseaux', level: 'HSK5', audio: 'audio/jian-dao.webm' },
    { pinyin: 'x\u01D0 f\xE0 shu\u01D0', hanzi: '\u6D17\u53D1\u6C34', translation: 'Shampoing', level: '', audio: 'audio/xi-fa-shui.webm' }
  ];

  function setupCards() {
    var cards = Array.prototype.slice.call(document.querySelectorAll('.ma-card'));
    var audios = Array.prototype.slice.call(document.querySelectorAll('audio'));
    cards.forEach(function (card, i) {
      var audio = audios[i] || null;
      var words = (i === 0) ? DEFAULT_WORDS : SET2_WORDS;
      card._words = words;

      var audioBtns = Array.prototype.slice.call(card.querySelectorAll('.ma-snd')).filter(function (b) {
        return /Écouter|ecouter/i.test(b.getAttribute('aria-label') || '');
      });
      var nextBtns = Array.prototype.slice.call(card.querySelectorAll('.ma-snd')).filter(function (b) {
        return /Mot suivant/i.test(b.getAttribute('aria-label') || '');
      });

      audioBtns.forEach(function (btn) {
        btn.addEventListener('click', function (e) {
          e.stopPropagation();
          if (!audio || !audio.src) return;
          if (audio.paused) { audio.currentTime = 0; audio.play().catch(function () {}); }
          else { audio.pause(); }
        });
      });
      if (audio) {
        audio.addEventListener('play', function () { card.classList.add('on'); });
        audio.addEventListener('pause', function () { card.classList.remove('on'); });
        audio.addEventListener('ended', function () { card.classList.remove('on'); });
      }

      nextBtns.forEach(function (btn) {
        btn.addEventListener('click', function (e) {
          e.stopPropagation();
          var svg = btn.querySelector('svg');
          if (svg) {
            svg.style.transition = 'transform 0.6s cubic-bezier(0.4, 0, 0.2, 1)';
            var m = (svg.style.transform || '').match(/rotate\(([-\d.]+)deg\)/);
            var cur = m ? parseFloat(m[1]) : 0;
            svg.style.transform = 'rotate(' + (cur + 360) + 'deg)';
          }
          var spans = Array.prototype.slice.call(card.querySelectorAll('span[style*="transition: opacity 0.26s"]'));
          var levelSpan = spans[0];
          var pinyinSpan = spans[1];
          var hanziSpan = spans[2];
          var transSpan = spans[3];

          var curHanzi = hanziSpan ? hanziSpan.textContent.trim() : '';
          var idx = 0;
          var found = false;
          for (var wi = 0; wi < words.length; wi++) {
            if (words[wi].hanzi === curHanzi) { idx = wi; found = true; break; }
          }
          var nextWord;
          if (!found) {
            nextWord = words[0];
          } else {
            nextWord = words[(idx + 1) % words.length];
          }

          spans.forEach(function (sp) {
            sp.style.transition = 'opacity .26s ease, transform .26s ease, filter .26s ease';
            sp.style.opacity = '0';
            sp.style.transform = 'scale(.96)';
            sp.style.filter = 'blur(6px)';
          });
          setTimeout(function () {
            if (pinyinSpan) pinyinSpan.textContent = nextWord.pinyin + ' ';
            if (hanziSpan) hanziSpan.textContent = nextWord.hanzi;
            if (transSpan) transSpan.textContent = nextWord.translation;
            if (levelSpan) levelSpan.textContent = nextWord.level;
            audioBtns.forEach(function (b) {
              b.setAttribute('aria-label', 'Écouter la prononciation de ' + nextWord.pinyin + ' ');
            });
            if (audio) {
              var audioFile = nextWord.audio || '';
              if (audioFile) {
                audio.src = audioFile;
              } else {
                audio.removeAttribute('src');
              }
              audio.load();
            }
            spans.forEach(function (sp) {
              sp.style.opacity = '1';
              sp.style.transform = 'scale(1)';
              sp.style.filter = 'blur(0)';
            });
          }, 260);
        });
      });

      setupBeam(card);
    });
  }

  function setupBeam(card) {
    var svg = card.querySelector('svg[viewBox="0 0 1700 1200"]');
    if (!svg) { return; }
    var paths = Array.prototype.slice.call(svg.querySelectorAll('path[stroke-dasharray]'));
    if (paths.length === 0) { return; }
    var Te4 = paths.length;
    var b2 = 0.3;
    var ne4 = Array.from({ length: Te4 }, function (_, t) { return 1000 * b2 * (t + 1) / Te4; });
    var v2 = 6;
    var start = performance.now();
    var raf;
    function tick(now) {
      var a3 = ((now - start) / 1000 / v2) % 1 * 1000;
      paths.forEach(function (p, e4) {
        p.setAttribute('stroke-dashoffset', String(ne4[e4] - a3));
      });
      raf = requestAnimationFrame(tick);
    }
    raf = requestAnimationFrame(tick);
  }

  function setupFlashes() {
    document.querySelectorAll('.ma-flash, .ma-flash-slow').forEach(function (el) {
      el.style.setProperty('--ma-play', 'running');
    });
  }

  function setupNavIndicators() {
    document.querySelectorAll('[data-framer-page-link-current="true"] .framer-8xjv6s').forEach(function (el) {
      el.style.opacity = '1';
    });
  }

  function setupMenu() {
    if (window.matchMedia && !window.matchMedia('(max-width: 809.98px)').matches) return;
    var btn = document.querySelector('.framer-g6dnnw');
    if (!btn) return;
    // find the mobile overlay: its parent nav has framer-v-1op6k05/wxn6ma/z534k0
    var overlays = Array.prototype.slice.call(document.querySelectorAll('.framer-1nssdn1'));
    var overlay = overlays.find(function (o) {
      var nav = o.parentElement;
      return nav && /framer-v-(1op6k05|wxn6ma|z534k0)/.test(nav.className);
    });
    if (!overlay) return;
    var nav = overlay.parentElement;
    var open = false;
    overlay.style.opacity = '0';
    overlay.style.pointerEvents = 'none';
    overlay.style.transition = 'opacity 0.25s ease';
    if (nav) { nav.style.overflow = 'hidden'; }
    btn.addEventListener('click', function (e) {
      e.stopPropagation();
      open = !open;
      overlay.style.transition = 'opacity 0.25s ease';
      overlay.style.opacity = open ? '1' : '0';
      overlay.style.pointerEvents = open ? 'auto' : 'none';
      if (nav) {
        nav.style.overflow = open ? 'visible' : 'hidden';
        nav.style.height = open ? 'auto' : '64px';
      }
    });
  }

  function setupHoverCss() {
    if (document.getElementById('ma-hover-css')) return;
    var style = document.createElement('style');
    style.id = 'ma-hover-css';
    style.textContent = '.framer-ddmvkx { transition: transform 0.4s cubic-bezier(0.34, 1.56, 0.64, 1); } .framer-ddmvkx:hover { transform: rotate(-9deg) scale(1.05); }';
    document.head.appendChild(style);
  }

  function setupBreathing() {
    if (document.getElementById('ma-breathing-css')) return;
    var style = document.createElement('style');
    style.id = 'ma-breathing-css';
    style.textContent = '@keyframes ma-breathe-y {  0%, 100% {   translate: 0 0;  } 50% {    translate: 0 var(--ma-breath-amp, -4px); animation-duration: 3s;}}';
    document.head.appendChild(style);
    var tiles = [
      /** Hero */
      { cls: 'framer-1srmklb', amp: -4 },
      { cls: 'framer-15u0sjo', amp: -5 },
      { cls: 'framer-1tzcm02', amp: -1 },
      { cls: 'framer-1pqgq3w', amp: 5 },
      { cls: 'framer-197nmll', amp: 3 },
      { cls: 'framer-ugovxm', amp: -3 },
      { cls: 'framer-1hfvizv', amp: 1 },
      { cls: 'framer-107r3zv', amp: 2 },
      /** Revise */
      { cls: 'framer-yoyvz9', amp: -4 },
      { cls: 'framer-dr02yz', amp: -5 },
      { cls: 'framer-15vlru6', amp: -1 },
      { cls: 'framer-zx43nw', amp: 5 },
      { cls: 'framer-27p5zf', amp: 3 },
      /** Footer */
      { cls: 'framer-lekqks', amp: -7 },
      { cls: 'framer-11iry6f', amp: -7 },
      { cls: 'framer-1ycfkgx', amp: 9 },
      { cls: 'framer-1wvkp5p', amp: -11 },
      { cls: 'framer-tusu5h', amp: 7 },
      { cls: 'framer-l24oz2', amp: -9 },
      { cls: 'framer-1ow1247', amp: 11 },

      { cls: 'framer-h0e3', amp: -4 },
      { cls: 'framer-q2kkja', amp: -5 },
      { cls: 'framer-ft4ft1', amp: -1 },
      { cls: 'framer-130mkzc', amp: 5 },
      { cls: 'framer-1p0dpw9', amp: 3 },
      { cls: 'framer-1j264d1', amp: -3 },
      { cls: 'framer-1uyskhb', amp: 1 },
      { cls: 'framer-1gux3ff', amp: 2 },

      { cls: 'framer-1rwwqd5', amp: -4 },
      { cls: 'framer-go210', amp: -5 },
      { cls: 'framer-1gjouq8', amp: -1 },
      { cls: 'framer-1un0fmi', amp: 5 },
      { cls: 'framer-av5cln', amp: 3 },
      { cls: 'framer-llu32x', amp: -3 },
    ];
    var i = 0;
    tiles.forEach(function (t) {
      var els = Array.prototype.slice.call(document.querySelectorAll('.' + t.cls));
      els.forEach(function (el) {
        el.style.setProperty('--ma-breath-amp', t.amp + 'px');
        var dur = 2.0 + (i * 0.13);
        var delay = 0.8 + (i * 0.07);
        el.style.animation = 'ma-breathe-y ' + dur.toFixed(2) + 's cubic-bezier(0.54, 0.01, 0.51, 1) ' + delay.toFixed(2) + 's infinite';
        i++;
      });
    });
  }

  function setupVideoSync() {
    var video = document.querySelector('.framer-168es9-container video');
    if (!video) return;

    var timings = [
      { id: '1', start: 1, end: 5 },
      { id: '2', start: 7, end: 14 },
      { id: '3', start: 18, end: 24 }
    ];

    var containers = timings.map(function (t) {
      return {
        el: document.querySelector('[data-video-sync="' + t.id + '"]'),
        start: t.start,
        end: t.end
      };
    }).filter(function (item) {
      return item.el;
    });

    function update() {
      var time = video.currentTime;

      containers.forEach(function (item) {
        item.el.classList.toggle(
          'video-sync-visible',
          time >= item.start && time < item.end
        );
      });
    }

    video.addEventListener('timeupdate', update);
    video.addEventListener('seeked', update);

    update();

    setInterval(update, 50);
  }

  function setupScrollLogo() {
    var logos = document.querySelectorAll('.framer-8xjv6s');
    if (!logos.length) return;

    function update() {
      var scrollY = window.scrollY;
      var visible = scrollY > 160;

      logos.forEach(function (logo, index) {
        logo.classList.toggle('logo-visible', visible);
      });
    }

    update();
    window.addEventListener('scroll', update, { passive: true });
  }

  function revealAppearFallback() {
    setTimeout(function () {
      document.querySelectorAll('[data-framer-appear-id]').forEach(function (el) {
        var cs = getComputedStyle(el);
        if (parseFloat(cs.opacity) < 0.5) {
          el.style.opacity = '1';
          el.style.transform = 'none';
        }
      });
    }, 2000);
  }

  function init() {
    stripWhitespace();
    setupReveal();
    setupTicker();
    setupFaq();
    setupCards();
    setupFlashes();
    setupNavIndicators();
    setupMenu();
    setupHoverCss();
    setupBreathing();
    setupVideoSync();
    setupScrollLogo();
    revealAppearFallback();
  }

  if (document.body) {
    init();
  } else {
    document.addEventListener('DOMContentLoaded', init);
  }
})();
