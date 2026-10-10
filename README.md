# Mój Pies

Przeglądarkowa gra w opiekę nad psem: karmienie, picie, aportowanie, spacery w parku,
kąpiel z otrzepywaniem, sen na posłaniu, nauka sztuczek, więź, poziomy, sklep i akcesoria.

Główną postacią docelowo będzie **prawdziwy pies odwzorowany ze zdjęć**. Na razie gra używa
tymczasowej postaci w stylu 2D cartoon, a cała grafika psa jest **wymienną skórką**.
Podmiana wyglądu nie wymaga zmian w kodzie i nie resetuje imienia, postępów, więzi ani ekwipunku.

## Uruchomienie

Otwórz `index.html` w przeglądarce (działa także bezpośrednio z dysku) albo uruchom
lokalny serwer: `npm run serve` i wejdź na http://localhost:8080.
Postęp zapisuje się automatycznie w przeglądarce.

## Jak grać

* **Dotknij psa**, żeby go pogłaskać. Głowa, grzbiet, brzuch i ogon dają różne reakcje,
  a przeciągnięcie palcem to dłuższe głaskanie.
* **Dotknij podłogi**, a pies tam podejdzie. **Dotknij piłki**, żeby ją rzucić.
* Przyciski: Karm, Woda, Piłka (aport), Smakołyk, Spacer, Kąpiel, Kość, Do mnie!
* Menu: Sztuczki (komendy i trening ze smakołykiem), Plecak, Sklep, Wygląd, Profil.

Pies ma własne potrzeby i zachowuje się po psiemu: sam przynosi piłkę, czeka i skomle
pod drzwiami, gdy chce na spacer, prosi przy pustej misce, szczeka na listonosza, goni
własny ogon, drapie się łapą, wita się z radością po powrocie i otrzepuje po kąpieli.

## Wygląd psa: najważniejsze

* `assets/skins/<id>/skin.js`: konfiguracja skórki (grafiki, punkty obrotu, punkty zaczepienia, proporcje).
* `assets/skins/manifest.js`: lista skórek wczytywanych przez grę.
* `docs/WYGLAD-PSA.md`: **pełna instrukcja dodania prawdziwego psa**: jakie zdjęcia, jakie pliki, które parametry.
* `assets/skins/_szablon-prawdziwy-pies/`: szablon do skopiowania.
* W grze: **Wygląd → Studio wyglądu**. Pokazuje każdą animację, szkielet, strefy dotyku i raport konfiguracji.

Wbudowane skórki: `cartoon-beagle` (domyślna), `cartoon-husky` (inne proporcje, te same animacje)
i `demo-klatki` (przykład formatu z klatek PNG, czyli dla serii zdjęć).

## Struktura

```
index.html, css/style.css
js/core/       narzędzia (matematyka, zapis w localStorage, zdarzenia)
js/dog/        logika psa: kontrakt stanów, logiczne ciało i strefy, zadania, mózg
js/game/       dane, zapis gry, klasa Game (potrzeby, świat, akcje, nagrody)
js/render/     wygląd: rejestr skórek, renderery (warstwy / klatki), animacje, scena
js/ui/         interfejs i ikony
assets/skins/  skórki psa
tools/         generator tymczasowych grafik, wypalanie skórki do klatek
tests/         test dymny (Playwright)
archiwum/      poprzedni projekt (Organizacja Esportowa)
```

## Testy

`npm install` (instaluje Playwright), potem `npm test`.
