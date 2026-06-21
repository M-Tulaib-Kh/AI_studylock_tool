/* ═══════════════════════════════════════════
   StudyLock App JS — v3.0
═══════════════════════════════════════════ */

// ── Dark Mode ─────────────────────────────
function toggleDarkMode() {
  const isDark = document.body.classList.toggle('dark-mode');
  localStorage.setItem('darkMode', isDark);
  const icon = document.getElementById('darkIcon');
  if (icon) icon.className = isDark ? 'ti ti-sun' : 'ti ti-moon';
}

(function initDarkMode() {
  if (localStorage.getItem('darkMode') === 'true') {
    document.body.classList.add('dark-mode');
    const icon = document.getElementById('darkIcon');
    if (icon) icon.className = 'ti ti-sun';
  }
})();

// ── Mobile sidebar toggle ─────────────────
function toggleSidebar() {
  const sidebar = document.querySelector('.sidebar');
  if (sidebar) sidebar.classList.toggle('open');
}

document.addEventListener('click', function(e) {
  const sidebar = document.querySelector('.sidebar');
  if (!sidebar) return;
  if (!sidebar.contains(e.target) && !e.target.closest('.sidebar-toggle')) {
    sidebar.classList.remove('open');
  }
});

// ── Auto-dismiss alerts ───────────────────
document.addEventListener('DOMContentLoaded', function() {
  document.querySelectorAll('.alert').forEach(function(a) {
    setTimeout(function() {
      a.style.transition = 'opacity 0.5s';
      a.style.opacity = '0';
      setTimeout(function() { a.remove(); }, 500);
    }, 5000);
  });
});

// ── Active nav item from URL ──────────────
document.addEventListener('DOMContentLoaded', function() {
  const path = window.location.pathname;
  document.querySelectorAll('.nav-item').forEach(function(item) {
    const href = item.getAttribute('href');
    if (href && path.startsWith(href) && href !== '/') {
      item.classList.add('active');
    }
  });
});

// ── Confirm delete helper ─────────────────
function confirmDelete(msg) {
  return confirm(msg || 'Are you sure you want to delete this?');
}

// ── Searchable / hover dropdown (any selectable field) ────
// Upgrades any <select class="course-select"> or <select class="searchable-select">
// into a searchable, hover-friendly dropdown. The original <select> stays in the
// DOM (hidden) so form submission and any existing th:field/name bindings, plus any
// onchange="..." handlers already on the element, keep working unchanged.
function enhanceCourseSelects() {
  document.querySelectorAll('select.course-select, select.searchable-select').forEach(function(select) {
    if (select.dataset.enhanced === '1') return;
    select.dataset.enhanced = '1';

    const options = Array.from(select.options).filter(function(o) { return o.value !== ''; });
    const placeholder = (select.options[0] && select.options[0].value === '') ? select.options[0].text : 'Select...';

    const wrap = document.createElement('div');
    wrap.className = 'course-picker';

    const input = document.createElement('input');
    input.type = 'text';
    input.className = 'course-picker-input';
    input.placeholder = placeholder;
    input.autocomplete = 'off';
    input.readOnly = false;
    if (select.value) {
      const sel = options.find(function(o) { return o.value === select.value; });
      if (sel) input.value = sel.text;
    }

    const caret = document.createElement('i');
    caret.className = 'ti ti-chevron-down course-picker-caret';

    const menu = document.createElement('div');
    menu.className = 'course-picker-menu';

    function renderMenu(filter) {
      menu.innerHTML = '';
      const f = (filter || '').toLowerCase();
      const filtered = options.filter(function(o) { return o.text.toLowerCase().includes(f); });
      if (filtered.length === 0) {
        const empty = document.createElement('div');
        empty.className = 'course-picker-empty';
        empty.textContent = 'No matching courses';
        menu.appendChild(empty);
        return;
      }
      filtered.forEach(function(o, idx) {
        const opt = document.createElement('div');
        opt.className = 'course-picker-option';
        if (idx === 0) opt.classList.add('highlighted');
        const meta = o.getAttribute('data-meta');
        opt.innerHTML = '<span>' + o.text.replace(/</g, '&lt;') + '</span>' +
          (meta ? '<span class="cp-meta">' + meta.replace(/</g, '&lt;') + '</span>' : '');
        opt.addEventListener('mousedown', function(e) {
          e.preventDefault();
          select.value = o.value;
          input.value = o.text;
          select.dispatchEvent(new Event('change', { bubbles: true }));
          closeMenu();
        });
        menu.appendChild(opt);
      });
    }

    function openMenu() {
      wrap.classList.add('open');
      renderMenu(input.value === (select.selectedIndex >= 0 ? select.options[select.selectedIndex].text : '') ? '' : input.value);
    }
    function closeMenu() { wrap.classList.remove('open'); }

    input.addEventListener('focus', function() { input.select(); openMenu(); });
    input.addEventListener('click', openMenu);
    input.addEventListener('input', function() { openMenu(); renderMenu(input.value); });
    input.addEventListener('keydown', function(e) {
      if (e.key === 'Escape') { closeMenu(); input.blur(); }
      if (e.key === 'Enter') { e.preventDefault(); }
    });
    document.addEventListener('click', function(e) {
      if (!wrap.contains(e.target)) closeMenu();
    });

    select.style.display = 'none';
    select.parentNode.insertBefore(wrap, select);
    wrap.appendChild(input);
    wrap.appendChild(caret);
    wrap.appendChild(menu);
    wrap.appendChild(select);
  });
}

document.addEventListener('DOMContentLoaded', enhanceCourseSelects);

