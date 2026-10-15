// Behavioral reconstruction from IDA/Hex-Rays and original-machine-code audit.
// This is recovered source, not the author's original source. Windows x64 only.
#include <windows.h>
#include <tlhelp32.h>
#include <algorithm>
#include <cstdio>
#include <cwchar>
#include <string>
#include <vector>

namespace {
struct Candidate { DWORD pid{}; wchar_t executable[260]{}; wchar_t title[256]{}; };
HWND mainWindow{}, processList{}, statusLabel{}, injectButton{};
std::wstring payloadPath;
std::vector<Candidate> candidates;
bool injectionSucceeded{};
bool ownConsole{};
bool automatedMode{};
volatile LONG cancelled{};
std::wstring nativeLogPath;

struct Handle {
  HANDLE value{INVALID_HANDLE_VALUE};
  explicit Handle(HANDLE h) : value(h) {}
  ~Handle() { if (value && value != INVALID_HANDLE_VALUE) CloseHandle(value); }
  explicit operator bool() const { return value && value != INVALID_HANDLE_VALUE; }
};

BOOL WINAPI consoleControl(DWORD event) {
  if (event == CTRL_C_EVENT || event == CTRL_BREAK_EVENT) {
    InterlockedExchange(&cancelled, 1);
    return TRUE;
  }
  return FALSE;
}

void openConsole(bool diagnosticMode) {
  DWORD inheritedOutputType = GetFileType(GetStdHandle(STD_OUTPUT_HANDLE));
  if (diagnosticMode && (inheritedOutputType == FILE_TYPE_PIPE || inheritedOutputType == FILE_TYPE_DISK)) {
    setvbuf(stdout, nullptr, _IONBF, 0);
    return;
  }
  if (!GetConsoleWindow() && !AttachConsole(ATTACH_PARENT_PROCESS))
    ownConsole = AllocConsole() != FALSE;
  FILE* stream{};
  freopen_s(&stream, "CONOUT$", "w", stdout);
  freopen_s(&stream, "CONOUT$", "w", stderr);
  freopen_s(&stream, "CONIN$", "r", stdin);
  setvbuf(stdout, nullptr, _IONBF, 0);
  SetConsoleOutputCP(CP_UTF8);
  SetConsoleTitleW(L"Slinky - startup progress");
  SetConsoleCtrlHandler(consoleControl, TRUE);
}

void print(const std::wstring& message) {
  int size = WideCharToMultiByte(CP_UTF8, 0, message.c_str(), -1, nullptr, 0, nullptr, nullptr);
  if (size <= 0) return;
  std::string utf8(static_cast<size_t>(size), '\0');
  WideCharToMultiByte(CP_UTF8, 0, message.c_str(), -1, utf8.data(), size, nullptr, nullptr);
  printf("%s\n", utf8.c_str());
}

void windowsError(const wchar_t* operation, DWORD error = GetLastError()) {
  wchar_t* detail{};
  FormatMessageW(FORMAT_MESSAGE_ALLOCATE_BUFFER | FORMAT_MESSAGE_FROM_SYSTEM |
    FORMAT_MESSAGE_IGNORE_INSERTS, nullptr, error, 0, reinterpret_cast<wchar_t*>(&detail), 0, nullptr);
  print(std::wstring(L"ERROR: ") + operation + L" (Windows " + std::to_wstring(error) +
    L"): " + (detail ? detail : L"No further details available."));
  if (detail) LocalFree(detail);
}

int finish(int code, bool pause = true) {
  if (ownConsole && pause && !automatedMode) {
    printf("\nPress Enter to close this window.\n");
    // Reset a cancellation request so the diagnostics remain visible.
    InterlockedExchange(&cancelled, 0);
    char line[8]{};
    fgets(line, sizeof line, stdin);
  }
  return code;
}

enum class StartupStatus { Waiting, Running, AlreadyRunning, Failed, Reloaded };
StartupStatus lastStartupStatus{StartupStatus::Waiting};

StartupStatus statusFromLine(const std::string& line) {
  if (line.find("FINAL STATUS: ALREADY_RUNNING") != std::string::npos ||
      line.find("already running in this process; ignoring second injection") != std::string::npos)
    return StartupStatus::AlreadyRunning;
  if (line.find("FINAL STATUS: FAILED") != std::string::npos ||
      line.find("bootstrap failed; unloading") != std::string::npos ||
      line.find("Bootstrap.start:") != std::string::npos) return StartupStatus::Failed;
  if (line.find("FINAL STATUS: RELOADED") != std::string::npos) return StartupStatus::Reloaded;
  if (line.find("FINAL STATUS: RUNNING") != std::string::npos ||
      line.find("Slinky is running") != std::string::npos) return StartupStatus::Running;
  return StartupStatus::Waiting;
}

void reportStatus(StartupStatus status) {
  switch (status) {
  case StartupStatus::Running:
    printf("\nREADY: Slinky finished starting. Press Right Shift in Minecraft.\n"); break;
  case StartupStatus::Reloaded:
    printf("\nREADY: Slinky loaded again after the previous unload. Press Right Shift in Minecraft.\n"); break;
  case StartupStatus::AlreadyRunning:
    printf("\nSKIPPED: Slinky has an active instance in this game; no reload was attempted.\n"
      "Unload it from the Slinky menu, then run this launcher again.\n"); break;
  case StartupStatus::Failed:
    printf("\nFAILED: Slinky could not finish starting. The reason is in the messages above.\n"); break;
  default: break;
  }
}

// Read complete UTF-8 log lines while allowing the worker to append concurrently.
struct LogReader {
  ULONGLONG offset{};
  std::string pending;
  StartupStatus status{StartupStatus::Waiting};
  bool finalStatus{};
  DWORD error{};
  void consumeStatusLine(const std::string& line) {
    const auto next = statusFromLine(line);
    if (next == StartupStatus::Waiting) return;
    if (line.find("FINAL STATUS: ") != std::string::npos) {
      status = next;
      finalStatus = true;
      return;
    }
    if (finalStatus) return;
    // The original worker could say 'running' after ignoring the second load.
    // A legacy failure or skipped load must survive that misleading later line.
    if (next == StartupStatus::Failed) status = next;
    else if (next == StartupStatus::AlreadyRunning && status != StartupStatus::Failed) status = next;
    else if (status == StartupStatus::Waiting || status == StartupStatus::Running) status = next;
  }
  bool read(const std::wstring& path) {
    HANDLE file = CreateFileW(path.c_str(), GENERIC_READ,
      FILE_SHARE_READ | FILE_SHARE_WRITE | FILE_SHARE_DELETE, nullptr, OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, nullptr);
    if (file == INVALID_HANDLE_VALUE) { error = GetLastError(); return false; }
    LARGE_INTEGER size{}, position{};
    if (!GetFileSizeEx(file, &size)) { error = GetLastError(); CloseHandle(file); return false; }
    if (static_cast<ULONGLONG>(size.QuadPart) < offset) {
      offset = 0; pending.clear(); status = StartupStatus::Waiting; finalStatus = false;
    }
    position.QuadPart = static_cast<LONGLONG>(offset);
    if (!SetFilePointerEx(file, position, nullptr, FILE_BEGIN)) { error = GetLastError(); CloseHandle(file); return false; }
    char buffer[4096];
    DWORD count{};
    BOOL readOk = TRUE;
    while ((readOk = ReadFile(file, buffer, sizeof buffer, &count, nullptr)) != FALSE && count) {
      offset += count;
      pending.append(buffer, count);
      size_t newline{};
      while ((newline = pending.find('\n')) != std::string::npos) {
        std::string line = pending.substr(0, newline + 1);
        pending.erase(0, newline + 1);
        // Skip the UTF-8 BOM emitted by the native CRT on a new log.
        if (line.compare(0, 3, "\xef\xbb\xbf") == 0) line.erase(0, 3);
        if (line.size() >= 2 && line[line.size() - 2] == '\r') line.erase(line.size() - 2, 1);
        // Keep routine JVM bookkeeping in the log, while the console shows progress.
        if (line.find("is not prepared; retaining fresh modifiers") == std::string::npos &&
            line.find("preserved existing ACC_STRICT constructor flag") == std::string::npos &&
            line.find("constructor modifier compatibility:") == std::string::npos)
          fwrite(line.data(), 1, line.size(), stdout);
        consumeStatusLine(line);
      }
    }
    error = readOk ? ERROR_SUCCESS : GetLastError();
    CloseHandle(file);
    return readOk != FALSE;
  }
  void flushLastLine() {
    if (pending.empty()) return;
    if (pending.compare(0, 3, "\xef\xbb\xbf") == 0) pending.erase(0, 3);
    print(L"Log ends with a partial line:");
    printf("%s\n", pending.c_str());
    consumeStatusLine(pending);
    pending.clear();
  }
};

bool waitForStartup(DWORD pid, ULONGLONG started) {
  lastStartupStatus = StartupStatus::Waiting;
  print(L"Watching startup log: " + nativeLogPath);
  printf("The DLL is loaded. Waiting for Slinky to finish starting (up to 250 seconds total).\n"
    "Ctrl+C stops waiting; it does not unload anything already loaded into the game.\n");
  LogReader log;
  Handle process(OpenProcess(SYNCHRONIZE, FALSE, pid));
  ULONGLONG nextProgress = GetTickCount64() + 15000;
  ULONGLONG legacyStatusAt{};
  while (!cancelled && GetTickCount64() - started < 250000) {
    log.read(nativeLogPath);
    if (log.status != StartupStatus::Waiting && !legacyStatusAt) legacyStatusAt = GetTickCount64();
    // Allow a final marker following a legacy message to arrive on the next read.
    if (log.status != StartupStatus::Waiting && (log.finalStatus || GetTickCount64() - legacyStatusAt >= 500)) {
      lastStartupStatus = log.status;
      reportStatus(log.status);
      return log.status == StartupStatus::Running || log.status == StartupStatus::Reloaded;
    }
    if (process && WaitForSingleObject(process.value, 0) == WAIT_OBJECT_0) {
      printf("\nFAILED: The selected game process closed before startup finished.\n");
      return false;
    }
    if (GetTickCount64() >= nextProgress) {
      printf("Still waiting for the game to finish startup...\n");
      nextProgress = GetTickCount64() + 15000;
    }
    Sleep(150);
  }
  log.read(nativeLogPath);
  log.flushLastLine();
  if (log.status != StartupStatus::Waiting) {
    lastStartupStatus = log.status;
    reportStatus(log.status);
    return log.status == StartupStatus::Running || log.status == StartupStatus::Reloaded;
  }
  if (cancelled) printf("\nStopped waiting. Startup was not confirmed.\n");
  else printf("\nTIMEOUT: The DLL loaded, but Slinky never confirmed it finished starting.\n");
  if (log.error != ERROR_SUCCESS && log.error != ERROR_FILE_NOT_FOUND)
    windowsError(L"Reading the startup log", log.error);
  print(L"Keep the log for troubleshooting: " + nativeLogPath);
  return false;
}

void showAvailableStartupLog() {
  LogReader log;
  log.read(nativeLogPath);
  log.flushLastLine();
  lastStartupStatus = log.status;
  reportStatus(log.status);
  print(L"Startup log: " + nativeLogPath);
}

// Original: 0x1400019F0. Compare full path, not only DLL basename.
BYTE* moduleBase(DWORD pid, const wchar_t* pathOrName, bool fullPath) {
  Handle snapshot(CreateToolhelp32Snapshot(TH32CS_SNAPMODULE | TH32CS_SNAPMODULE32, pid));
  if (!snapshot) return nullptr;
  MODULEENTRY32W module{};
  module.dwSize = sizeof module;
  if (Module32FirstW(snapshot.value, &module)) do {
    if (!_wcsicmp(fullPath ? module.szExePath : module.szModule, pathOrName))
      return module.modBaseAddr;
  } while (Module32NextW(snapshot.value, &module));
  return nullptr;
}

// Original: 0x140001780. Return 2 for already loaded, 1 for loaded, 0 on failure.
int inject(DWORD pid, const std::wstring& dll) {
  if (moduleBase(pid, dll.c_str(), true)) {
    printf("SKIPPED: This DLL is already loaded in PID %lu. Startup was not re-run.\n", pid);
    return 2;
  }
  Handle process(OpenProcess(PROCESS_CREATE_THREAD | PROCESS_VM_OPERATION | PROCESS_VM_READ |
                            PROCESS_VM_WRITE | PROCESS_QUERY_INFORMATION, FALSE, pid));
  if (!process) { windowsError(L"Opening the selected game process"); return 0; }
  const SIZE_T size = (dll.size() + 1) * sizeof(wchar_t);
  void* remotePath = VirtualAllocEx(process.value, nullptr, size, MEM_COMMIT | MEM_RESERVE, PAGE_READWRITE);
  if (!remotePath) { windowsError(L"Allocating the DLL path in the game"); return 0; }
  int result = 0;
  bool mayFreePath = true;
  SIZE_T written = 0;
  const BOOL copied = WriteProcessMemory(process.value, remotePath, dll.c_str(), size, &written);
  const DWORD copyError = copied ? ERROR_PARTIAL_COPY : GetLastError();
  if (copied && written == size) {
    HMODULE localKernel = GetModuleHandleW(L"kernel32.dll");
    FARPROC localLoad = localKernel ? GetProcAddress(localKernel, "LoadLibraryW") : nullptr;
    BYTE* remoteKernel = moduleBase(pid, L"kernel32.dll", false);
    if (localKernel && localLoad && remoteKernel) {
      // Preserve the original RVA relocation assumption; runtime behavior was not tested.
      auto start = reinterpret_cast<LPTHREAD_START_ROUTINE>(remoteKernel +
        (reinterpret_cast<BYTE*>(localLoad) - reinterpret_cast<BYTE*>(localKernel)));
      Handle thread(CreateRemoteThread(process.value, nullptr, 0, start, remotePath, 0, nullptr));
      if (thread) {
        printf("Loading Slinky into PID %lu...\n", pid);
        DWORD waitResult = WAIT_TIMEOUT;
        const ULONGLONG began = GetTickCount64();
        while (!cancelled && GetTickCount64() - began < 30000) {
          waitResult = WaitForSingleObject(thread.value, 150);
          if (waitResult != WAIT_TIMEOUT) break;
        }
        if (waitResult == WAIT_OBJECT_0) {
          DWORD exitCode{};
          if (!GetExitCodeThread(thread.value, &exitCode)) windowsError(L"Reading the DLL loader result");
          // A worker may deliberately unload its unused DLL after reporting
          // ALREADY_RUNNING or FAILED. Its log remains the startup result.
          const DWORD logAttributes = GetFileAttributesW(nativeLogPath.c_str());
          if (exitCode || (logAttributes != INVALID_FILE_ATTRIBUTES && !(logAttributes & FILE_ATTRIBUTE_DIRECTORY)))
            result = 1;
          for (int attempt = 0; !result && attempt < 100 && !cancelled; ++attempt) {
            if (moduleBase(pid, dll.c_str(), true)) { result = 1; break; }
            Sleep(50);
          }
          if (!result) printf("ERROR: The DLL is not present in the game after the load attempt.\n");
        } else {
          // The loader can still be reading remotePath. Keep that memory valid.
          mayFreePath = false;
          if (waitResult == WAIT_FAILED) windowsError(L"Waiting for the DLL loader");
          else printf(cancelled ? "Stopped waiting for the DLL loader. Its result is unknown.\n" :
            "TIMEOUT: The game did not finish loading the DLL within 30 seconds. Its result is unknown.\n");
        }
      } else windowsError(L"Starting the DLL loader in the game");
    } else {
      windowsError(L"Finding the Windows DLL loader", ERROR_PROC_NOT_FOUND);
    }
  } else windowsError(L"Copying the DLL path into the game", copyError);
  if (mayFreePath) VirtualFreeEx(process.value, remotePath, 0, MEM_RELEASE);
  return result;
}

BOOL CALLBACK windowTitle(HWND window, LPARAM parameter) {
  if (!IsWindowVisible(window) || !GetWindowTextLengthW(window)) return TRUE;
  DWORD pid = 0;
  GetWindowThreadProcessId(window, &pid);
  auto& list = *reinterpret_cast<std::vector<Candidate>*>(parameter);
  for (auto& candidate : list) {
    if (candidate.pid == pid && !candidate.title[0]) {
      GetWindowTextW(window, candidate.title, static_cast<int>(std::size(candidate.title)));
      break;
    }
  }
  return TRUE;
}

// Original: 0x140001490, 0x140001690. Any visible Java window passes.
std::vector<Candidate> enumerateCandidates() {
  std::vector<Candidate> result;
  Handle snapshot(CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0));
  if (!snapshot) return result;
  PROCESSENTRY32W process{};
  process.dwSize = sizeof process;
  if (Process32FirstW(snapshot.value, &process)) do {
    if (result.size() < 64 && (!_wcsicmp(process.szExeFile, L"java.exe") ||
                              !_wcsicmp(process.szExeFile, L"javaw.exe"))) {
      Candidate entry{};
      entry.pid = process.th32ProcessID;
      wcsncpy_s(entry.executable, process.szExeFile, _TRUNCATE);
      result.push_back(entry);
    }
  } while (Process32NextW(snapshot.value, &process));
  EnumWindows(windowTitle, reinterpret_cast<LPARAM>(&result));
  result.erase(std::remove_if(result.begin(), result.end(), [](const auto& c) { return !c.title[0]; }), result.end());
  return result;
}

