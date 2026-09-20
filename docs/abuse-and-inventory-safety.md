# Ochrona nadużyć i spójność zapasów

## Limity dostępu

Wszystkie instancje korzystają z liczników i krótkich blokad w PostgreSQL. Restart maszyny nie zeruje liczników.
Liczone są także nieudane próby (przed walidacją i uwierzytelnieniem). Odrzucenie zwraca HTTP 429 i `Retry-After`.

| Zmienna środowiskowa | Wartość domyślna | Zakres |
| --- | --- | --- |
| `AUTH_LOGIN_PER_15_MINUTES` | 20 | logowanie z jednego IP |
| `AUTH_REGISTRATIONS_PER_HOUR` | 3 | rejestracja z jednego IP |
| `AUTH_GLOBAL_REGISTRATIONS_PER_DAY` | 100 | rejestracja we wszystkich instancjach |
| `AUTH_PASSWORD_REQUESTS_PER_15_MINUTES` | 5 | osobno żądanie i wykonanie resetu, na IP |
| `AUTH_REFRESH_PER_15_MINUTES` | 60 | odświeżanie sesji z jednego IP |
| `AUTH_EMAIL_SENDS_PER_15_MINUTES` | 10 | wysyłka kodu weryfikacji z jednego IP |
| `AUTH_EMAIL_VERIFICATIONS_PER_15_MINUTES` | 20 | potwierdzanie kodu z jednego IP |

Okno zaczyna się przy pierwszej próbie i wygasa po wskazanym czasie. IPv6 jest grupowany po prefiksie /64.
Weryfikacja e-mail ma dodatkowo niezależne, ruchome limity konta/adresu opisane w [kontrakcie weryfikacji](email-verification.md).
Przechowujemy skrót IP, nie surowy adres; wygasłe wpisy są usuwane przy kolejnych limitowanych żądaniach.
Skrót jest pseudonimizacją, nie gwarancją anonimowości. Użytkownicy za wspólnym NAT dzielą limit.

Domyślnie używany jest adres połączenia; `X-Forwarded-For` jest ignorowany.
`fly.toml` włącza `ABUSE_TRUST_FLY_PROXY=true`: używamy wtedy `Fly-Client-IP`, a brak/poprawność nagłówka jest walidowana.
To ustawienie jest bezpieczne **wyłącznie**, gdy ruch publiczny przechodzi przez handler HTTP Fly,
a port aplikacji nie jest bezpośrednio dostępny niezaufanym klientom. Nie włączać za innym proxy bez zmiany konfiguracji.
Źródło: [nagłówki Fly Proxy](https://www.fly.io/docs/networking/request-headers/).

## Budżet AI

- Dotychczasowe limity FREE/PREMIUM pozostają bez zmian; retry nie zwiększa liczby użyć, ale jego koszt nadal się liczy.
- `AI_GLOBAL_DAILY_BUDGET_USD=10.00`: dodatkowy, wspólny limit dzienny dla całej aplikacji, od północy UTC.
- Przed każdym wywołaniem dostawcy rezerwowany jest konserwatywny koszt. Po prawidłowym `usage` rozliczany jest koszt rzeczywistego zużycia według skonfigurowanego cennika.
- Niepewne wywołania (timeout, brak usage) zostawiają rezerwację. Dostęp może zostać wstrzymany wcześniej niż wynikałoby z faktury dostawcy.
- Usunięcie konta nie kasuje globalnego kosztu. Odpowiedź otrzymana po północy jest rozliczana w dniu rozpoczęcia wywołania.
- Przy 80% budżetu wraz z bieżącymi rezerwacjami pojawia się `AI_GLOBAL_BUDGET_WARNING` w logach (raz dziennie). Nie jest to automatyczne powiadomienie e-mail; regułę alertu w monitoringu trzeba skonfigurować osobno.
- `AI_ENABLED=false` wyłącza nowe wywołania AI (HTTP 503); zmiana konfiguracji wymaga restartu/wdrożenia. Nie przerywa zapytań już wysłanych.
- Ochrona ogranicza skutki wielu kont, ale nie zastępuje weryfikacji e-mail, ochrony antybotowej ani limitów u dostawcy.

## Zapasy, rezerwacje i zakupy

- Krótkie transakcje zapisujące zapasy, posiłki, rezerwacje i listę zakupów blokują jeden rekord lodówki.
  Różne lodówki mogą być modyfikowane równolegle. Wywołania AI nie trzymają tej blokady.
- Po odpowiedzi AI backend ponownie odczytuje posiłki, zapasy i rezerwacje pod blokadą. Zmieniony/usunięty/wykonany posiłek powoduje konflikt zamiast zapisu starej propozycji.
- Zmniejszenie ilości produktu ogranicza jego aktywne rezerwacje do faktycznego zapasu, z pierwszeństwem wcześniejszych posiłków; archiwizacja zwalnia je. Zwiększenie ilości nie odtwarza automatycznie rezerwacji.
- Zmiana porcji lub przepisu zwalnia rezerwacje i usuwa wkład danego posiłku do listy zakupów.
  Wpisy ręczne i wkłady innych posiłków pozostają. Rezerwacje i propozycję zakupów trzeba wygenerować ponownie.
- Zmiana porcji nadaje nowe ID składnikom snapshotu; mobilka powinna użyć odpowiedzi PUT lub ponownie pobrać posiłek.
  Dzięki temu stary import zakupów lub stara propozycja rezerwacji nie może zostać przyjęta dla nowych porcji.
- Sama zmiana daty zachowuje rezerwacje. Usunięcie/wykonanie posiłku usuwa również jego wkład zakupowy.
- Migracja V14 zachowuje stare niejednoznaczne wpisy bez ilości jako ręczne, aby nie usunąć potencjalnych dopisków użytkownika.

## Przeterminowane produkty

AI nie otrzymuje produktów z `effectiveExpireAt` wcześniejszym niż dzisiejsza data UTC ani produktów zarchiwizowanych.
Dotyczy planowania z lodówką, dopasowania zakupów i automatycznych rezerwacji.
Istniejąca rezerwacja przeterminowanego produktu nie pomniejsza braków na liście zakupów.
Produkty ważne do dziś oraz bez określonej daty pozostają kandydatami. Na zwykłej liście lodówki nadal widać produkty przeterminowane.
To filtr według daty, nie gwarancja bezpieczeństwa żywności; nie wprowadzamy automatycznej oceny warunków przechowywania.

## Weryfikacja

`./mvnw verify` uruchamia testy H2. Gdy ustawione jest `POSTGRES_TEST_URL`, CI uruchamia również
`PostgresAccountSecurityTest`, `PostgresAbuseProtectionTest`, `PostgresInventoryConsistencyTest`
oraz `PostgresGuardMigrationTest` (aktualizacja z V12 z istniejącymi danymi)
na testowym PostgreSQL, z rzeczywistymi migracjami Flyway i `ddl-auto=validate`.
Nigdy nie podawać tutaj adresu produkcyjnej bazy: testy zapisują i usuwają dane testowe.
