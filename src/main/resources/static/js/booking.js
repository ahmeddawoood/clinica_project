(() => {
    'use strict';
    const el = id => document.getElementById(id);
    const doctor = el('doctorSelect'), date = el('bookingDate'), timestamp = el('appointmentDate');
    let requestNumber = 0;
    const initial = timestamp.value;
    if (initial) date.value = initial.slice(0, 10);
    function updateSummary() {
        el('summaryDoctor').textContent = doctor.value ? doctor.selectedOptions[0].textContent : 'Nu ai selectat încă';
        el('summaryDate').textContent = date.value ? new Intl.DateTimeFormat('ro-RO', {dateStyle:'long'}).format(new Date(date.value + 'T12:00')) : 'Nu ai selectat încă';
        el('summaryTime').textContent = timestamp.value ? timestamp.value.slice(11,16) : 'Alege un interval';
        el('bookingSubmit').disabled = !timestamp.value;
    }
    async function loadSlots() {
        const current = ++requestNumber;
        timestamp.value = '';
        el('slotOptions').replaceChildren();
        el('retrySlots').hidden = true;
        updateSummary();
        if (!doctor.value || !date.value) {
            el('slotStatus').textContent = 'Alege un medic și o dată pentru a vedea orele disponibile.';
            return;
        }
        el('slotStatus').textContent = 'Se verifică disponibilitatea…';
        try {
            const response = await fetch('/api/slots/' + encodeURIComponent(doctor.value) + '/' + encodeURIComponent(date.value), {headers: {'Accept':'application/json'}});
            if (!response.ok || response.redirected) throw new Error('slots unavailable');
            const slots = await response.json();
            if (current !== requestNumber) return;
            if (!Array.isArray(slots) || slots.some(s => !/^\d{2}:00$/.test(s))) throw new Error('invalid slots');
            el('slotStatus').textContent = slots.length ? 'Selectează o oră. Intervalele sunt afișate în ora locală a clinicii.' : 'Nu există intervale disponibile. Alege altă dată sau alt medic.';
            slots.forEach((slot, index) => {
                const wrapper = document.createElement('div'); wrapper.className = 'slot-option';
                const input = document.createElement('input'); input.type = 'radio'; input.name = 'selectedSlot'; input.id = 'slot-' + index; input.value = slot; input.required = true;
                const label = document.createElement('label'); label.htmlFor = input.id; label.textContent = slot;
                input.addEventListener('change', () => { timestamp.value = date.value + 'T' + slot; updateSummary(); });
                wrapper.append(input, label); el('slotOptions').append(wrapper);
            });
        } catch (_) {
            if (current !== requestNumber) return;
            el('slotStatus').textContent = 'Nu am putut încărca orele. Reîncearcă; dacă sesiunea a expirat, autentifică-te din nou.';
            el('retrySlots').hidden = false;
        }
    }
    el('specialtyFilter').addEventListener('change', event => {
        const specialty = event.target.value.toLocaleLowerCase('ro');
        Array.from(doctor.options).forEach(option => { option.hidden = !!option.value && !!specialty && option.dataset.specialty?.toLocaleLowerCase('ro') !== specialty; option.disabled = option.hidden; });
        doctor.value = ''; loadSlots();
    });
    doctor.addEventListener('change', loadSlots); date.addEventListener('change', loadSlots);
    el('retrySlots').addEventListener('click', loadSlots);
    el('bookingForm').addEventListener('submit', event => {
        if (!timestamp.value) { event.preventDefault(); return; }
        el('bookingSubmit').disabled = true; el('submitStatus').textContent = 'Se verifică și se salvează programarea…';
    });
    window.addEventListener('pageshow', loadSlots);
})();
