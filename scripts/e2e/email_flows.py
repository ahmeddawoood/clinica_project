"""SMTP capture and reset-password checks with disposable seeded users only."""
import html
import json
import pathlib
import re
import time
import urllib.request
from urllib.parse import urlparse, parse_qs
from playwright.sync_api import sync_playwright

BASE='http://127.0.0.1:8082'
MAIL='http://127.0.0.1:8025'
results=[]

def messages():
    with urllib.request.urlopen(MAIL+'/api/v1/messages',timeout=10) as r:
        return json.load(r)['messages']

def require(c,m):
    assert c,m

def check(name,fn):
    try:
        fn()
        results.append({'name':name,'passed':True})
    except Exception as e:
        # Do not write reset links, headers, or payloads to logs/artifacts.
        results.append({'name':name,'passed':False,'error_type':type(e).__name__})
    print(json.dumps(results[-1]),flush=True)

def booking_mail():
    subjects=[m['Subject'] for m in messages() if any(t['Address']=='mihai@email.com' for t in m['To'])]
    for marker in ['Programare creată','Plată confirmată','Programare confirmată','Programare anulată']:
        require(any(marker in s for s in subjects),'Missing booking lifecycle email')
check('SMTP captures booking payment confirmation and cancellation emails',booking_mail)

with sync_playwright() as pw:
    browser=pw.chromium.launch()
    context=browser.new_context()
    page=context.new_page()
    def reset_flow():
        page.goto(BASE+'/forgot-password')
        page.locator('[name=email]').fill('ana@email.com')
        page.locator('button[type=submit]').click()
        page.wait_for_url(BASE+'/forgot-password?sent')
        found=None
        for _ in range(30):
            found=next((m for m in messages() if 'Resetare parol' in m['Subject'] and any(t['Address']=='ana@email.com' for t in m['To'])),None)
            if found: break
            time.sleep(1)
        require(found is not None,'Password reset email not received')
        with urllib.request.urlopen(MAIL+'/api/v1/message/'+found['ID'],timeout=10) as r:
            content=json.load(r)['HTML']
        match=re.search(r'href="([^"]*reset-password\?token=[^"]+)"',content)
        require(match is not None,'Reset link absent')
        link=html.unescape(match.group(1))
        require(link.startswith(BASE+'/reset-password?token='),'Reset link must point to test app')
        token=parse_qs(urlparse(link).query)['token'][0]
        page.goto(link)
        page.locator('[name=password]').fill('Test-reset-password-123')
        page.locator('[name=confirmPassword]').fill('Test-reset-password-123')
        page.locator('button[type=submit]').click()
        page.wait_for_url(BASE+'/login?passwordReset')
        page.locator('[name=username]').fill('ana@email.com')
        page.locator('[name=password]').fill('Test-reset-password-123')
        page.locator('button[type=submit]').click()
        page.wait_for_url(BASE+'/patient/dashboard')
        # Reusing the consumed token must not change the password again.
        anon=browser.new_context()
        p=anon.new_page()
        p.goto(BASE+'/forgot-password')
        csrf=p.locator('[name=_csrf]').input_value()
        response=anon.request.post(BASE+'/reset-password',form={'token':token,'password':'Unexpected-change-123','confirmPassword':'Unexpected-change-123','_csrf':csrf},max_redirects=0)
        require(response.status==200 and 'deja folosit' in response.text(),'Consumed reset token was not rejected')
        anon.close()
        for log in ['application.log']:
            require(token not in pathlib.Path('e2e-results',log).read_text(),'Reset token appeared in application log')
    check('SMTP reset link permits one password change and new login without token logging',reset_flow)
    browser.close()
pathlib.Path('e2e-results/email-results.json').write_text(json.dumps(results,indent=2))
if not all(r['passed'] for r in results): raise SystemExit(1)
