"""Business flows against disposable localhost MySQL fixtures only; no real payments/messages."""
import datetime
import json
import pathlib
import re
from playwright.sync_api import sync_playwright

BASE = 'http://127.0.0.1:8082'
results = []

def check(name, fn):
    try:
        fn()
        results.append({'name': name, 'passed': True})
    except Exception as e:
        results.append({'name': name, 'passed': False, 'error': str(e)[:1500]})
    print(json.dumps(results[-1]), flush=True)

def require(value, message):
    assert value, message

with sync_playwright() as pw:
    browser = pw.chromium.launch()
    def login(email, password, role):
        c = browser.new_context()
        p = c.new_page()
        p.goto(BASE + '/login')
        p.locator('[name=username]').fill(email)
        p.locator('[name=password]').fill(password)
        p.locator('button[type=submit]').click()
        p.wait_for_url(BASE + '/' + role + '/dashboard')
        token = p.locator('#csrf-holder').get_attribute('data-token')
        header = p.locator('#csrf-holder').get_attribute('data-header')
        return c, p, {header: token}
    patient, page, headers = login('mihai@email.com', 'patient123', 'patient')
    other, other_page, other_headers = login('ana@email.com', 'patient123', 'patient')
    doctor, doctor_page, doctor_headers = login('doctor1@clinic.com', 'doctor123', 'doctor')
    def post(c, path, data, h):
        return c.request.post(BASE + path, form=data, headers=h, max_redirects=0)
    def appointment(i):
        r = patient.request.get(BASE + f'/api/appointments/{i}')
        require(r.status == 200, f'Appointment read HTTP {r.status}')
        return r.json()
    doctors = patient.request.get(BASE + '/api/doctors').json()
    doctor_id = next(d['id'] for d in doctors if d.get('fullName') == 'Alexandru Popescu')
    day = datetime.date.today() + datetime.timedelta(days=100)
    while day.weekday() > 4:
        day += datetime.timedelta(days=1)
    slots = patient.request.get(BASE + f'/api/slots/{doctor_id}/{day}').json()
    require(len(slots) >= 2, 'Need two disposable future slots')
    def book(slot):
        r = post(patient, '/patient/book', {'doctorId':str(doctor_id), 'appointmentDate':f'{day}T{slot}', 'notes':'Routine test appointment'}, headers)
        require(r.status == 302, f'Booking HTTP {r.status}')
        m = re.search(r'/appointments/(\d+)/pay', r.headers.get('location',''))
        require(m is not None, 'Missing payment redirect')
        return int(m.group(1))
    def concurrent_booking():
        import concurrent.futures, threading, urllib.request, urllib.parse, urllib.error
        barrier=threading.Barrier(2)
        class NoRedirect(urllib.request.HTTPRedirectHandler):
            def redirect_request(self, req, fp, code, msg, headers, newurl): return None
        def submit(args):
            cookies, csrf_headers=args
            body=urllib.parse.urlencode({'doctorId':str(doctor_id),'appointmentDate':f'{day}T{slots[-1]}','notes':'Synthetic race check'}).encode()
            h={**csrf_headers,'Cookie':cookies,'Content-Type':'application/x-www-form-urlencoded'}
            req=urllib.request.Request(BASE+'/patient/book',data=body,headers=h)
            barrier.wait(timeout=10)
            try:
                r=urllib.request.build_opener(NoRedirect()).open(req,timeout=90)
                return r.status, r.headers.get('Location','')
            except urllib.error.HTTPError as e:
                return e.code, e.headers.get('Location','')
        args=[('; '.join(c['name']+'='+c['value'] for c in ctx.cookies()),h)
              for ctx,h in [(patient,headers),(other,other_headers)]]
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
            responses=list(pool.map(submit,args))
        wins=[location for status,location in responses if status==302 and re.search(r'/appointments/\d+/pay',location)]
        require(len(wins)==1, 'Concurrent requests must create exactly one booking: '+str(responses))
        require(all(status==302 for status,_ in responses),'Conflict must return a recoverable redirect')
        require(slots[-1] not in patient.request.get(BASE+f'/api/slots/{doctor_id}/{day}').json(),'Race winner must reserve slot')
    check('simultaneous patients cannot reserve the same slot', concurrent_booking)
    aid = book(slots[0])
    check('new booking awaits payment', lambda: require(appointment(aid)['status'] == 'PENDING_PAYMENT','Wrong initial state'))
    check('pending payment reserves advertised slot', lambda: require(slots[0] not in patient.request.get(BASE + f'/api/slots/{doctor_id}/{day}').json(), 'Reserved slot is still advertised'))
    def duplicate():
        before = len(patient.request.get(BASE + '/api/appointments').json())
        post(patient,'/patient/book',{'doctorId':str(doctor_id),'appointmentDate':f'{day}T{slots[0]}'},headers)
        require(len(patient.request.get(BASE + '/api/appointments').json()) == before, 'Duplicate appointment created')
    check('duplicate booking does not create another appointment', duplicate)
    check('other patient cannot pay this booking', lambda: require(post(other,'/apply-payment-coupon',{'appointmentId':str(aid),'couponCode':'smoke-coupon'},other_headers).status == 403,'Other patient payment allowed'))
    check('invalid coupon rejected', lambda: require(post(patient,'/apply-payment-coupon',{'appointmentId':str(aid),'couponCode':'wrong'},headers).status == 400,'Invalid coupon accepted'))
    check('disabled Stripe rejects intent without completing payment', lambda: require(post(patient,'/create-payment-intent',{'appointmentId':str(aid)},headers).status == 503 and appointment(aid)['status']=='PENDING_PAYMENT','Disabled payment changed state'))
    def coupon():
        r=post(patient,'/apply-payment-coupon',{'appointmentId':str(aid),'couponCode':'smoke-coupon'},headers)
        require(r.status==200 and appointment(aid)['status']=='PENDING','Coupon failed')
    check('test coupon advances booking to pending',coupon)
    check('repeated coupon rejected', lambda: require(post(patient,'/apply-payment-coupon',{'appointmentId':str(aid),'couponCode':'smoke-coupon'},headers).status==409,'Duplicate payment accepted'))
    def confirm():
        post(doctor,f'/doctor/confirm/{aid}',{},doctor_headers)
        require(appointment(aid)['status']=='CONFIRMED','Confirmation failed')
    check('assigned doctor confirms paid booking',confirm)
    def cancel():
        post(patient,f'/patient/cancel/{aid}',{'cancelReason':'Test cleanup'},headers)
        require(appointment(aid)['status']=='CANCELLED','Cancellation failed')
    check('owner cancels confirmed booking',cancel)
    second = book(slots[1])
    post(patient,f'/patient/cancel/{second}',{},headers)
    def cancelled_coupon():
        r=post(patient,'/apply-payment-coupon',{'appointmentId':str(second),'couponCode':'smoke-coupon'},headers)
        require(r.status==409 and appointment(second)['status']=='CANCELLED','Coupon revived a cancelled booking')
    check('cancelled unpaid booking cannot be paid by coupon',cancelled_coupon)
    check('cancelled booking cannot create payment intent',lambda: require(post(patient,'/create-payment-intent',{'appointmentId':str(second)},headers).status==409,'Cancelled intent was not rejected before provider access'))
    def messages():
        page.goto(BASE + '/patient/messages')
        options=page.locator('select[name=receiverId] option').evaluate_all('(els)=>els.map(x=>({value:x.value,text:x.textContent}))')
        uid=next(x['value'] for x in options if 'Alexandru' in x['text'] and 'Popescu' in x['text'])
        subject='E2E private fixture message'
        r=post(patient,'/patient/messages/send',{'receiverId':uid,'subject':subject,'body':'Test request only'},headers)
        require(r.status==302,'Send failed')
        doctor_page.goto(BASE + '/doctor/messages')
        link=doctor_page.locator('[data-href^="/doctor/messages/"]').filter(has_text=subject).first
        href=link.get_attribute('data-href')
        require(href is not None,'Doctor inbox missing message')
        mid=int(href.rsplit('/',1)[1])
        require(other.request.get(BASE + f'/patient/messages/{mid}').status==403,'Unrelated patient read private message')
        require(doctor.request.get(BASE + href).status==200,'Doctor cannot read message')
        r=post(doctor,'/doctor/messages/reply',{'parentId':str(mid),'body':'E2E fixture reply'},doctor_headers)
        require(r.status==302,'Reply failed')
        page.goto(BASE + '/patient/messages')
        reply=page.locator('[data-href^="/patient/messages/"]').filter(has_text='Re: '+subject).first
        reply.click()
        require('E2E fixture reply' in page.locator('body').inner_text(),'Reply not delivered')
    check('internal message delivery reply and third-party denial',messages)
    browser.close()
pathlib.Path('e2e-results/business-results.json').write_text(json.dumps(results,indent=2))
print(f'{sum(r["passed"] for r in results)}/{len(results)} business checks passed')
if not all(r['passed'] for r in results):
    raise SystemExit(1)
