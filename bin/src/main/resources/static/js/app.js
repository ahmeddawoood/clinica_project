

(function () {
    'use strict';
const toggleBtn = document.getElementById('sidebarToggle');
    const sidebar   = document.querySelector('.sidebar');
    const overlay   = document.getElementById('sidebarOverlay');

    if (toggleBtn && sidebar) {
        toggleBtn.addEventListener('click', () => {
            sidebar.classList.toggle('open');
            if (overlay) overlay.classList.toggle('d-none');
        });
    }

    if (overlay) {
        overlay.addEventListener('click', () => {
            sidebar.classList.remove('open');
            overlay.classList.add('d-none');
        });
    }
const currentPath = window.location.pathname;
    document.querySelectorAll('.sidebar-link').forEach(link => {
        const href = link.getAttribute('href');
        if (!href || href === '/') return;
        if (currentPath === href || currentPath.startsWith(href + '/')) {
            link.classList.add('active');
        }
    });
document.querySelectorAll('.alert-auto-dismiss').forEach(alert => {
        setTimeout(() => {
            try {
                bootstrap.Alert.getOrCreateInstance(alert).close();
            } catch (_) {}
        }, 4000);
    });
document.querySelectorAll('form[data-confirm]').forEach(form => {
        form.addEventListener('submit', e => {
            if (!confirm(form.getAttribute('data-confirm') || 'E?ti sigur?')) {
                e.preventDefault();
            }
        });
    });

    document.querySelectorAll('[data-confirm]:not(form)').forEach(el => {
        el.addEventListener('click', e => {
            if (!confirm(el.getAttribute('data-confirm') || 'E?ti sigur?')) {
                e.preventDefault();
            }
        });
    });
document.querySelectorAll('form').forEach(form => {
        form.addEventListener('submit', function () {
            const btn = form.querySelector('button[type="submit"]');
            if (btn && !btn.dataset.noDisable) {
                setTimeout(() => {
                    btn.disabled = true;
                    btn.dataset.originalText = btn.innerHTML;
                    btn.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span> Se proceseaza...';
                }, 0);
            }
        });
    });
document.querySelectorAll('[data-bs-toggle="tooltip"]').forEach(el => {
        new bootstrap.Tooltip(el, { trigger: 'hover' });
    });
const specialtyFilter = document.getElementById('specialtyFilter');
    const doctorSelect    = document.getElementById('doctorSelect');

    if (specialtyFilter && doctorSelect) {
        const allOptions = Array.from(doctorSelect.options).filter(o => o.value !== '');

        specialtyFilter.addEventListener('change', () => {
            const selected = specialtyFilter.value.toLowerCase();
            while (doctorSelect.options.length > 1) doctorSelect.remove(1);
            allOptions.forEach(opt => {
                if (!selected || opt.dataset.specialty?.toLowerCase() === selected) {
                    doctorSelect.add(opt.cloneNode(true));
                }
            });
            doctorSelect.value = '';
        });
    }
const apptDateInput = document.getElementById('appointmentDate');
    if (apptDateInput) {
        const now = new Date();
        now.setMinutes(now.getMinutes() - now.getTimezoneOffset());
        apptDateInput.min = now.toISOString().slice(0, 16);

        apptDateInput.addEventListener('change', () => {
            const date = new Date(apptDateInput.value);
            const hour = date.getHours();
            if (hour < 8 || hour >= 18) {
                apptDateInput.setCustomValidity('Programarile se pot face doar între 08:00 ?i 18:00.');
                apptDateInput.reportValidity();
            } else {
                apptDateInput.setCustomValidity('');
            }
        });
    }
document.querySelectorAll('input[data-search-table]').forEach(input => {
        const table = document.getElementById(input.getAttribute('data-search-table'));
        if (!table) return;
        const tbody = table.querySelector('tbody');

        input.addEventListener('input', () => {
            const q = input.value.toLowerCase().trim();
            tbody.querySelectorAll('tr').forEach(row => {
                row.style.display = (!q || row.textContent.toLowerCase().includes(q)) ? '' : 'none';
            });
            table.dispatchEvent(new Event('filterChanged'));
        });
    });
document.querySelectorAll('table[data-paginate]').forEach(table => {
        const perPage = parseInt(table.getAttribute('data-paginate')) || 10;
        const tbody   = table.querySelector('tbody');
        if (!tbody) return;

        let currentPage = 1;

        function visibleRows() {
            return Array.from(tbody.querySelectorAll('tr[data-status], tr:not(.pagination-empty)'))
                .filter(r => r.style.display !== 'none' && !r.classList.contains('pagination-empty'));
        }

        function render() {
            const rows = visibleRows();
            const totalPages = Math.max(1, Math.ceil(rows.length / perPage));
            if (currentPage > totalPages) currentPage = totalPages;

            rows.forEach((r, i) => {
                r.style.display = (i >= (currentPage - 1) * perPage && i < currentPage * perPage) ? '' : 'none';
            });

            let ctrl = table.parentElement.querySelector('.pagination-ctrl');
            if (!ctrl) {
                ctrl = document.createElement('div');
                ctrl.className = 'pagination-ctrl d-flex align-items-center justify-content-between px-3 py-2 border-top';
                table.parentElement.appendChild(ctrl);
            }

            const from = rows.length === 0 ? 0 : (currentPage - 1) * perPage + 1;
            const to   = Math.min(currentPage * perPage, rows.length);
            ctrl.innerHTML = `
                <span class="text-muted small">${from}–${to} din ${rows.length} înregistrari</span>
                <div class="d-flex gap-1">
                    <button class="btn btn-sm btn-outline-secondary" id="pgPrev" ${currentPage === 1 ? 'disabled' : ''}>
                        <i class="bi bi-chevron-left"></i>
                    </button>
                    <span class="btn btn-sm btn-primary disabled px-3">${currentPage} / ${totalPages}</span>
                    <button class="btn btn-sm btn-outline-secondary" id="pgNext" ${currentPage >= totalPages ? 'disabled' : ''}>
                        <i class="bi bi-chevron-right"></i>
                    </button>
                </div>`;

            ctrl.querySelector('#pgPrev')?.addEventListener('click', () => { currentPage--; render(); });
            ctrl.querySelector('#pgNext')?.addEventListener('click', () => { currentPage++; render(); });
        }

        table.addEventListener('filterChanged', () => { currentPage = 1; render(); });
        render();
    });
document.querySelectorAll('.priority-badge').forEach(badge => {
        const p = (badge.textContent || '').trim().toUpperCase();
        const map = {
            CRITICAL: 'priority-CRITICAL',
            HIGH:     'priority-HIGH',
            MEDIUM:   'priority-MEDIUM',
            LOW:      'priority-LOW',
        };
        if (map[p] && !badge.className.includes('priority-')) {
            badge.classList.add(map[p]);
        }
    });
document.querySelectorAll('.btn-print').forEach(btn => {
        btn.addEventListener('click', () => window.print());
    });

})();
function csrfHeaders() {
    const el = document.getElementById('csrf-holder');
    if (!el) return {};
    const headers = {};
    headers[el.dataset.header] = el.dataset.token;
    return headers;
}
(function () {
    const root    = document.documentElement;
    const STORAGE = 'mediclinic-theme';
    const saved = localStorage.getItem(STORAGE) || 'light';
    root.setAttribute('data-theme', saved);

    function updateIcon(theme) {
        const btn = document.getElementById('darkToggle');
        if (!btn) return;
        btn.innerHTML = theme === 'dark'
            ? '<i class="bi bi-sun-fill"></i>'
            : '<i class="bi bi-moon-fill"></i>';
        btn.title = theme === 'dark' ? 'Mod luminos' : 'Mod întunecat';
    }

    document.addEventListener('DOMContentLoaded', () => {
        updateIcon(root.getAttribute('data-theme'));

        const btn = document.getElementById('darkToggle');
        if (!btn) return;
        btn.addEventListener('click', () => {
            const next = root.getAttribute('data-theme') === 'dark' ? 'light' : 'dark';
            root.setAttribute('data-theme', next);
            localStorage.setItem(STORAGE, next);
            updateIcon(next);
        });
    });
})();
