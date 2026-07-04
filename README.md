# MediClinic

Platformă de management al unei clinici medicale (proiect de licență) — Spring Boot 4 / Java 21 / Thymeleaf / MySQL.

## Cerințe

- **Java 21 (JDK)** — verifică cu `java -version`.
- **XAMPP** (sau orice server MySQL/MariaDB 8+) cu modulul MySQL pornit.
- Nu este nevoie de Maven instalat separat — proiectul include wrapper-ul (`mvnw` / `mvnw.cmd`).

## Pornire pe alt calculator (ex. calculatorul profesorului) cu XAMPP

### 1. Pornește MySQL din XAMPP Control Panel

Deschide XAMPP Control Panel și apasă **Start** pe modulul **MySQL**.

### 2. Creează baza de date cu `utf8mb4` (pas obligatoriu)

Deschide [phpMyAdmin](http://localhost/phpmyadmin), mergi la tab-ul **SQL** și rulează:

```sql
CREATE DATABASE IF NOT EXISTS clinic
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;
```

> **De ce contează:** dacă baza de date e creată cu charset-ul implicit din phpMyAdmin (deseori `latin1` sau `utf8` pe 3 octeți), diacriticele românești (ă, â, î, ș, ț) se vor afișa incorect în aplicație — indiferent de cod. `utf8mb4` este singurul charset MySQL care poate stoca corect toate caracterele Unicode folosite de aplicație.

### 3. Verifică datele de conectare

`src/main/resources/application.properties` este configurat implicit pentru XAMPP:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/clinic?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true&useUnicode=true&characterEncoding=UTF-8&connectionCollation=utf8mb4_unicode_ci
spring.datasource.username=root
spring.datasource.password=
```

Acestea sunt exact setările implicite XAMPP (`root`, fără parolă, port `3306`). Dacă pe calculatorul profesorului contul `root` are parolă, actualizează `spring.datasource.password`.

### 4. Pornește aplicația

Din rădăcina proiectului (Windows):

```bat
mvnw.cmd spring-boot:run
```

Sau construiește un `.jar` și rulează-l direct (util pentru o demonstrație, fără a depinde de Maven):

```bat
mvnw.cmd clean package -DskipTests
java -jar target\clinic-1.0.0.jar
```

La prima pornire, Hibernate creează automat tabelele (`ddl-auto=update`), iar aplicația populează automat baza de date cu date demo (doctori, pacienți, programări).

Aplicația pornește pe **http://localhost:8082**.

## Conturi demo (create automat la prima pornire)

| Rol      | Email                | Parolă      |
|----------|-----------------------|-------------|
| Admin    | admin@clinic.com      | admin123    |
| Doctor   | doctor1@clinic.com    | doctor123   |
| Pacient  | mihai@email.com       | patient123  |

## Probleme frecvente

- **Diacritice afișate greșit (`?`, `�`, `Ã®`, etc.)** — baza de date nu a fost creată cu `utf8mb4` (vezi pasul 2). Șterge baza de date existentă și recreeaz-o corect, apoi repornește aplicația ca Hibernate să recreeze tabelele cu charset-ul corect.
- **Eroare de conectare la MySQL** — verifică dacă modulul MySQL este pornit în XAMPP și dacă portul `3306` nu este ocupat de o altă instanță MySQL.
- **Port 8082 ocupat** — schimbă `server.port` în `application.properties` sau pornește cu `--server.port=ALTPORT`.
- **Prima rulare durează mult / are nevoie de internet** — `mvnw` descarcă dependențele Maven o singură dată; rulările ulterioare folosesc cache-ul local.

## Funcționalități opționale (dezactivate implicit)

Stripe (plăți), DeepSeek AI (analiză simptome), email și SMS (Twilio) sunt dezactivate implicit și nu necesită configurare pentru a rula/testa aplicația. Se pot activa prin variabilele de mediu din `application.properties` (`STRIPE_SECRET_KEY`, `DEEPSEEK_API_KEY`, `MAIL_ENABLED`, `SMS_ENABLED`).

## Plăți Stripe în mod local (fără Docker)

Fișierul `.env` din rădăcina proiectului conține deja o cheie Stripe de test. `application.properties` o încarcă automat (`spring.config.import=optional:file:.env[.properties]`), deci Stripe funcționează direct la `mvnw spring-boot:run` sau `java -jar`, fără Docker.

- **Localhost nu este o problemă** pentru plăți — API-ul Stripe este apelat de server (ieșire HTTPS către `api.stripe.com`), nu invers, deci merge din orice rețea cu acces la internet.
- Testează cu cardul de test Stripe: `4242 4242 4242 4242`, orice dată viitoare, orice CVC.
- **Webhook-ul Stripe** (`/stripe/webhook`) *nu* este necesar pentru demonstrație — confirmarea plății și rambursările sunt făcute direct de server (nu depind de un callback extern către localhost). Webhook-ul e doar un mecanism de rezervă pentru evenimente asincrone; dacă vrei totuși să-l testezi local, folosește [Stripe CLI](https://stripe.com/docs/stripe-cli): `stripe listen --forward-to localhost:8082/stripe/webhook`, care îți dă un `STRIPE_WEBHOOK_SECRET` temporar de pus în `.env`.
