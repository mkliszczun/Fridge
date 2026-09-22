# Uzupełnianie produktu przez AI

`POST /api/ai/products/generate` wymaga zwykłej, uwierzytelnionej sesji.
Zwraca propozycję, **nie zapisuje produktu**. Obowiązuje wspólny limit AI;
automatyczne ponowienie błędnej odpowiedzi liczy się jako jedno użycie,
ale koszt obu odpowiedzi jest rozliczany.

Przykładowe żądanie:

```json
{
  "name": "Mleko UHT 3,2%",
  "ean": "5901234123457",
  "brand": null,
  "productType": null,
  "defaultUnit": null,
  "shelfLifeAfterOpeningDays": null,
  "offData": {
    "productName": "Mleko UHT 3,2%",
    "brands": "Marka z OFF",
    "categoriesTags": ["en:dairies", "en:milks"]
  }
}
```

Wymagana jest tylko nazwa. `offData` jest opcjonalnym, ograniczonym kontekstem
z istniejącego `GET /api/off/{ean}` — nie przyjmujemy adresów URL do pobrania.
Odpowiedź zawiera `name`, `ean`, `brand`, `productType`, `defaultUnit`,
`shelfLifeAfterOpeningDays` oraz `defaultExpirationDays`.
Uzupełnione ręcznie wartości mają pierwszeństwo, również `0` dni.

`defaultExpirationDays` pochodzi z wybranej kategorii, nie jest nowym polem
produktu ani zmianą ustawień kategorii. `shelfLifeAfterOpeningDays` jest
opcjonalnym nadpisaniem dla produktu. Puste pole zachowuje dziedziczenie
z kategorii. Szacunek AI nie zastępuje etykiety i terminu konkretnego opakowania.

Mobilka: Katalog produktów → Dodaj produkt → wpisanie nazwy lub Skanuj kod produktu
→ Uzupełnij z AI → korekta danych → Zapisz produkt (`POST /api/products`).
Skanowanie i przeglądanie propozycji nie zapisują niczego. Zapis obsługuje teraz
także opcjonalne pole `brand`, istniejące już w modelu i bazie.

Błędne wejście: `400`; brak sesji: `401`; limit AI: `429`;
niepoprawna odpowiedź modelu po jednej próbie poprawy: `502`;
niedostępny dostawca lub brak konfiguracji: `503`. Błąd AI/OFF nie blokuje
ręcznego wypełnienia formularza. Nie są potrzebne nowe sekrety ani migracje.
