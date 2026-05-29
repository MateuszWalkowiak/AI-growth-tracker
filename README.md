# AI Growth Tracker

Aplikacja Android do planowania, sledzenia i analizowania codziennych zadan rozwojowych. Wykorzystuje AI (Gemini) do generowania spersonalizowanych planow tygodniowych, integruje sie z Firebase i oferuje modul sledzenia biegow z mapa (Mapbox).

## Funkcjonalnosci

- **Kalendarz z postepami** — widok miesiczny z kolorowym oznaczeniem progresu na kazdy dzien
- **Zadania z timerem** — tworzenie zadan z kategoriami, priorytetami i minutnikiem wykonania
- **Tygodniowy plan AI** — asystent Gemini generuje plan na tydzien z uwzglednieniem swiat i weekendow
- **Statystyki** — wykresy postepow, podsumowania tygodniowe i dzienne
- **Sledzenie biegow** — GPS tracking z mapa trasy (Mapbox), historia treningow
- **Powiadomienia** — przypomnienia o zaplanowanych zadaniach
- **Firebase Auth** — logowanie i synchronizacja danych miedzy urzadzeniami
- **Health Connect** — integracja z krokomierzem

## Stack technologiczny

| Warstwa | Technologia |
|---|---|
| UI | Jetpack Compose + Material 3 |
| Architektura | MVVM (ViewModel + StateFlow) |
| Baza danych | Room (SQLite) |
| Backend | Firebase Auth + Firestore |
| AI | Google Gemini API |
| Mapy | Mapbox SDK |
| Swieta | Calendarific API |
| HTTP | Retrofit + Gson |
| Lokalizacja | Google Play Services Location |

## Wymagania

- Android Studio Hedgehog (2023.1.1) lub nowsze
- Android SDK 35 (min SDK 26)
- JDK 11

## Konfiguracja kluczy API

Aplikacja korzysta z kilku zewnetrznych serwisow. Przed zbudowaniem projektu musisz skonfigurowac odpowiednie klucze:

### 1. Firebase

1. Utworz projekt w [Firebase Console](https://console.firebase.google.com/)
2. Dodaj aplikacje Android z package name `com.MateuszW.aigrowthtracker`
3. Pobierz plik `google-services.json`
4. Umiesc go w katalogu `app/`

> Plik `google-services.json` jest juz dodany do `app/.gitignore` — nie trafi do repozytorium.

### 2. Calendarific (Holiday API)

1. Zarejestruj sie na [calendarific.com](https://calendarific.com/) i pobierz klucz API
2. Dodaj do pliku `local.properties` w katalogu glownym projektu:
   ```properties
   HOLIDAY_API_KEY=twoj_klucz_calendarific
   ```

### 3. Mapbox

1. Utworz konto na [mapbox.com](https://www.mapbox.com/)
2. Wygeneruj public access token
3. Dodaj do pliku `local.properties`:
   ```properties
   MAPBOX_ACCESS_TOKEN=pk.twoj_token_mapbox
   ```

### 4. Gemini (Google AI)

1. Wygeneruj klucz API w [Google AI Studio](https://aistudio.google.com/app/apikey)
2. Klucz wpisujesz **w aplikacji** w ekranie Ustawienia — nie jest przechowywany w kodzie zrodlowym

### Przykladowy `local.properties`

```properties
sdk.dir=C\:\\Users\\TwojUser\\AppData\\Local\\Android\\Sdk
HOLIDAY_API_KEY=twoj_klucz_calendarific
MAPBOX_ACCESS_TOKEN=pk.twoj_token_mapbox
```

> **Uwaga:** Plik `local.properties` jest w `.gitignore` i nigdy nie powinien byc commitowany.

## Budowanie projektu

```bash
# Klonowanie repozytorium
git clone https://github.com/MateuszWalkowiak/AI-growth-tracker.git
cd AI-growth-tracker

# Skonfiguruj klucze (patrz sekcja wyzej)
# Umiesc google-services.json w app/

# Zbuduj projekt
./gradlew assembleDebug
```

Alternatywnie otworz projekt w Android Studio i uruchom na emulatorze lub fizycznym urzadzeniu.

## Struktura projektu

```
app/src/main/java/com/MateuszW/aigrowthtracker/
├── MainActivity.kt              # Punkt wejscia, nawigacja
├── MainScreen.kt                # Ekran glowny z kalendarzem
├── AddTaskScreen.kt             # Dodawanie nowego zadania
├── ExecuteTaskScreen.kt         # Wykonywanie zadania z timerem
├── TaskDetailsScreen.kt         # Szczegoly zadania
├── DayDetailsScreen.kt          # Podsumowanie dnia
├── StatisticsScreen.kt          # Wykresy i statystyki
├── SettingsScreen.kt            # Ustawienia aplikacji
├── LoginScreen.kt               # Ekran logowania (Firebase)
├── WeeklyPreviewScreen.kt       # Podglad planu tygodniowego
├── WeeklySummaryScreen.kt       # Podsumowanie tygodnia
├── WeeklyContinuationScreen.kt  # Kontynuacja planu
├── HolidayAdjustmentScreen.kt   # Dostosowanie do swiat
├── TaskViewModel.kt             # Glowny ViewModel
├── data/                        # Room entities, DAO, Firebase repo
└── running/                     # Modul sledzenia biegow (GPS + Mapbox)
```