bool extractNative() {
  HRSRC resource = FindResourceW(nullptr, MAKEINTRESOURCEW(200), RT_RCDATA);
  if (!resource) { windowsError(L"Finding the bundled DLL"); return false; }
  DWORD size = SizeofResource(nullptr, resource);
  HGLOBAL loaded = LoadResource(nullptr, resource);
  const void* bytes = loaded ? LockResource(loaded) : nullptr;
  if (!bytes || !size) { windowsError(L"Reading the bundled DLL", ERROR_INVALID_DATA); return false; }
  wchar_t temporary[MAX_PATH]{};
  DWORD tempLength = GetTempPathW(MAX_PATH, temporary);
  if (!tempLength || tempLength >= MAX_PATH) { windowsError(L"Finding a temporary folder", tempLength ? ERROR_BUFFER_OVERFLOW : GetLastError()); return false; }
  wchar_t directory[MAX_PATH]{};
  _snwprintf_s(directory, _TRUNCATE, L"%lsslinky-%lu", temporary, GetCurrentProcessId());
  if (!CreateDirectoryW(directory, nullptr) && GetLastError() != ERROR_ALREADY_EXISTS) {
    windowsError(L"Creating a temporary folder"); return false;
  }
  payloadPath = std::wstring(directory) + L"\\SlinkyNative.dll";
  nativeLogPath = std::wstring(directory) + L"\\slinky-native.log";
  // A reused launcher PID must not make an old successful log look like today's result.
  if (!DeleteFileW(nativeLogPath.c_str()) && GetLastError() != ERROR_FILE_NOT_FOUND) {
    windowsError(L"Clearing the previous startup log"); return false;
  }
  Handle file(CreateFileW(payloadPath.c_str(), GENERIC_WRITE, 0, nullptr, CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, nullptr));
  if (!file) { windowsError(L"Writing the bundled DLL"); return false; }
  DWORD written{};
  const BOOL saved = WriteFile(file.value, bytes, size, &written, nullptr);
  const DWORD writeError = saved ? ERROR_WRITE_FAULT : GetLastError();
  if (!saved || written != size) {
    windowsError(L"Extracting the bundled DLL", writeError); return false;
  }
  print(L"Extracted DLL: " + payloadPath);
  return true;
}

