# Potwierdzanie e-maila — kontrakt dla mobilki

## Warunki wdrożenia

V15 oznacza **wszystkie istniejące konta jako niepotwierdzone**, zwiększa wersję sesji
i usuwa stare refresh tokeny. Zachowuje konta, dane aplikacji, role i premium.
Każdy użytkownik musi ponownie podać hasło i potwierdzić adres.
**Przed produkcją konieczne są nowa mobilka, konfiguracja SMTP i test prawdziwej wiadomości
na środowisku testowym.** Stary klient oczekujący JWT od razu po rejestracji/logowaniu nie wystarczy.
Wdrożenie musi objąć wszystkie instancje backendu: stara wersja nie egzekwuje potwierdzenia adresu.

## API

Endpointy `/auth/email/*` nie wymagają JWT. Tymczasowy token należy przesyłać wyłącznie w JSON,
nie jako `Authorization: Bearer`. Na publiczne endpointy logowania/weryfikacji nie wysyłać starego JWT.

1. `POST /auth/register` z `{"login":"email@example.com","password":"..."}` → **202**:

   ```json
   {
     "verificationToken":"opaque-random-secret",
     "expiresAt":"2026-09-17T12:30:00Z",
     "email":"email@example.com",
     "emailRequired":false
   }
   ```

   Konto jeszcze nie istnieje, wiadomość jeszcze nie jest wysyłana. Konto powstanie jako FREE/USER.
   Login: e-mail, maks. 64 znaki; hasło: min. 8 znaków, maks. 72 bajty UTF-8.

2. `POST /auth/login` z dotychczasowym loginem/e-mailem i hasłem → **200** z parą tokenów
   dla potwierdzonego konta; **202** z powyższym procesem dla niepotwierdzonego; **401** przy
   nieprawidłowych danych. Nieprawidłowy stary adres daje `email=null`, `emailRequired=true`.
   Mobilka pozwala poprawić adres również wtedy, gdy ma poprawną składnię.

3. `POST /auth/email/send` z `{"verificationToken":"...","email":"correct@example.com"}` → **200**:

   ```json
   {"codeExpiresAt":"2026-09-17T12:10:00Z","resendAvailableAt":"2026-09-17T12:01:00Z"}
   ```

   `email` jest opcjonalny; pominięcie oznacza adres bieżącego procesu. Ten sam endpoint ponawia wysyłkę.
   Udana wysyłka zastępuje poprzedni kod **tego procesu**, również przy zmianie adresu.
   Adres istniejącego konta zmieni się dopiero po potwierdzeniu; dotychczasowy login pozostaje ważny.
   Przy nowej rejestracji adres musi odpowiadać loginowi: korekta wymaga nowej rejestracji,
   żeby nie można było zająć cudzego loginu potwierdzeniem własnej skrzynki.
   Adresy nowych procesów normalizujemy do małych liter. Konflikt z e-mailem **lub loginem**
   innego konta (bez uwzględniania wielkości liter) → **409**; bez automatycznego scalania kont.

4. `POST /auth/email/verify` z `{"verificationToken":"...","code":"012345"}` → **200**:

   ```json
   {"token":"JWT","refreshToken":"opaque-refresh-secret","expiresIn":900}
   ```

   Kod to **string**, dokładnie sześć cyfr, z zachowaniem zera na początku.
   Mobilka usuwa token tymczasowy i zapisuje zwykłą parę tokenów. Nie prosi ponownie o hasło.
   Potwierdzenie/utworzenie konta, zużycie kodu i wydanie sesji są atomowe.
   Powtórzenie nie wydaje kolejnej sesji. Po utracie odpowiedzi należy się zalogować.

## Błędy i limity

- **400**: zły format, błędny/wygasły kod albo wygasły/zużyty proces. Pole `error` rozróżnia
  błąd kodu od procesu. Przy błędzie procesu ponownie rejestracja lub logowanie.
