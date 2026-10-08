/*
 * Autoclicker per Windows.
 *
 * Clicca nella posizione del cursore, in qualsiasi applicazione, alla
 * frequenza scelta oppure il piu' velocemente possibile (0 = massimo).
 * F6 (configurabile) avvia/ferma da qualunque finestra, Esc ferma sempre.
 *
 * I click partono da un thread dedicato con SendInput, a blocchi di BATCH
 * click per chiamata; la frequenza e' tenuta con QueryPerformanceCounter
 * (attesa con Sleep finche' manca piu' di 2 ms, poi attesa attiva).
 */
#ifndef UNICODE
#define UNICODE
#endif
#ifndef _UNICODE
#define _UNICODE
#endif
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <commctrl.h>
#include <shellapi.h>
#include <mmsystem.h>
#include <stdio.h>
#include <stdlib.h>
#include <wchar.h>

#ifndef MOD_NOREPEAT
#define MOD_NOREPEAT 0x4000
#endif

#define APP_TITLE   L"Autoclicker"
#define APP_CLASS   L"AutoclickerVeloceWnd"

#define BATCH       64              /* click per chiamata a SendInput */
#define WM_APP_DONE (WM_APP + 1)    /* il thread dei click si e' fermato da solo */

enum { ID_CPS = 101, ID_BUTTON, ID_LIMIT, ID_DURATION, ID_TOPMOST, ID_START, ID_STATUS };
enum { TIMER_STATUS = 1, TIMER_COUNTDOWN };
enum { HOTKEY_TOGGLE = 1, HOTKEY_ESC };
enum { STOP_USER, STOP_LIMIT, STOP_DURATION, STOP_ESC };

typedef struct {
    double cps;         /* click al secondo, 0 = massimo */
    int button;         /* 0 sinistro, 1 destro, 2 centrale */
    LONGLONG limit;     /* numero di click dopo cui fermarsi, 0 = mai */
    double duration;    /* secondi dopo cui fermarsi, 0 = mai */
} Config;

/* Valori iniziali, eventualmente cambiati dalla riga di comando */
static Config g_init = { 0, 0, 0, 0 };
static int g_autostart = -1;        /* secondi prima dell'avvio automatico, -1 = no */
static BOOL g_initTopmost;

/* Stato condiviso con il thread dei click */
static Config g_cfg;
static volatile LONG g_running;
static volatile LONGLONG g_clicks;
static volatile LONG g_topmost;
static volatile LONG g_paused;      /* cursore sopra l'autoclicker */
static LARGE_INTEGER g_freq, g_startTime, g_endTime;

/* Stato dell'interfaccia (solo thread principale) */
static HANDLE g_thread;
static LONG g_session;
static HWND g_hwnd, g_cps, g_button, g_limit, g_duration, g_chkTop, g_start, g_status, g_hint;
static HFONT g_font;
static double g_scale = 1.0;
static UINT g_hotkey = VK_F6;
static wchar_t g_hotkeyName[8] = L"F6";
static BOOL g_hotkeyOk;
static int g_countdown;
static LARGE_INTEGER g_lastTick;
static LONGLONG g_lastClicks;

static int S(int v) { return (int)(v * g_scale + 0.5); }

/* 1234567 -> "1.234.567" */
static void FormatInt(LONGLONG v, wchar_t *out)
{
    wchar_t rev[32];
    int n = 0, digits = 0;

    if (v < 0) v = 0;
    do {
        if (digits && digits % 3 == 0) rev[n++] = L'.';
        rev[n++] = (wchar_t)(L'0' + v % 10);
        v /= 10;
        digits++;
    } while (v);
    while (n) *out++ = rev[--n];
    *out = 0;
}

/* 12.5 -> "12,5" */
static void FormatDec(double v, wchar_t *out, size_t len)
{
    wchar_t *p;
    swprintf(out, len, L"%.1f", v);
    for (p = out; *p; p++)
        if (*p == L'.') *p = L',';
}

/* Numero >= 0, accetta sia la virgola sia il punto come separatore decimale */
static BOOL ParseNumber(const wchar_t *s, double *out)
{
    wchar_t buf[64], *p, *end;

    if (!s) return FALSE;
    while (*s == L' ') s++;
    if (!_wcsicmp(s, L"max") || !_wcsicmp(s, L"massimo")) { *out = 0; return TRUE; }
    wcsncpy(buf, s, 63);
    buf[63] = 0;
    for (p = buf; *p; p++)
        if (*p == L',') *p = L'.';
    while (p > buf && p[-1] == L' ') *--p = 0;
    if (!*buf) return FALSE;
    *out = wcstod(buf, &end);
    return *end == 0 && *out >= 0 && *out < 1e12;
}

