"""Real MySQL races with a synthetic coupon; never calls an external payment provider."""
import concurrent.futures, datetime, re, threading, urllib.request, urllib.parse, urllib.error
from playwright.sync_api import sync_playwright
BASE='http://127.0.0.1:8082'
with sync_playwright() as pw:
    browser=pw.chromium.launch(); context=browser.new_context();page=context.new_page()
    page.goto(BASE+'/login');page.locator('[name=username]').fill('mihai@email.com');page.locator('[name=password]').fill('patient123');page.locator('button[type=submit]').click();page.wait_for_url('**/patient/dashboard')
    headers={page.locator('#csrf-holder').get_attribute('data-header'):page.locator('#csrf-holder').get_attribute('data-token')}
    cookies='; '.join(c['name']+'='+c['value'] for c in context.cookies())
    doctor=context.request.get(BASE+'/api/doctors').json()[0]['id']
    day=datetime.date.today()+datetime.timedelta(days=150)
    while day.weekday()>4:day+=datetime.timedelta(days=1)
    slots=context.request.get(BASE+f'/api/slots/{doctor}/{day}').json();assert len(slots)>=2
    def book(slot):
        r=context.request.post(BASE+'/patient/book',headers=headers,form={'doctorId':str(doctor),'appointmentDate':f'{day}T{slot}'},max_redirects=0)
        return int(re.search(r'/appointments/(\d+)/pay',r.headers['location']).group(1))
    def race(jobs):
        barrier=threading.Barrier(2)
        class NoRedirect(urllib.request.HTTPRedirectHandler):
            def redirect_request(self,*args,**kwargs):return None
        def send(job):
            path,data=job;req=urllib.request.Request(BASE+path,data=urllib.parse.urlencode(data).encode(),headers={**headers,'Cookie':cookies,'Content-Type':'application/x-www-form-urlencoded'})
            barrier.wait(timeout=10)
            try:
                with urllib.request.build_opener(NoRedirect()).open(req,timeout=90) as r:return r.status
            except urllib.error.HTTPError as e:return e.code
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:return list(pool.map(send,jobs))
    first=book(slots[0]);coupon=('/apply-payment-coupon',{'appointmentId':str(first),'couponCode':'smoke-coupon'})
    assert sorted(race([coupon,coupon]))==[200,409], 'Duplicate payment must be rejected under contention'
    second=book(slots[1]);coupon=('/apply-payment-coupon',{'appointmentId':str(second),'couponCode':'smoke-coupon'})
    results=race([coupon,(f'/patient/cancel/{second}',{})]);assert results[0] in (200,409) and results[1]==302,results
    appointment=context.request.get(BASE+f'/api/appointments/{second}').json()
    assert appointment['status']=='CANCELLED','Payment must not revive a concurrently cancelled appointment'
    print('PASS MySQL races: duplicate coupon accepted once; concurrent cancellation remains terminal')
    browser.close()
