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

La prima pornire pe o bază goală, Flyway aplică migrarea V1, apoi Hibernate validează schema (`ddl-auto=validate`). Datele demo sunt create numai dacă setezi `DEMO_DATA_ENABLED=true` pe o bază locală de demonstrație. Pentru o bază existentă, urmează [procedura de adoptare](docs/database-migrations.md) înainte de pornire.

Aplicația pornește pe **http://localhost:8082**.

## Conturi demo (numai cu DEMO_DATA_ENABLED=true)

| Rol      | Email                | Parolă      |
|----------|-----------------------|-------------|
| Admin    | admin@clinic.com      | admin123    |
| Doctor   | doctor1@clinic.com    | doctor123   |
| Pacient  | mihai@email.com       | patient123  |

## Probleme frecvente

- **Diacritice afișate greșit (`?`, `�`, `Ã®`, etc.)** — baza de date nu a fost creată cu `utf8mb4` (vezi pasul 2). Fă un backup și verifică setările bazei și ale coloanelor înainte de conversia charset-ului. Nu șterge datele existente.
- **Eroare de conectare la MySQL** — verifică dacă modulul MySQL este pornit în XAMPP și dacă portul `3306` nu este ocupat de o altă instanță MySQL.
- **Port 8082 ocupat** — schimbă `server.port` în `application.properties` sau pornește cu `--server.port=ALTPORT`.
- **Prima rulare durează mult / are nevoie de internet** — `mvnw` descarcă dependențele Maven o singură dată; rulările ulterioare folosesc cache-ul local.

## Integrări externe

Stripe (plăți), DeepSeek AI (analiză simptome), email și SMS (Twilio) sunt neconfigurate sau dezactivate implicit și nu necesită configurare pentru a rula/testa aplicația. Se pot activa prin variabilele de mediu din `application.properties` (`STRIPE_SECRET_KEY`, `DEEPSEEK_API_KEY`, `MAIL_ENABLED`, `SMS_ENABLED`).

## Configurare locală și Docker

Copiază `.env.example` în `.env`. Fișierul `.env` nu este inclus în Git și nu
conține chei preconfigurate. Completează `MYSQL_PASSWORD` și
`MYSQL_ROOT_PASSWORD` înainte de `docker compose up --build`.

Ollama este activat implicit (`OLLAMA_ENABLED=true`). Pentru Docker, descarcă
modelul după pornirea serviciului: `docker compose exec ollama ollama pull qwen3:1.7b`.
Pentru rulare fără Docker, pornește Ollama separat și descarcă același model,
sau setează `OLLAMA_ENABLED=false`. În acest caz, recomandările de specialitate
folosesc fallback-ul existent, iar `/api/ai/chat` răspunde cu 503.
Testele folosesc un provider simulat și nu necesită Ollama.

Stripe, DeepSeek, email și SMS necesită configurare separată. Pentru Stripe,
setează cheile de test în `.env`. Pentru evenimente asincrone locale, folosește
Stripe CLI cu `stripe listen --forward-to localhost:8082/stripe/webhook` și pune
secretul returnat în `STRIPE_WEBHOOK_SECRET`.

MySQL folosește Flyway și validare Hibernate în toate configurațiile MySQL.
Vezi [migrarea bazelor existente](docs/database-migrations.md) și
[ordinea de integrare a PR-urilor AI](docs/ai-integration-plan.md).

Demo data is disabled by default. Enable `DEMO_DATA_ENABLED=true` only on a disposable local database. Turning it off does not delete demo accounts already created; review existing accounts before deployment.

Docker publishes MySQL, Ollama, and phpMyAdmin only on localhost. The application remains on port 8082. The public health endpoint exposes status only.

## Operational readiness

For first-administrator provisioning, secret configuration, backup/restore tools,
and provider acceptance tests, see [Operational readiness](docs/operational-readiness.md).