void injectionError(HWND owner) {
  if (automatedMode) return;
  SetWindowTextW(statusLabel, L"Startup failed - see console for the reason.");
  if (owner) ShowWindow(owner, SW_HIDE);
  printf("\nStartup was not confirmed. See the specific message above.\n");
}

void injectSelected(DWORD pid) {
  KillTimer(mainWindow, 1);
  print(L"Selected game process: PID " + std::to_wstring(pid));
  ULONGLONG started = GetTickCount64();
  const int loaded = inject(pid, payloadPath);
  if (loaded == 1 && waitForStartup(pid, started)) {
    injectionSucceeded = true;
    PostQuitMessage(0);
  } else {
    if (loaded == 0) showAvailableStartupLog();
    if (loaded == 2 || lastStartupStatus == StartupStatus::AlreadyRunning) {
      lastStartupStatus = StartupStatus::AlreadyRunning;
      ShowWindow(mainWindow, SW_HIDE);
      PostQuitMessage(2);
      return;
    }
    injectionError(mainWindow);
    PostQuitMessage(1);
  }
}

void refreshList() {
  DWORD selectedPid = 0;
  LRESULT selection = SendMessageW(processList, LB_GETCURSEL, 0, 0);
  if (selection != LB_ERR && static_cast<size_t>(selection) < candidates.size())
    selectedPid = candidates[static_cast<size_t>(selection)].pid;
  candidates = enumerateCandidates();
  SendMessageW(processList, LB_RESETCONTENT, 0, 0);
  size_t newSelection = 0;
  for (size_t i = 0; i < candidates.size(); ++i) {
    const auto& c = candidates[i];
    wchar_t row[600]{};
    _snwprintf_s(row, _TRUNCATE, L"[%5lu]  %-9ls  %ls", c.pid, c.executable, c.title);
    SendMessageW(processList, LB_ADDSTRING, 0, reinterpret_cast<LPARAM>(row));
    if (c.pid == selectedPid) newSelection = i;
  }
  if (!candidates.empty()) {
    SendMessageW(processList, LB_SETCURSEL, newSelection, 0);
    wchar_t label[80]{};
    _snwprintf_s(label, _TRUNCATE, L"%zu Minecraft window%ls", candidates.size(), candidates.size() == 1 ? L"" : L"s");
    SetWindowTextW(statusLabel, label);
  } else SetWindowTextW(statusLabel, L"Waiting for Minecraft to open...");
  EnableWindow(injectButton, !candidates.empty());
}