/* ---------------------------------------------------------------------- */
/* Thread dei click                                                        */
/* ---------------------------------------------------------------------- */

/*
 * Non cliccare sulla finestra dell'autoclicker (altrimenti partire con il
 * pulsante "Avvia" fermerebbe subito tutto). Niente WindowFromPoint: manda
 * WM_NCHITTEST alla finestra sotto il cursore e si bloccherebbe se l'app
 * bersaglio e' sommersa di click.
 */
static BOOL CursorOnOwnWindow(void)
{
    POINT p;
    RECT r;

    if (!g_topmost && GetForegroundWindow() != g_hwnd) return FALSE;
    return GetCursorPos(&p) && GetWindowRect(g_hwnd, &r) && PtInRect(&r, p);
}

static DWORD WINAPI ClickThread(LPVOID param)
{
    INPUT in[BATCH * 2];
    const Config cfg = g_cfg;
    const LONG session = (LONG)(INT_PTR)param;
    const double freq = (double)g_freq.QuadPart;
    const LONGLONG endTicks = cfg.duration > 0 ? (LONGLONG)(cfg.duration * freq) : 0;
    LARGE_INTEGER t0, now;
    LONGLONG sent = 0, slot = 0;
    DWORD down, up;
    BOOL resync = FALSE;
    int reason = STOP_USER, i;

    switch (cfg.button) {
    case 1:  down = MOUSEEVENTF_RIGHTDOWN;  up = MOUSEEVENTF_RIGHTUP;  break;
    case 2:  down = MOUSEEVENTF_MIDDLEDOWN; up = MOUSEEVENTF_MIDDLEUP; break;
    default: down = MOUSEEVENTF_LEFTDOWN;   up = MOUSEEVENTF_LEFTUP;   break;
    }
    ZeroMemory(in, sizeof in);
    for (i = 0; i < BATCH; i++) {
        in[2 * i].type = in[2 * i + 1].type = INPUT_MOUSE;
        in[2 * i].mi.dwFlags = down;
        in[2 * i + 1].mi.dwFlags = up;
    }

    timeBeginPeriod(1);
    SetThreadPriority(GetCurrentThread(),
                      cfg.cps > 0 ? THREAD_PRIORITY_HIGHEST : THREAD_PRIORITY_ABOVE_NORMAL);
    QueryPerformanceCounter(&t0);
    g_startTime = t0;  /* il riepilogo parte dal primo click */

    while (g_running) {
        LONGLONG elapsed, n;
        UINT got;

        if (GetAsyncKeyState(VK_ESCAPE) & 0x8000) { reason = STOP_ESC; break; }
        QueryPerformanceCounter(&now);
        elapsed = now.QuadPart - t0.QuadPart;
        if (endTicks && elapsed >= endTicks) { reason = STOP_DURATION; break; }

        if (cfg.cps > 0) {
            /* click "dovuti" dall'avvio; il primo parte subito */
            LONGLONG due = (LONGLONG)(elapsed * cfg.cps / freq) + 1;

            if (resync) { slot = due - 1; resync = FALSE; }
            n = due - slot;
            if (n <= 0) {
                double wait = slot / cfg.cps - elapsed / freq;
                if (wait > 0.002) Sleep(1);
                else SwitchToThread();
                continue;
            }
            /* in ritardo (thread rallentato): salta, niente raffiche di recupero */
            if (n > BATCH) { slot += n - BATCH; n = BATCH; }
        } else {
            n = BATCH;
        }
        if (cfg.limit && sent + n > cfg.limit) n = cfg.limit - sent;

        if (CursorOnOwnWindow()) {
            InterlockedExchange(&g_paused, 1);
            resync = TRUE;
            Sleep(10);
            continue;
        }

        InterlockedExchange(&g_paused, 0);
        got = SendInput((UINT)(2 * n), in, sizeof(INPUT));
        if (got & 1) SendInput(1, &in[1], sizeof(INPUT));  /* mai lasciare il tasto premuto */
        slot += n;
        sent += got / 2;
        InterlockedExchange64(&g_clicks, sent);
        if (got == 0) Sleep(1);  /* input bloccato (es. schermata UAC o di blocco) */
        if (cfg.limit && sent >= cfg.limit) { reason = STOP_LIMIT; break; }
    }

    QueryPerformanceCounter(&g_endTime);
    timeEndPeriod(1);
    if (reason != STOP_USER) {
        InterlockedExchange(&g_running, 0);
        PostMessageW(g_hwnd, WM_APP_DONE, (WPARAM)session, (LPARAM)reason);
    }
    return 0;
}

