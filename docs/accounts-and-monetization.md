# Konta, sesje, limit AI i premium

Zmiany obejmują backend. Aplikacja mobilna musi podłączyć poniższe API. Premium jest obecnie uprawnieniem nadawanym przez administratora, bez pobierania opłat. Zakupy Apple/Google, weryfikacja transakcji, odnowienia, zwroty i SDK reklam wymagają osobnej integracji.

## API konta

Wszystkie prywatne endpointy wymagają `Authorization: Bearer <token>`.

| Endpoint | Treść JSON / wynik |
| --- | --- |
| `POST /auth/register` | `{"login":"email@example.com","password":"..."}` → 201 i para tokenów. Nowe konta zawsze FREE/USER. Login jest e-mailem, maks. 64 znaki. |
| `POST /auth/login` | Ten sam format → 200 i para tokenów; błędne dane → 401. |
| `POST /auth/refresh` | `{"refreshToken":"..."}` → nowa para; bez nagłówka ze starym JWT. |
| `POST /auth/logout` | Wylogowuje wszystkie urządzenia, unieważniając JWT i refresh tokeny; 204. |
| `POST /auth/password/forgot` | `{"email":"email@example.com"}` → 202 z identycznym komunikatem dla istniejących i nieznanych adresów. Wysyłka maks. raz na 5 minut na konto. |
| `POST /auth/password/reset` | `{"token":"...","password":"..."}` → 204. Jednorazowy link ważny 30 minut; stare sesje zostają unieważnione. |
| `GET /api/me` | `id`, `email`, `plan` (`FREE`/`PREMIUM`), `premiumUntil`, `adsEnabled`. |
| `DELETE /api/me` | `{"password":"aktualne hasło"}` → 204. Wymaga zalogowania i ponownego podania hasła. |
| `GET /api/me/ai-usage` | Data UTC, czas odnowienia limitu, limit i szacowany koszt USD, pozostały budżet, liczba użyć i ich limit (`null` dla PREMIUM), tokeny wejściowe/odczytu cache/zapisu cache/wyjściowe oraz liczba nierozliczonych rezerwacji. |
| `PUT /admin/users/{id}/premium` | ADMIN: `{"premiumUntil":"2026-10-01T00:00:00Z"}` nadaje premium; `{"premiumUntil":null}` je odbiera. Data musi być przyszła. |

Para tokenów ma postać `{"token":"JWT","refreshToken":"opaque-secret","expiresIn":900}`. Pole `token` zachowuje zgodność z dotychczasowym logowaniem. Domyślnie JWT jest ważny 15 minut, a sesja odświeżania ma nieprzedłużany termin 30 dni od logowania. Aplikacja powinna przechowywać refresh token w bezpiecznym magazynie systemowym, zapisywać nowy token po odświeżeniu i wykonywać tylko jedno odświeżanie naraz. Ponowne użycie zużytego refresh tokenu unieważnia wszystkie sesje konta. Jeśli odpowiedź odświeżania zginie w sieci, potrzebne będzie ponowne logowanie.

Nowe hasła: minimum 8 znaków i maksymalnie 72 bajty UTF-8 (ograniczenie BCrypt). Po wdrożeniu stare JWT bez wersji sesji wymagają ponownego logowania. Status konta oraz uprawnienia są odczytywane z bazy przy każdym żądaniu; premium nie jest zaufaną flagą w JWT ani w danych przesłanych przez klienta. Wygaśnięcie premium działa bez ponownego logowania.

## Katalog produktów

Zalogowany użytkownik może przeglądać i dodawać produkty. Zmiana terminu przydatności po otwarciu i usuwanie pozycji katalogu wymagają ADMIN. Uprawnienia dotyczą katalogu wspólnego, a nie własnych pozycji użytkownika w lodówce. Nawet administrator nie może usunąć produktu używanego w lodówce (409).

## Usunięcie konta

Operacja jest transakcyjna: usuwa konto, role, tokeny, reset hasła, statystyki AI, prywatne przepisy i posiłki utworzone przez użytkownika wraz ze składnikami, rezerwacjami oraz powiązaniami listy zakupów. Lodówki bez innych członków są usuwane wraz z zawartością. Współdzielone lodówki pozostają; jeśli potrzeba, jeden z pozostałych członków otrzymuje OWNER. Produkty w tych lodówkach pozostają bez identyfikatora usuniętego właściciela. Poszczególne wiersze współdzielonej listy zakupów pozostają, lecz nie zawierają powiązań do usuniętych składników. Snapshot przepisu użyty w posiłku innego domownika pozostaje bez odnośnika do prywatnego przepisu. Katalog wspólny pozostaje.

## Rozliczanie OpenAI

Konto FREE ma maksymalnie **3 użycia i 0,10 USD na dzień UTC**; przekroczenie dowolnego limitu blokuje następne użycie. Konto PREMIUM nie ma limitu liczby użyć, lecz ma limit **0,50 USD na dzień UTC**. Jedno żądanie użytkownika zużywa jedno użycie niezależnie od automatycznych ponowień OpenAI. Każda próba wywołania każdego z czterech klientów OpenAI nadal wymaga osobnej rezerwacji kosztu, także po błędnej odpowiedzi i wtedy, gdy późniejsza walidacja odrzuci wygenerowane dane. Blokada w bazie serializuje rezerwacje danego użytkownika między instancjami aplikacji.

