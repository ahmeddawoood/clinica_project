"""Public discovery and account entry, using disposable CI fixtures only."""
from pathlib import Path
from playwright.sync_api import sync_playwright, expect
BASE='http://127.0.0.1:8082'
out=Path('e2e-results');out.mkdir(exist_ok=True)
with sync_playwright() as p:
    browser=p.chromium.launch()
    page=browser.new_page(viewport={'width':1440,'height':1050})
    errors=[]
    page.on('pageerror',lambda error:errors.append(str(error)))
    page.route('**/api/doctors',lambda route:route.fulfill(status=503,body='unavailable'))
    assert page.goto(BASE+'/').status==200
    expect(page.locator('#retryDirectory')).to_be_visible()
    page.unroute('**/api/doctors')
    page.locator('#retryDirectory').click()
    expect(page.locator('.directory-doctor')).to_have_count(6)
    page.locator('#specialtySearch').select_option(label='Cardiologie')
    expect(page.locator('.directory-doctor')).to_have_count(1)
    page.locator('#doctorSearch').fill('no-such-doctor-123')
    expect(page.locator('.directory-doctor')).to_have_count(0)
    page.get_by_role('button',name='Resetează',exact=True).click()
    expect(page.locator('.directory-doctor')).to_have_count(6)
    page.screenshot(path=str(out/'public-home-desktop.png'),full_page=True,animations='disabled')
    page.locator('#moreDoctors').click()
    expect(page.locator('.directory-doctor')).to_have_count(12)
    page.set_viewport_size({'width':390,'height':844})
    page.get_by_role('button',name='Resetează',exact=True).click()
    assert page.evaluate('document.documentElement.scrollWidth <= innerWidth+1')
    page.screenshot(path=str(out/'public-home-mobile.png'),full_page=True,animations='disabled')
    booking_path = page.locator('.directory-doctor a').first.get_attribute('href')
    assert booking_path.startswith('/patient/book?doctorId=')
    selected_doctor = booking_path.split('doctorId=')[1]
    page.locator('.directory-doctor a').first.click()
    page.wait_for_url(BASE+'/login')
    expect(page.get_by_label('Adresă email',exact=True)).to_be_visible()
    assert 'admin123' not in page.locator('body').inner_text()
    page.screenshot(path=str(out/'public-login-mobile.png'),full_page=True,animations='disabled')
    page.locator('[name=username]').fill('mihai@email.com')
    page.locator('[name=password]').fill('patient123')
    page.locator('#togglePassword').click()
    expect(page.locator('#passwordInput')).to_have_attribute('type','text')
    page.locator('button[type=submit]').click()
    page.wait_for_url(BASE+booking_path)
    expect(page.locator('#doctorSelect')).to_have_value(selected_doctor)
    expect(page.locator('#bookingSubmit')).to_be_disabled()
    # The home route redirects signed-in patients to their dashboard.
    # A saved directory link must still work when opened while signed in.
    page.goto(BASE+'/')
    page.wait_for_url('**/patient/dashboard')
    page.goto(BASE+booking_path)
    page.wait_for_url(BASE+booking_path)
    expect(page.locator('#doctorSelect')).to_have_value(selected_doctor)
    page.goto(BASE+'/patient/book?doctorId=invalid')
    expect(page.locator('#doctorSelect')).to_have_value('')
    expect(page.locator('#bookingSubmit')).to_be_disabled()
    # Fresh unauthenticated context for account forms and malicious directory text.
    page.close()
    page=browser.new_page(viewport={'width':390,'height':844})
    page.on('pageerror',lambda error:errors.append(str(error)))
    for route in ['/register','/forgot-password','/reset-password?token=invalid-fixture']:
        assert page.goto(BASE+route).status==200
        assert page.evaluate('document.documentElement.scrollWidth <= innerWidth+1'),route
        page.screenshot(path=str(out/('public-'+route.split('?')[0].strip('/')+'-mobile.png')),full_page=True,animations='disabled')
    page.route('**/api/doctors',lambda route:route.fulfill(json=[{'fullName':'<img src=x onerror=alert(1)>','specialty':'Test'}]))
    page.goto(BASE+'/')
    expect(page.locator('.directory-doctor')).to_have_count(1)
    expect(page.locator('.directory-doctor img')).to_have_count(0)
    assert not errors,errors
    print('PASS public discovery: retry, specialty/search/reset, pagination, mobile, safe text rendering, account entry and recovery pages')
    browser.close()
