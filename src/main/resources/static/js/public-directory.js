(() => {
    'use strict';
    const list = document.getElementById('doctorDirectory');
    const status = document.getElementById('directoryStatus');
    const search = document.getElementById('doctorSearch');
    const specialty = document.getElementById('specialtySearch');
    const retry = document.getElementById('retryDirectory');
    const more = document.getElementById('moreDoctors');
    let doctors = [], visible = 6;
    const normalize = value => String(value || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLocaleLowerCase('ro');
    function render() {
        const term = normalize(search.value.trim());
        const matches = doctors.filter(d => (!specialty.value || d.specialty === specialty.value)
            && normalize(d.fullName + ' ' + d.specialty).includes(term));
        list.replaceChildren();
        matches.slice(0, visible).forEach(d => {
            const card = document.createElement('article'); card.className = 'directory-doctor';
            const initial = document.createElement('span'); initial.className = 'doctor-initial'; initial.setAttribute('aria-hidden', 'true'); initial.textContent = (d.fullName || 'M').charAt(0);
            const name = document.createElement('h3'); name.textContent = 'Dr. ' + d.fullName;
            const field = document.createElement('p'); field.textContent = d.specialty || 'Specialitate nespecificată';
            const link = document.createElement('a'); link.href = '/login'; link.className = 'public-text-link'; link.textContent = 'Intră în cont pentru programare →';
            card.append(initial, name, field, link); list.append(card);
        });
        status.textContent = matches.length ? `${matches.length} medici găsiți · ${Math.min(visible, matches.length)} afișați` : 'Nu am găsit medici pentru această căutare. Încearcă alt nume sau resetează filtrele.';
        more.hidden = matches.length <= visible;
    }
    async function load() {
        retry.hidden = true; search.disabled = specialty.disabled = true;
        list.setAttribute('aria-busy', 'true'); status.textContent = 'Se încarcă lista medicilor…';
        const controller = new AbortController(); const timer = setTimeout(() => controller.abort(), 10000);
        try {
            const response = await fetch('/api/doctors', {signal: controller.signal});
            if (!response.ok) throw new Error('Directory unavailable');
            const data = await response.json(); if (!Array.isArray(data)) throw new Error('Invalid directory');
            doctors = data.filter(d => d && typeof d.fullName === 'string' && (d.specialty == null || typeof d.specialty === 'string'))
                .sort((a, b) => a.fullName.localeCompare(b.fullName, 'ro'));
            specialty.replaceChildren(new Option('Toate specialitățile', ''));
            [...new Set(doctors.map(d => d.specialty).filter(Boolean))].sort((a,b)=>a.localeCompare(b,'ro')).forEach(s => specialty.add(new Option(s,s)));
            search.disabled = specialty.disabled = false; render();
        } catch (_) {
            list.replaceChildren(); more.hidden = true; retry.hidden = false;
            status.textContent = 'Lista medicilor nu este disponibilă momentan. Reîncearcă încărcarea sau intră în cont.';
        } finally { clearTimeout(timer); list.setAttribute('aria-busy', 'false'); }
    }
    document.getElementById('directoryFilters').addEventListener('submit', e => e.preventDefault());
    document.getElementById('directoryFilters').addEventListener('reset', () => setTimeout(() => { visible = 6; render(); }, 0));
    [search, specialty].forEach(el => el.addEventListener('input', () => { visible = 6; render(); }));
    more.addEventListener('click', () => { visible += 6; render(); }); retry.addEventListener('click', load); load();
})();