Model domyślny: `gpt-5.6-luna`, standardowy tryb przetwarzania. Stawki z [cennika OpenAI](https://developers.openai.com/api/docs/pricing), sprawdzone 2026-09-09: 0,20 USD / mln tokenów wejściowych, 0,02 USD / mln odczytów cache, 0,25 USD / mln zapisów cache, 1,20 USD / mln tokenów wyjściowych. Zapis cache jest osobną kategorią kosztu zgodnie z [dokumentacją prompt caching](https://developers.openai.com/api/docs/guides/prompt-caching).

Żądania są ograniczone do 64 KiB serializowanego JSON i 8192 tokenów wyjściowych, wyłącznie tekst, bez narzędzi i historii po stronie dostawcy. Rezerwacja uwzględnia konserwatywny pułap 128000 tokenów wejściowych (po wyższej stawce zwykłego wejścia/zapisu cache) i maksymalną odpowiedź. Po otrzymaniu `usage` rezerwacja jest zastępowana kosztem zgłoszonych tokenów, w tym tokenów rozumowania zawartych w liczniku wyjściowym. Brak szczegółów zapisu cache oznacza ostrożne rozliczenie niebuforowanego wejścia jako zapis cache.

Timeout, błąd HTTP, brak usage lub restart procesu pozostawiają rezerwację do końca danego dnia. Dzięki temu awaria nie pozwala na bezpłatne powtarzanie zapytań; kosztem jest możliwość wcześniejszego wykorzystania limitu. Gdy nie wystarcza budżetu na całą rezerwację, API zwraca 429 i `Retry-After` do północy UTC. Spóźniona odpowiedź jest rozliczana w dniu rozpoczęcia, nawet po północy. Koszty przechowywane są jako całkowite mikro-USD, zaokrąglane w górę.

To limit szacowanego kosztu API, a nie gwarancja wysokości faktury z podatkami lub dopłatami regionalnymi. Zmiana modelu wymaga równoczesnego ustawienia jego identyfikatora i wszystkich cen. Niezgodność modelu i cennika blokuje wywołania. Dla endpointów regionalnych należy uwzględnić dopłatę w cenach. Limity kont nie zastępują globalnych limitów wydatków i ochrony rejestracji przed zakładaniem wielu kont.

## Konfiguracja wdrożenia

| Zmienna | Wartość / cel |
| --- | --- |
| `JWT_SECRET` | Silny losowy sekret, minimum 32 bajty. Nie umieszczać w repozytorium. |
| `JWT_EXPIRATION_MS` | Domyślnie `900000`. |
| `SMTP_HOST`, `SMTP_PORT` | Serwer SMTP, port domyślnie 587. |
| `SMTP_USERNAME`, `SMTP_PASSWORD`, `MAIL_FROM` | Dane wysyłki i zatwierdzony adres nadawcy. |
| `SMTP_AUTH`, `SMTP_STARTTLS_REQUIRED` | Domyślnie `true`. STARTTLS włączony. |
| `PASSWORD_RESET_URL` | Publiczny HTTPS, np. `https://api.twoja-domena.pl/reset-password.html`. Bez fragmentu i danych logowania. |
| `OPENAI_API_KEY`, `OPENAI_MODEL` | Klucz po stronie backendu, model domyślnie `gpt-5.6-luna`. |
| `OPENAI_PRICED_MODEL` | Identyfikator modelu, do którego odnoszą się poniższe ceny. |
| `OPENAI_INPUT_PRICE`, `OPENAI_CACHED_INPUT_PRICE`, `OPENAI_CACHE_WRITE_PRICE`, `OPENAI_OUTPUT_PRICE` | USD za milion tokenów: domyślnie `0.20`, `0.02`, `0.25`, `1.20`. |
| `AI_FREE_DAILY_BUDGET_USD`, `AI_FREE_DAILY_USES` | Limity FREE, domyślnie `0.10` USD i `3` użycia. |
| `AI_PREMIUM_DAILY_BUDGET_USD` | Limit PREMIUM, domyślnie `0.50` USD; liczba użyć jest nieograniczona. |

Link resetu zawiera token w fragmencie URL, który nie trafia do żądania serwera. Strona usuwa go z paska adresu, nie używa zewnętrznych zasobów i przesyła token z nowym hasłem przez POST. Backend zapisuje tylko SHA-256 losowego tokenu i nie zwraca go w odpowiedzi endpointu forgot. Wygasłe refresh tokeny i hashe resetu są czyszczone co godzinę. Wysyłkę poczty trzeba sprawdzić na środowisku testowym przed premierą; testy automatyczne nie wysyłają prawdziwych wiadomości.

Przed wdrożeniem wykonać backup i uruchomić migracje na kopii danych. V10 dodaje klucze obce właścicieli/członków do użytkowników: istniejące osierocone identyfikatory muszą być wcześniej sprawdzone i poprawione. Nie scalać wdrożenia backendu z wydaniem starego klienta bez obsługi odświeżania JWT. Testy GitHub Actions uruchamiają `./mvnw verify`, także przepływy kont na PostgreSQL po migracjach Flyway.