/* ---------------------------------------------------------------------- */
/* Interfaccia                                                             */
/* ---------------------------------------------------------------------- */

static void SetStatus(const wchar_t *text) { SetWindowTextW(g_status, text); }

static void SetEditNumber(HWND edit, double v)
{
    wchar_t buf[64];
    swprintf(buf, 64, L"%.10g", v);
    SetWindowTextW(edit, buf);
}

static void EnableInputs(BOOL on)
{
    wchar_t buf[64];
    EnableWindow(g_cps, on);
    EnableWindow(g_button, on);
    EnableWindow(g_limit, on);
    EnableWindow(g_duration, on);
    EnableWindow(g_chkTop, on);
    swprintf(buf, 64, on ? L"Avvia (%ls)" : L"Ferma (%ls)", g_hotkeyName);
    SetWindowTextW(g_start, buf);
}

static BOOL ReadField(HWND edit, const wchar_t *name, double *out)
{
    wchar_t buf[64], msg[160];

    GetWindowTextW(edit, buf, 64);
    if (ParseNumber(buf, out)) return TRUE;
    swprintf(msg, 160, L"Valore non valido per \"%ls\": serve un numero maggiore o uguale a 0.", name);
    MessageBoxW(g_hwnd, msg, APP_TITLE, MB_ICONWARNING);
    SetFocus(edit);
    SendMessageW(edit, EM_SETSEL, 0, -1);
    return FALSE;
}

static BOOL ReadConfig(Config *c)
{
    double limit;

    if (!ReadField(g_cps, L"Click al secondo", &c->cps)) return FALSE;
    if (!ReadField(g_limit, L"Ferma dopo N click", &limit)) return FALSE;
    if (!ReadField(g_duration, L"Ferma dopo N secondi", &c->duration)) return FALSE;
    c->limit = (LONGLONG)limit;
    c->button = (int)SendMessageW(g_button, CB_GETCURSEL, 0, 0);
    if (c->button < 0) c->button = 0;
    return TRUE;
}

static void UpdateStatus(void)
{
    wchar_t buf[160], a[32], b[32];
    LARGE_INTEGER now;
    LONGLONG clicks = g_clicks;
    double dt;

    QueryPerformanceCounter(&now);
    dt = (double)(now.QuadPart - g_lastTick.QuadPart) / g_freq.QuadPart;
    if (dt <= 0) return;
    FormatInt(clicks, a);
    FormatInt((LONGLONG)((clicks - g_lastClicks) / dt + 0.5), b);
    if (g_paused)
        swprintf(buf, 160, L"In pausa: il cursore e' sull'autoclicker\n%ls click finora", a);
    else
        swprintf(buf, 160, L"In esecuzione: %ls click\n%ls click/s", a, b);
    SetStatus(buf);
    g_lastTick = now;
    g_lastClicks = clicks;
}

/* Ferma (se serve) il thread dei click e mostra il riepilogo della sessione */
static void StopClicking(int reason)
{
    static const wchar_t *why[] = {
        L"Fermato", L"Limite di click raggiunto", L"Tempo scaduto", L"Fermato con Esc"
    };
    wchar_t buf[200], a[32], b[32], c[32];
    double secs;

    if (!g_thread) return;
    InterlockedExchange(&g_running, 0);
    WaitForSingleObject(g_thread, INFINITE);
    CloseHandle(g_thread);
    g_thread = NULL;
    KillTimer(g_hwnd, TIMER_STATUS);
    UnregisterHotKey(g_hwnd, HOTKEY_ESC);
    EnableInputs(TRUE);

    secs = (double)(g_endTime.QuadPart - g_startTime.QuadPart) / g_freq.QuadPart;
    FormatInt(g_clicks, a);
    FormatDec(secs, b, 32);
    FormatInt(secs > 0 ? (LONGLONG)(g_clicks / secs + 0.5) : 0, c);
    swprintf(buf, 200, L"%ls: %ls click in %ls s\nmedia %ls click/s", why[reason], a, b, c);
    SetStatus(buf);
}

