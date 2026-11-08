# Luna – Android základ projektu

Cílové zařízení: Samsung Galaxy Tab Active3 (SM-T575), Android 13. Aktivační fráze: **Ahoj Luno**.

## Co obsahuje tato první vývojová verze
- Základní Android aplikaci s obrazovkou Luny.
- Vyžádání oprávnění mikrofonu a oznámení.
- Samostatnou foreground službu typu microphone.
- GitHub Actions workflow pro sestavení debug APK.

## Co zatím NEOBSAHUJE
Tato verze ještě nedetekuje „Ahoj Luno“, nepřepisuje řeč na text a nevolá Open WebUI. Je to sestavitelná technická základna, nikoli hotový hlasový asistent. V dalším kroku musíme ověřit vhodný wake-word model s podporou české fráze a doplnit hlasový/API řetězec.

## Sestavení přes GitHub
1. Vytvoř na GitHubu nový prázdný repozitář, například `Luna-Android`.
2. Nahraj obsah ZIPu do kořene repozitáře (nikoli nadřazenou složku `LunaAndroid`).
3. Otevři **Actions**. Po nahrání do větve `main` se spustí workflow **Build Luna APK**.
4. Po dokončení otevři běh workflow a v části **Artifacts** stáhni `Luna-debug-apk`.
5. Rozbal stažený artefakt; uvnitř je `app-debug.apk`.

## Bezpečnost
- Není vložen žádný Open WebUI token ani heslo. Neposílej je do chatu.
- Aplikace zatím používá HTTP povolené pro lokální síť; před ostrým použitím omezíme síťovou konfiguraci a ověříme zabezpečení.
- První spuštění služby proveď přímo v aplikaci a uděl jí požadovaná oprávnění. Android může omezovat mikrofonní službu spuštěnou z pozadí.