LRESULT CALLBACK launcherProcedure(HWND window, UINT message, WPARAM wParam, LPARAM lParam) {
  switch (message) {
  case WM_CREATE: {
    mainWindow = window;
    RECT rect{};
    GetClientRect(window, &rect);
    const int width = rect.right, height = rect.bottom;
    processList = CreateWindowExW(WS_EX_CLIENTEDGE, L"LISTBOX", nullptr,
      WS_CHILD | WS_VISIBLE | WS_VSCROLL | LBS_NOTIFY, 8, 8, width - 16, height - 52, window, reinterpret_cast<HMENU>(101), nullptr, nullptr);
    statusLabel = CreateWindowExW(0, L"STATIC", L"", WS_CHILD | WS_VISIBLE | SS_CENTERIMAGE | SS_LEFTNOWORDWRAP,
      8, height - 38, width - 196, 26, window, reinterpret_cast<HMENU>(104), nullptr, nullptr);
    injectButton = CreateWindowExW(0, L"BUTTON", L"Inject", WS_CHILD | WS_VISIBLE | BS_DEFPUSHBUTTON,
      width - 184, height - 40, 84, 28, window, reinterpret_cast<HMENU>(102), nullptr, nullptr);
    HWND cancel = CreateWindowExW(0, L"BUTTON", L"Cancel", WS_CHILD | WS_VISIBLE,
      width - 94, height - 40, 86, 28, window, reinterpret_cast<HMENU>(103), nullptr, nullptr);
    auto font = reinterpret_cast<WPARAM>(GetStockObject(DEFAULT_GUI_FONT));
    for (HWND control : {processList, statusLabel, injectButton, cancel}) SendMessageW(control, WM_SETFONT, font, TRUE);
    SetTimer(window, 1, 750, nullptr);
    refreshList();
    return 0;
  }
  case WM_TIMER:
    if (wParam == 1) {
      if (cancelled) { printf("Cancelled before startup.\n"); PostQuitMessage(1); return 0; }
      size_t previousCount = candidates.size();
      refreshList();
      if (!previousCount && candidates.size() == 1) injectSelected(candidates.front().pid);
    }
    return 0;
  case WM_COMMAND:
    if (LOWORD(wParam) == 102) {
      LRESULT selection = SendMessageW(processList, LB_GETCURSEL, 0, 0);
      if (selection != LB_ERR && static_cast<size_t>(selection) < candidates.size())
        injectSelected(candidates[static_cast<size_t>(selection)].pid);
    } else if (LOWORD(wParam) == 103) { printf("Cancelled before startup.\n"); PostQuitMessage(1); }
    return 0;
  case WM_CLOSE: printf("Cancelled before startup.\n"); PostQuitMessage(1); return 0;
  default: return DefWindowProcW(window, message, wParam, lParam);
  }
}