static void StartClicking(void)
{
    Config c;

    StopClicking(STOP_USER);  /* raccoglie un eventuale thread gia' terminato */
    if (!ReadConfig(&c)) return;

    g_cfg = c;
    g_clicks = 0;
    g_paused = 0;
    g_lastClicks = 0;
    QueryPerformanceCounter(&g_startTime);
    g_lastTick = g_startTime;
    g_session++;
    InterlockedExchange(&g_running, 1);
    g_thread = CreateThread(NULL, 0, ClickThread, (LPVOID)(INT_PTR)g_session, 0, NULL);
    if (!g_thread) {
        InterlockedExchange(&g_running, 0);
        SetStatus(L"Errore: impossibile avviare il thread dei click.");
        return;
    }
    /* Esc globale solo mentre clicca, cosi' non lo sottrae alle altre app;
       il thread controlla comunque anche GetAsyncKeyState */
    RegisterHotKey(g_hwnd, HOTKEY_ESC, MOD_NOREPEAT, VK_ESCAPE);
    EnableInputs(FALSE);
    SetStatus(L"In esecuzione: 0 click\n(in pausa se il cursore e' su questa finestra)");
    SetTimer(g_hwnd, TIMER_STATUS, 250, NULL);
}

static void CancelCountdown(void)
{
    KillTimer(g_hwnd, TIMER_COUNTDOWN);
    g_countdown = 0;
}

static void ShowCountdown(void)
{
    wchar_t buf[160];
    swprintf(buf, 160, L"Partenza tra %d s: porta il cursore dove cliccare.\n%ls per annullare.",
             g_countdown, g_hotkeyName);
    SetStatus(buf);
}

static void Toggle(void)
{
    if (g_countdown) {
        CancelCountdown();
        SetStatus(L"Avvio automatico annullato.");
    } else if (g_thread && g_running) {
        StopClicking(STOP_USER);
    } else {
        StartClicking();
    }
}

static HWND Ctl(DWORD ex, const wchar_t *cls, const wchar_t *text, DWORD style,
                int x, int y, int w, int h, int id)
{
    HWND c = CreateWindowExW(ex, cls, text, WS_CHILD | WS_VISIBLE | style,
                             S(x), S(y), S(w), S(h), g_hwnd, (HMENU)(INT_PTR)id,
                             GetModuleHandleW(NULL), NULL);
    SendMessageW(c, WM_SETFONT, (WPARAM)g_font, TRUE);
    return c;
}