- **409**: adres zajęty, również jeśli zajęto go między wysłaniem a wpisaniem kodu.
- **429**: limit; respektować `Retry-After` (sekundy).
- **503**: poczta niedostępna; `error` ma wartość
  `Email delivery unavailable; retry with the same verification token`.
  Można ponowić z tym samym, niewygasłym tokenem. Nie odblokowuje to konta.
  Nieudana wysyłka zachowuje poprzedni kod i nie pobiera limitu udanych wysyłek.
  Timeout SMTP może oznaczać niepewne dostarczenie; obowiązuje kod ostatniej wysyłki
  zatwierdzonej w bazie, niekoniecznie ostatniej otrzymanej wiadomości.

Token: 30 minut. Kod: 10 minut, nie dłużej niż proces.
Minimum 60 sekund między wysyłkami; maks. 5 wysyłek w **ruchomym oknie godzinnym** na konto lub adres.
Maks. 5 błędnych prób w **ruchomym oknie 15 minut**, wspólnie dla kolejnych procesów i ponowień.
Po wyczerpaniu prób również poprawny kod czeka na odnowienie limitu.
Nowy token, kod ani zmiana adresu nie zerują limitu istniejącego konta.

Osobne limity IP przed walidacją: domyślnie 10 żądań wysyłki i 20 potwierdzeń / 15 minut.
Zmienne: `AUTH_EMAIL_SENDS_PER_15_MINUTES`, `AUTH_EMAIL_VERIFICATIONS_PER_15_MINUTES`.
Zasady proxy/IPv6 jak w [ochronie nadużyć](abuse-and-inventory-safety.md).
Limity konta/adresu działają także przy `abuse.enabled=false`.

## Bezpieczeństwo i utrzymanie

- Token: 32 losowe bajty, w bazie SHA-256. Kod: generator kryptograficzny, w bazie BCrypt.
  Kod jest przypisany do procesu i jego aktualnego adresu; nie jest JWT.
- Oczekująca rejestracja przechowuje wyłącznie hash hasła. Proces istniejącego użytkownika
  jest przypisany do ID konta i wersji uwierzytelnienia. Zmiana hasła unieważnia proces.
- Potwierdzenie istniejącego konta unieważnia jego inne procesy, poprzednie sesje i link
  resetu wysłany na stary adres. Reset hasła nadal używa linku i **nie potwierdza adresu**.
- Bramka JWT, refresh, wydawanie sesji i budżet AI niezależnie wymagają potwierdzonego konta.
- Operacje weryfikacji serializuje blokada `email` w bazie, wspólna dla instancji.
  Blokujemy też rekord istniejącego użytkownika, aby uniknąć wyścigu ze zmianą hasła.
  W MVP blokada obejmuje SMTP z ograniczonymi timeoutami: powolna poczta może opóźnić
  inne operacje weryfikacji. Nie blokuje operacji na zapasach innych użytkowników.
- Liczniki zawierają hashe adresów/identyfikatorów — pseudonimizację, nie anonimizację.
  Wygasłe zdarzenia usuwamy przy weryfikacji, procesy w godzinowym sprzątaniu.
  Ważność jest sprawdzana przy każdym użyciu, niezależnie od uruchomienia sprzątania.
- Nie logować kodów, haseł, tokenów ani treści wiadomości; nie włączać debugowania SMTP
  ani logowania wartości parametrów SQL przy konfiguracji środowiska.

## SMTP i testy

Weryfikacja i reset hasła używają wspólnie `MAIL_FROM`, `SMTP_HOST`, `SMTP_PORT`,
`SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_AUTH`, `SMTP_STARTTLS_REQUIRED`.
Istniejące timeouty połączenia/odczytu/zapisu wynoszą po 5 sekund.
Tylko reset hasła wymaga `PASSWORD_RESET_URL` (HTTPS). Dostawca SMTP pozostaje do wyboru.

`./mvnw verify` przechwytuje wiadomości, bez prawdziwej wysyłki. Z `POSTGRES_TEST_URL` uruchamia
też `PostgresEmailVerificationTest` i `PostgresEmailVerificationMigrationTest` (V14 → V15
z istniejącymi kontami). Obecne CI ma PostgreSQL i uruchamia te testy automatycznie.
**Nie podawać adresu produkcyjnej bazy ani bazy z ważnymi danymi: testy zapisują i usuwają dane.**
