"""Runs only against the disposable localhost CI application and demo fixtures."""
import json
import pathlib
from playwright.sync_api import sync_playwright

BASE = 'http://127.0.0.1:8082'
OUT = pathlib.Path('e2e-results')
OUT.mkdir(exist_ok=True)
results = []

def check(name, action):
    try:
        action()
        results.append({'name': name, 'passed': True})
    except Exception as exc:
        results.append({'name': name, 'passed': False, 'error': str(exc)[:1500]})
    print(json.dumps(results[-1]), flush=True)

def require(condition, message):
    assert condition, message

with sync_playwright() as p:
    browser = p.chromium.launch()
    for role, email, password, paths in [
        ('patient', 'mihai@email.com', 'patient123', ['dashboard','profile','book','history','doctors','notifications','prescriptions','messages']),
        ('doctor', 'doctor1@clinic.com', 'doctor123', ['dashboard','profile','calendar','notifications','prescriptions','messages','schedule','queue']),
        ('admin', 'admin@clinic.com', 'admin123', ['dashboard','patients','doctors','appointments','users']),
    ]:
        context = browser.new_context(viewport={'width': 1440, 'height': 1000})
        page = context.new_page()
        errors = []
        page.on('pageerror', lambda exc: errors.append(str(exc)))
        def login():
            page.goto(BASE + '/login')
            page.locator('[name=username]').fill(email)
            page.locator('[name=password]').fill(password)
            page.locator('button[type=submit]').click()
            page.wait_for_url(BASE + '/' + role + '/dashboard')
        check(role + ' login', login)
        for path in paths:
            def visit(path=path):
                errors.clear()
                response = page.goto(BASE + '/' + role + '/' + path, wait_until='networkidle')
                require(response.status == 200, f'HTTP {response.status}')
                require('/login' not in page.url and '/error' not in page.url, 'Unexpected redirect')
                require(len(page.locator('body').inner_text()) > 100, 'Empty page')
                if role == 'doctor' and path == 'dashboard':
                    require(page.evaluate("Chart.getChart('monthlyChart').data.labels.length === 6"), 'Missing monthly chart labels')
                    require(page.evaluate("Chart.getChart('monthlyChart').data.datasets[0].data.some(n => n > 0)"), 'Monthly chart has no fixture appointments')
                page.screenshot(path=str(OUT / f'{role}-{path}.png'), full_page=True)
                require(not errors, 'Browser errors: ' + '; '.join(errors))
            check(role + ' ' + path, visit)
        def mobile():
            page.set_viewport_size({'width': 390, 'height': 844})
            response = page.goto(BASE + '/' + role + '/dashboard')
            require(response.status == 200, f'HTTP {response.status}')
            page.screenshot(path=str(OUT / f'{role}-mobile.png'), full_page=True)
            dimensions = page.evaluate('({width: innerWidth, scroll: document.documentElement.scrollWidth})')
            require(dimensions['scroll'] <= dimensions['width'] + 1,
                    f'Mobile horizontal overflow: {dimensions}')
            page.locator('#sidebarToggle').click()
            page.wait_for_timeout(250)
            require(page.locator('.sidebar').evaluate('(el) => el.getBoundingClientRect().left') >= -1,
                    'Mobile navigation did not open')
        check(role + ' mobile dashboard', mobile)
        if role == 'admin':
            def search_appointments():
                page.goto(BASE + '/admin/appointments')
                require(page.locator('#apptTable tbody tr').count() <= 20, 'Unbounded appointment page')
                page.locator('#appointment-search').fill('no-such-patient-fixture')
                page.get_by_role('button', name='Caută', exact=True).click()
                page.wait_for_url('**/admin/appointments?**')
                require(page.locator('#apptTable tbody tr').count() == 0, 'Search ignored')
                require('Nicio programare' in page.locator('body').inner_text(), 'Missing empty state')
                page.get_by_role('link', name='Resetează', exact=True).click()
                page.wait_for_url(BASE + '/admin/appointments')
                require(page.locator('#apptTable tbody tr').count() > 0, 'Reset lost appointments')
                dimensions = page.evaluate('({width: innerWidth, scroll: document.documentElement.scrollWidth})')
                require(dimensions['scroll'] <= dimensions['width'] + 1, 'Appointment mobile overflow')
                page.screenshot(path=str(OUT / 'admin-appointments-mobile.png'), full_page=True)
            check('admin appointment search, reset and mobile layout', search_appointments)
        if role == 'patient':
            def save_profile():
                page.goto(BASE + '/patient/profile')
                page.locator('[name=phone]').fill('0700000123')
                page.locator('form').filter(has=page.locator('[name=phone]')).locator('button[type=submit]').click()
                page.wait_for_url(BASE + '/patient/profile')
                page.reload()
                require(page.locator('[name=phone]').input_value() == '0700000123', 'Profile change not persisted')
            check('patient profile save and reload', save_profile)
            page.goto(BASE + '/patient/dashboard')
            token = page.locator('#csrf-holder').get_attribute('data-token')
            header = page.locator('#csrf-holder').get_attribute('data-header')
            def live_ai():
                response = context.request.post(BASE + '/api/ai/chat', headers={header:token},
                    data={'message': '/no_think Use findDoctorsBySpecialty to list the clinic doctors in Cardiologie. Return their names only.'}, timeout=240000)
                require(response.status == 200, f'AI HTTP {response.status}: {response.text()[:200]}')
                message = response.json().get('message', '')
                require('Maria' in message and 'Ionescu' in message, 'AI did not return the seeded cardiology doctor')
            check('live Ollama through authenticated app with clinic doctor lookup', live_ai)
            check('patient cannot enter admin', lambda: require(context.request.get(BASE + '/admin/users').status == 403, 'Admin access allowed'))
        context.close()
    browser.close()

(OUT / 'results.json').write_text(json.dumps(results, indent=2))
failed = [r for r in results if not r['passed']]
print(f'{len(results)-len(failed)}/{len(results)} checks passed', flush=True)
if failed:
    raise SystemExit(1)