static void CreateControls(void)
{
    const DWORD edit = WS_TABSTOP | ES_AUTOHSCROLL;
    wchar_t buf[200];
    int y = 12;

    Ctl(0, L"STATIC", L"Click al secondo (0 = massimo):", 0, 12, y + 3, 210, 20, 0);
    g_cps = Ctl(WS_EX_CLIENTEDGE, L"EDIT", L"", edit, 230, y, 98, 23, ID_CPS);
    y += 31;
    Ctl(0, L"STATIC", L"Tasto del mouse:", 0, 12, y + 3, 210, 20, 0);
    g_button = Ctl(0, L"COMBOBOX", L"", WS_TABSTOP | WS_VSCROLL | CBS_DROPDOWNLIST,
                   230, y, 98, 200, ID_BUTTON);
    SendMessageW(g_button, CB_ADDSTRING, 0, (LPARAM)L"Sinistro");
    SendMessageW(g_button, CB_ADDSTRING, 0, (LPARAM)L"Destro");
    SendMessageW(g_button, CB_ADDSTRING, 0, (LPARAM)L"Centrale");
    y += 31;
    Ctl(0, L"STATIC", L"Ferma dopo N click (0 = mai):", 0, 12, y + 3, 210, 20, 0);
    g_limit = Ctl(WS_EX_CLIENTEDGE, L"EDIT", L"", edit | ES_NUMBER, 230, y, 98, 23, ID_LIMIT);
    y += 31;
    Ctl(0, L"STATIC", L"Ferma dopo N secondi (0 = mai):", 0, 12, y + 3, 210, 20, 0);
    g_duration = Ctl(WS_EX_CLIENTEDGE, L"EDIT", L"", edit, 230, y, 98, 23, ID_DURATION);
    y += 31;
    g_chkTop = Ctl(0, L"BUTTON", L"Sempre in primo piano", WS_TABSTOP | BS_AUTOCHECKBOX,
                   12, y, 316, 22, ID_TOPMOST);
    y += 30;
    g_start = Ctl(0, L"BUTTON", L"", WS_TABSTOP | BS_PUSHBUTTON, 12, y, 316, 34, ID_START);
    y += 44;
    g_status = Ctl(0, L"STATIC", L"", SS_LEFT, 12, y, 316, 36, ID_STATUS);
    y += 42;
    swprintf(buf, 200, L"%ls = avvia/ferma da qualsiasi app, Esc = stop.\n"
             L"Clicca dove si trova il cursore.", g_hotkeyName);
    g_hint = Ctl(0, L"STATIC", buf, SS_LEFT | WS_DISABLED, 12, y, 316, 36, 0);

    SetEditNumber(g_cps, g_init.cps);
    SetEditNumber(g_limit, (double)g_init.limit);
    SetEditNumber(g_duration, g_init.duration);
    SendMessageW(g_button, CB_SETCURSEL, g_init.button, 0);
    if (g_initTopmost) {
        SendMessageW(g_chkTop, BM_SETCHECK, BST_CHECKED, 0);
        g_topmost = 1;
        SetWindowPos(g_hwnd, HWND_TOPMOST, 0, 0, 0, 0, SWP_NOMOVE | SWP_NOSIZE);
    }
    EnableInputs(TRUE);
}

static LRESULT CALLBACK WndProc(HWND hwnd, UINT msg, WPARAM wp, LPARAM lp)
{
    switch (msg) {
    case WM_CREATE:
        g_hwnd = hwnd;
        CreateControls();
        g_hotkeyOk = RegisterHotKey(hwnd, HOTKEY_TOGGLE, MOD_NOREPEAT, g_hotkey);
        if (!g_hotkeyOk) {
            wchar_t buf[160];
            swprintf(buf, 160, L"Attenzione: %ls e' gia' usato da un'altra app.\n"
                     L"Usa il pulsante o avvia con --hotkey F8.", g_hotkeyName);
            SetStatus(buf);
        } else if (g_autostart == 0) {
            StartClicking();
        } else if (g_autostart > 0) {
            g_countdown = g_autostart;
            ShowCountdown();
            SetTimer(hwnd, TIMER_COUNTDOWN, 1000, NULL);
        } else {
            wchar_t buf[160];
            swprintf(buf, 160, L"Pronto. Porta il cursore sull'app da cliccare\ne premi %ls.",
                     g_hotkeyName);
            SetStatus(buf);
        }
        return 0;

    case WM_HOTKEY:
        if (wp == HOTKEY_TOGGLE) Toggle();
        else if (wp == HOTKEY_ESC) StopClicking(STOP_ESC);
        return 0;

    case WM_COMMAND:
        if (LOWORD(wp) == ID_START && HIWORD(wp) == BN_CLICKED) {
            Toggle();
        } else if (LOWORD(wp) == ID_TOPMOST && HIWORD(wp) == BN_CLICKED) {
            BOOL on = SendMessageW(g_chkTop, BM_GETCHECK, 0, 0) == BST_CHECKED;
            InterlockedExchange(&g_topmost, on);
            SetWindowPos(hwnd, on ? HWND_TOPMOST : HWND_NOTOPMOST, 0, 0, 0, 0,
                         SWP_NOMOVE | SWP_NOSIZE);
        }
        return 0;

    case WM_TIMER:
        if (wp == TIMER_STATUS) {
            UpdateStatus();
        } else if (wp == TIMER_COUNTDOWN) {
            if (--g_countdown > 0) {
                ShowCountdown();
            } else {
                CancelCountdown();
                StartClicking();
            }
        }
        return 0;

    case WM_APP_DONE:
        if ((LONG)wp == g_session) StopClicking((int)lp);
        return 0;

    case WM_CLOSE:
        CancelCountdown();
        StopClicking(STOP_USER);
        DestroyWindow(hwnd);
        return 0;

    case WM_DESTROY:
        if (g_hotkeyOk) UnregisterHotKey(hwnd, HOTKEY_TOGGLE);
        PostQuitMessage(0);
        return 0;
    }
    return DefWindowProcW(hwnd, msg, wp, lp);
}