// These switches run before resource extraction or process enumeration.
// Keep them independent of Minecraft so console reporting can be checked safely.
std::vector<std::wstring> arguments() {
  std::vector<std::wstring> result;
  const wchar_t* current = GetCommandLineW();
  while (*current) {
    while (*current == L' ' || *current == L'\t') ++current;
    if (!*current) break;
    std::wstring argument;
    bool quoted = false;
    while (*current && (quoted || (*current != L' ' && *current != L'\t'))) {
      if (*current == L'"') quoted = !quoted;
      else argument += *current;
      ++current;
    }
    result.push_back(argument);
  }
  return result;
}

int runDiagnosticMode(const std::vector<std::wstring>& args) {
  if (args[1] == L"--self-test" && args.size() == 2) {
    struct Case { const char* text; StartupStatus expected; };
    const Case checks[] = {
      {"SlinkyNative loaded into PID 123", StartupStatus::Waiting},
      {"Slinky is running", StartupStatus::Running},
      {"already running in this process; ignoring second injection", StartupStatus::AlreadyRunning},
      {"bootstrap failed; unloading", StartupStatus::Failed},
      {"Bootstrap.start: java.lang.Exception: example", StartupStatus::Failed},
      {"FINAL STATUS: RUNNING", StartupStatus::Running},
      {"FINAL STATUS: ALREADY_RUNNING", StartupStatus::AlreadyRunning},
      {"FINAL STATUS: FAILED", StartupStatus::Failed},
      {"FINAL STATUS: RELOADED", StartupStatus::Reloaded}
    };
    for (const auto& check : checks) {
      if (statusFromLine(check.text) != check.expected) {
        printf("SELF-TEST FAILED: %s\n", check.text); return 1;
      }
    }
    struct Sequence { const char* first; const char* second; StartupStatus expected; bool final; };
    const Sequence sequences[] = {
      {"already running in this process; ignoring second injection", "Slinky is running", StartupStatus::AlreadyRunning, false},
      {"Bootstrap.start: java.lang.Exception: example", "Slinky is running", StartupStatus::Failed, false},
      {"Slinky is running", "FINAL STATUS: RELOADED", StartupStatus::Reloaded, true},
      {"already running in this process; ignoring second injection", "FINAL STATUS: FAILED", StartupStatus::Failed, true},
      {"FINAL STATUS: RUNNING", "bootstrap failed; unloading", StartupStatus::Running, true},
      {"FINAL STATUS: ALREADY_RUNNING", "Slinky is running", StartupStatus::AlreadyRunning, true}
    };
    for (const auto& sequence : sequences) {
      LogReader log;
      log.consumeStatusLine(sequence.first);
      log.consumeStatusLine(sequence.second);
      if (log.status != sequence.expected || log.finalStatus != sequence.final) {
        printf("SELF-TEST FAILED: status sequence %s / %s\n", sequence.first, sequence.second);
        return 1;
      }
    }
    printf("SELF-TEST PASSED: 9 startup messages and 6 ordered status sequences. No game was opened or modified.\n");
    return 0;
  }
  if (args[1] == L"--test-log" && args.size() == 3) {
    print(L"Reading test log: " + args[2]);
    LogReader log;
    if (!log.read(args[2])) { windowsError(L"Reading the test log", log.error); return 1; }
    log.flushLastLine();
    reportStatus(log.status);
    if (log.status == StartupStatus::Waiting) printf("INCOMPLETE: This log never confirmed startup.\n");
    printf("Test mode finished. No game was opened or modified.\n");
    if (log.status == StartupStatus::Running || log.status == StartupStatus::Reloaded) return 0;
    return log.status == StartupStatus::AlreadyRunning ? 2 :
      log.status == StartupStatus::Waiting ? 3 : 1;
  }
  printf("Usage: Slinky.exe [--no-pause | --self-test | --test-log \"path to log\"]\n");
  return 1;
}
} // namespace