/* ---------------------------------------------------------------------- */
/* Riga di comando                                                         */
/* ---------------------------------------------------------------------- */

static const wchar_t USAGE[] =
    L"Uso: autoclicker.exe [opzioni]\n\n"
    L"--cps N\t\tclick al secondo (0 o max = il piu' veloce possibile)\n"
    L"--tasto T\t\tsinistro | destro | centrale\n"
    L"--limite N\t\tferma dopo N click\n"
    L"--durata S\tferma dopo S secondi\n"
    L"--avvia [S]\tparte da solo dopo S secondi (default 3)\n"
    L"--hotkey Fn\ttasto avvia/ferma, da F1 a F24 (default F6)\n"
    L"--primopiano\tfinestra sempre in primo piano\n"
    L"--chiudi\t\tchiude l'autoclicker gia' aperto ed esce\n\n"
    L"Esempio: autoclicker.exe --cps 50 --durata 10 --avvia 5";

static BOOL Is(const wchar_t *a, const wchar_t *x, const wchar_t *y)
{
    return !_wcsicmp(a, x) || (y && !_wcsicmp(a, y));
}

static BOOL Fail(const wchar_t *what)
{
    wchar_t msg[1200];
    swprintf(msg, 1200, L"%ls\n\n%ls", what, USAGE);
    MessageBoxW(NULL, msg, APP_TITLE, MB_ICONWARNING);
    return FALSE;
}

/* Restituisce FALSE se il programma deve uscire subito */
static BOOL ParseArgs(BOOL *closeOnly)
{
    int argc, i;
    LPWSTR *argv = CommandLineToArgvW(GetCommandLineW(), &argc);
    BOOL ok = TRUE;
    double v;

    for (i = 1; ok && argv && i < argc; i++) {
        const wchar_t *a = argv[i], *next = i + 1 < argc ? argv[i + 1] : NULL;
        wchar_t msg[160];

        while (*a == L'-' || *a == L'/') a++;
        if (Is(a, L"cps", NULL)) {
            if (!ParseNumber(next, &v)) ok = Fail(L"--cps vuole un numero (0 = massimo).");
            else { g_init.cps = v; i++; }
        } else if (Is(a, L"tasto", L"button")) {
            if (next && Is(next, L"sinistro", L"left")) g_init.button = 0;
            else if (next && Is(next, L"destro", L"right")) g_init.button = 1;
            else if (next && Is(next, L"centrale", L"middle")) g_init.button = 2;
            else { ok = Fail(L"--tasto vuole sinistro, destro o centrale."); continue; }
            i++;
        } else if (Is(a, L"limite", L"limit")) {
            if (!ParseNumber(next, &v)) ok = Fail(L"--limite vuole un numero di click.");
            else { g_init.limit = (LONGLONG)v; i++; }
        } else if (Is(a, L"durata", L"duration")) {
            if (!ParseNumber(next, &v)) ok = Fail(L"--durata vuole un numero di secondi.");
            else { g_init.duration = v; i++; }
        } else if (Is(a, L"avvia", L"start")) {
            g_autostart = 3;
            if (next && ParseNumber(next, &v)) { g_autostart = (int)v; i++; }
        } else if (Is(a, L"hotkey", NULL)) {
            int n = next && (next[0] == L'F' || next[0] == L'f') ? _wtoi(next + 1) : 0;
            if (n < 1 || n > 24) { ok = Fail(L"--hotkey vuole un tasto da F1 a F24."); continue; }
            g_hotkey = VK_F1 + n - 1;
            swprintf(g_hotkeyName, 8, L"F%d", n);
            i++;
        } else if (Is(a, L"primopiano", L"topmost")) {
            g_initTopmost = TRUE;
        } else if (Is(a, L"chiudi", L"close")) {
            *closeOnly = TRUE;
        } else if (Is(a, L"help", L"aiuto") || Is(a, L"h", L"?")) {
            MessageBoxW(NULL, USAGE, APP_TITLE, MB_ICONINFORMATION);
            ok = FALSE;
        } else {
            swprintf(msg, 160, L"Opzione sconosciuta: %ls", argv[i]);
            ok = Fail(msg);
        }
    }
    LocalFree(argv);
    return ok;
}

/*
 * Windows 11 ignora timeBeginPeriod e rallenta i processi con la finestra
 * nascosta (es. dietro a un gioco a schermo intero): chiede di non farlo.
 */
static void DisablePowerThrottling(void)
{
    typedef BOOL (WINAPI *SetInfoFn)(HANDLE, PROCESS_INFORMATION_CLASS, LPVOID, DWORD);
    PROCESS_POWER_THROTTLING_STATE st;
    SetInfoFn set = (SetInfoFn)(void (*)(void))GetProcAddress(GetModuleHandleW(L"kernel32.dll"),
                                                             "SetProcessInformation");
    if (!set) return;
    ZeroMemory(&st, sizeof st);
    st.Version = PROCESS_POWER_THROTTLING_CURRENT_VERSION;
    st.ControlMask = PROCESS_POWER_THROTTLING_EXECUTION_SPEED |
                     PROCESS_POWER_THROTTLING_IGNORE_TIMER_RESOLUTION;
    st.StateMask = 0;
    set(GetCurrentProcess(), ProcessPowerThrottling, &st, sizeof st);
}

int WINAPI wWinMain(HINSTANCE inst, HINSTANCE prev, PWSTR cmd, int show)
{
    INITCOMMONCONTROLSEX icc = { sizeof icc, ICC_STANDARD_CLASSES };
    NONCLIENTMETRICSW ncm;
    WNDCLASSEXW wc;
    RECT r = { 0, 0, 340, 0 };
    BOOL closeOnly = FALSE;
    HWND old;
    HDC dc;
    MSG msg;
    int i;
    (void)prev;
    (void)cmd;

    if (!ParseArgs(&closeOnly)) return 1;

    /* Una sola istanza: quella nuova (con le nuove opzioni) sostituisce la vecchia */
    old = FindWindowW(APP_CLASS, NULL);
    if (old) {
        PostMessageW(old, WM_CLOSE, 0, 0);
        for (i = 0; i < 60 && IsWindow(old); i++) Sleep(50);
    }
    if (closeOnly) return 0;

    InitCommonControlsEx(&icc);
    ZeroMemory(&ncm, sizeof ncm);
    ncm.cbSize = sizeof ncm;
    ZeroMemory(&wc, sizeof wc);
    wc.cbSize = sizeof wc;
    QueryPerformanceFrequency(&g_freq);
    DisablePowerThrottling();

    dc = GetDC(NULL);
    g_scale = GetDeviceCaps(dc, LOGPIXELSY) / 96.0;
    ReleaseDC(NULL, dc);
    SystemParametersInfoW(SPI_GETNONCLIENTMETRICS, sizeof ncm, &ncm, 0);
    g_font = CreateFontIndirectW(&ncm.lfMessageFont);

    wc.lpfnWndProc = WndProc;
    wc.hInstance = inst;
    wc.hCursor = LoadCursorW(NULL, (LPCWSTR)IDC_ARROW);
    wc.hIcon = LoadIconW(inst, MAKEINTRESOURCEW(1));
    if (!wc.hIcon) wc.hIcon = LoadIconW(NULL, (LPCWSTR)IDI_APPLICATION);
    wc.hbrBackground = (HBRUSH)(COLOR_BTNFACE + 1);
    wc.lpszClassName = APP_CLASS;
    RegisterClassExW(&wc);

    r.right = S(340);
    r.bottom = S(304);
    AdjustWindowRectEx(&r, WS_OVERLAPPED | WS_CAPTION | WS_SYSMENU | WS_MINIMIZEBOX, FALSE, 0);
    if (!CreateWindowExW(WS_EX_CONTROLPARENT, APP_CLASS, APP_TITLE,
                         WS_OVERLAPPED | WS_CAPTION | WS_SYSMENU | WS_MINIMIZEBOX,
                         CW_USEDEFAULT, CW_USEDEFAULT, r.right - r.left, r.bottom - r.top,
                         NULL, NULL, inst, NULL))
        return 1;
    ShowWindow(g_hwnd, show);
    UpdateWindow(g_hwnd);

    while (GetMessageW(&msg, NULL, 0, 0) > 0) {
        if (!IsDialogMessageW(g_hwnd, &msg)) {
            TranslateMessage(&msg);
            DispatchMessageW(&msg);
        }
    }
    DeleteObject(g_font);
    return 0;
}