int WINAPI WinMain(HINSTANCE instance, HINSTANCE, LPSTR, int) {
  const auto args = arguments();
  automatedMode = args.size() == 2 && args[1] == L"--no-pause";
  openConsole(args.size() > 1);
  if (args.size() > 1 && !automatedMode) return finish(runDiagnosticMode(args), false);
  printf("Slinky startup diagnostics\n\n");
  if (!extractNative()) {
    printf("FAILED: Could not extract the bundled DLL.\n");
    return finish(1);
  }
  candidates = enumerateCandidates();
  printf("Found %zu visible Java game window(s).\n", candidates.size());
  for (const auto& candidate : candidates)
    print(L"  PID " + std::to_wstring(candidate.pid) + L": " + candidate.title);
  if (candidates.size() == 1) {
    DWORD pid = candidates.front().pid;
    ULONGLONG started = GetTickCount64();
    const int loaded = inject(pid, payloadPath);
    if (loaded == 1 && waitForStartup(pid, started)) return finish(0);
    if (loaded == 0) showAvailableStartupLog();
    if (loaded == 2 || lastStartupStatus == StartupStatus::AlreadyRunning) return finish(2);
    injectionError(nullptr);
    return finish(1);
  }
  printf(candidates.empty() ? "Waiting for a visible Minecraft window. Open Minecraft 1.8.9.\n" :
    "Select the Minecraft window in the process picker, then click Inject.\n");
  WNDCLASSEXW windowClass{};
  windowClass.cbSize = sizeof windowClass;
  windowClass.lpfnWndProc = launcherProcedure;
  windowClass.hInstance = instance;
  windowClass.hIcon = LoadIconW(nullptr, IDI_APPLICATION);
  windowClass.hCursor = LoadCursorW(nullptr, IDC_ARROW);
  windowClass.hbrBackground = reinterpret_cast<HBRUSH>(COLOR_BTNFACE + 1);
  windowClass.lpszClassName = L"SlinkyLauncher";
  if (!RegisterClassExW(&windowClass)) { windowsError(L"Registering the process picker"); return finish(1); }
  mainWindow = CreateWindowExW(WS_EX_APPWINDOW | WS_EX_TOPMOST, windowClass.lpszClassName,
    L"Slinky – Select Minecraft", WS_CAPTION | WS_SYSMENU | WS_MINIMIZEBOX,
    (GetSystemMetrics(SM_CXSCREEN) - 500) / 2, (GetSystemMetrics(SM_CYSCREEN) - 310) / 2,
    500, 310, nullptr, nullptr, instance, nullptr);
  if (!mainWindow) { windowsError(L"Opening the process picker"); return finish(1); }
  ShowWindow(mainWindow, SW_SHOW);
  UpdateWindow(mainWindow);
  MSG message{};
  while (GetMessageW(&message, nullptr, 0, 0) > 0) {
    TranslateMessage(&message);
    DispatchMessageW(&message);
  }
  DestroyWindow(mainWindow);
  return finish(injectionSucceeded ? 0 : lastStartupStatus == StartupStatus::AlreadyRunning ? 2 : 1);
}
