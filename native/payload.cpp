// Behavioral reconstruction of SlinkyNative.dll application code (0x180001000-0x18000399C).
// JNI/JVMTI types correct the function-pointer/type loss in the Hex-Rays evidence.
// Shared JNI/JVMTI core used by the Windows loader and Linux Attach API agent.
#ifdef _WIN32
#include <windows.h>
#else
#include <dlfcn.h>
#include <unistd.h>
#include <fcntl.h>
#include <ctime>
#endif
#include <jni.h>
#include <jvmti.h>
#include <algorithm>
#include <cstdarg>
#include <cerrno>
#include <cstdio>
#include <cstring>
#include <string>
#include <unordered_map>
#include <vector>
#include <atomic>
#include <chrono>
#include <codecvt>
#include <filesystem>
#include <fstream>
#include <locale>
#include <mutex>
#include <thread>

namespace {
#ifdef _WIN32
HMODULE moduleHandle{};
volatile LONG workerStarted{};
#else
std::atomic<bool> workerStarted{false};
std::string runDirectory;
bool bridgeEverRegistered{};
#endif
jvmtiEnv* tooling{};
std::mutex operationLock;
std::mutex logLock;
jclass captureTarget{}, replacementTarget{};  // Global references, valid during a locked operation.
std::vector<unsigned char> capturedBytes;
bool capturedNameMatches{};
const unsigned char* replacementBytes{};
jint replacementLength{};
bool replacementApplied{};

using ExclusiveLock = std::lock_guard<std::mutex>;

void pauseWorker(unsigned milliseconds) {
  std::this_thread::sleep_for(std::chrono::milliseconds(milliseconds));
}

#ifndef _WIN32
std::string utf8(const std::wstring& value) {
  return std::wstring_convert<std::codecvt_utf8<wchar_t>>{}.to_bytes(value);
}
std::wstring wide(const std::string& value) {
  return std::wstring_convert<std::codecvt_utf8<wchar_t>>{}.from_bytes(value);
}
#endif

// Original: 0x180002830. UTF-8 log beside the loaded DLL plus debugger output.
void log(const wchar_t* format, ...) {
  wchar_t body[2048]{};
  va_list arguments;
  va_start(arguments, format);
#ifdef _WIN32
  _vsnwprintf_s(body, _TRUNCATE, format, arguments);
#else
  std::vswprintf(body, std::size(body), format, arguments);
#endif
  va_end(arguments);
#ifdef _WIN32
  SYSTEMTIME time{};
  GetLocalTime(&time);
  wchar_t line[2304]{};
  _snwprintf_s(line, _TRUNCATE, L"[%04u-%02u-%02u %02u:%02u:%02u.%03u] %ls\r\n",
    time.wYear, time.wMonth, time.wDay, time.wHour, time.wMinute, time.wSecond, time.wMilliseconds, body);
  OutputDebugStringW(line);
  wchar_t fileName[MAX_PATH]{};
  DWORD length = GetModuleFileNameW(moduleHandle, fileName, MAX_PATH);
  if (!length || length >= MAX_PATH) return;
  wchar_t* separator = wcsrchr(fileName, L'\\');
  if (!separator) return;
  *separator = 0;
  std::wstring path = std::wstring(fileName) + L"\\slinky-native.log";
  ExclusiveLock guard(logLock);
  FILE* file{};
  if (!_wfopen_s(&file, path.c_str(), L"a, ccs=UTF-8") && file) {
    fputws(line, file);
    fclose(file);
  }
#else
  auto now = std::chrono::system_clock::now();
  auto timestamp = std::chrono::system_clock::to_time_t(now);
  std::tm time{};
  localtime_r(&timestamp, &time);
  char stamp[64]{};
  std::strftime(stamp, sizeof stamp, "[%Y-%m-%d %H:%M:%S] ", &time);
  std::string line;
  try { line = std::string(stamp) + utf8(body) + "\n"; }
  catch (...) { line = std::string(stamp) + "<unreadable log message>\n"; }
  ExclusiveLock guard(logLock);
  if (runDirectory.empty()) return;
  std::ofstream file(std::filesystem::path(runDirectory) / "slinky-native.log", std::ios::app);
  if (file) file << line;
#endif
}

std::wstring javaText(JNIEnv* env, jstring string, size_t maximum = 2047) {
  if (!string) return {};
  const jchar* characters = env->GetStringChars(string, nullptr);
  if (!characters) return {};
  size_t length = std::min(maximum, static_cast<size_t>(env->GetStringLength(string)));
  std::wstring result;
#ifdef _WIN32
  result.assign(reinterpret_cast<const wchar_t*>(characters), length);
#else
  for (size_t index = 0; index < length; ++index) {
    unsigned codepoint = characters[index];
    if (codepoint >= 0xD800 && codepoint <= 0xDBFF && index + 1 < length &&
        characters[index + 1] >= 0xDC00 && characters[index + 1] <= 0xDFFF) {
      codepoint = 0x10000 + ((codepoint - 0xD800) << 10) + (characters[++index] - 0xDC00);
    } else if (codepoint >= 0xD800 && codepoint <= 0xDFFF) codepoint = 0xFFFD;
    result.push_back(static_cast<wchar_t>(codepoint));
  }
#endif
  env->ReleaseStringChars(string, characters);
  return result;
}

jstring javaPath(JNIEnv* env, const std::wstring& path) {
#ifdef _WIN32
  return env->NewString(reinterpret_cast<const jchar*>(path.data()), static_cast<jsize>(path.size()));
#else
  std::vector<jchar> characters;
  for (wchar_t character : path) {
    unsigned codepoint = static_cast<unsigned>(character);
    if (codepoint > 0xFFFF && codepoint <= 0x10FFFF) {
      codepoint -= 0x10000;
      characters.push_back(static_cast<jchar>(0xD800 + (codepoint >> 10)));
      characters.push_back(static_cast<jchar>(0xDC00 + (codepoint & 0x3FF)));
    } else characters.push_back(static_cast<jchar>(codepoint));
  }
  return env->NewString(characters.data(), static_cast<jsize>(characters.size()));
#endif
}

void exceptionLog(JNIEnv* env, const wchar_t* operation) {
  if (!env || !env->ExceptionCheck()) { log(L"%ls failed", operation); return; }
  jthrowable exception = env->ExceptionOccurred();
  env->ExceptionClear();
  jclass throwable = env->FindClass("java/lang/Throwable");
  jmethodID toString = throwable ? env->GetMethodID(throwable, "toString", "()Ljava/lang/String;") : nullptr;
  jstring text = toString ? static_cast<jstring>(env->CallObjectMethod(exception, toString)) : nullptr;
  if (text && !env->ExceptionCheck()) {
    auto message = javaText(env, text, 1023);
    log(L"%ls: %ls", operation, message.c_str());
  } else {
    env->ExceptionClear();
    log(L"%ls failed with an unreadable exception", operation);
  }
  if (text) env->DeleteLocalRef(text);
  if (throwable) env->DeleteLocalRef(throwable);
  if (exception) env->DeleteLocalRef(exception);
  if (env->ExceptionCheck()) env->ExceptionClear();
}

void toolingError(const wchar_t* operation, jvmtiError error, jclass target = nullptr) {
  if (!tooling || error == JVMTI_ERROR_NONE) return;
  char* name{};
  char* signature{};
  tooling->GetErrorName(error, &name);
  if (target) tooling->GetClassSignature(target, &signature, nullptr);
  log(L"%ls failed: jvmti=%d (%hs), target=%hs", operation, static_cast<int>(error),
    name ? name : "unknown", signature ? signature : "<unknown>");
  if (name) tooling->Deallocate(reinterpret_cast<unsigned char*>(name));
  if (signature) tooling->Deallocate(reinterpret_cast<unsigned char*>(signature));
}

// Original: 0x180002BF0. Parse this_class, not merely a string search for its name.
bool classNameMatches(jclass target, const unsigned char* bytes, jint length) {
  if (!tooling || !target || !bytes || length < 10 || bytes[0] != 0xCA || bytes[1] != 0xFE ||
      bytes[2] != 0xBA || bytes[3] != 0xBE) return false;
  const size_t size = static_cast<size_t>(length);
  auto read16 = [&](size_t offset) -> unsigned { return (bytes[offset] << 8) | bytes[offset + 1]; };
  unsigned count = read16(8);
  if (count < 2) return false;
  std::vector<unsigned> classNames(count);
  std::vector<std::string> utf8(count);
  size_t cursor = 10;
  for (unsigned index = 1; index < count; ++index) {
    if (cursor >= size) return false;
    unsigned tag = bytes[cursor++];
    size_t skip{};
    switch (tag) {
    case 1: {
      if (cursor + 2 > size) return false;
      unsigned stringLength = read16(cursor);
      cursor += 2;
      if (cursor + stringLength > size) return false;
      utf8[index].assign(reinterpret_cast<const char*>(bytes + cursor), stringLength);
      cursor += stringLength;
      continue;
    }
    case 7:
      if (cursor + 2 > size) return false;
      classNames[index] = read16(cursor);
      skip = 2;
      break;
    case 3: case 4: case 9: case 10: case 11: case 12: case 17: case 18: skip = 4; break;
    case 5: case 6: skip = 8; ++index; break;
    case 8: case 16: case 19: case 20: skip = 2; break;
    case 15: skip = 3; break;
    default: return false;
    }
    if (cursor + skip > size) return false;
    cursor += skip;
  }
  if (cursor + 4 > size) return false;
  unsigned thisClass = read16(cursor + 2);
  if (!thisClass || thisClass >= count) return false;
  unsigned nameIndex = classNames[thisClass];
  if (!nameIndex || nameIndex >= count || utf8[nameIndex].empty()) return false;
  char* signature{};
  if (tooling->GetClassSignature(target, &signature, nullptr) != JVMTI_ERROR_NONE || !signature) return false;
  std::string expected = "L" + utf8[nameIndex] + ";";
  bool matches = expected == signature;
  tooling->Deallocate(reinterpret_cast<unsigned char*>(signature));
  return matches;
}

void JNICALL classFileLoadHook(jvmtiEnv* jvmti, JNIEnv* env, jclass redefining,
    jobject, const char*, jobject, jint length, const unsigned char* bytes,
    jint* newLength, unsigned char** newBytes) {
  if (!env || !redefining || !bytes || length <= 0) return;
  bool sameName = classNameMatches(redefining, bytes, length);
  if (replacementTarget && env->IsSameObject(redefining, replacementTarget) && sameName) {
    unsigned char* allocation{};
    if (jvmti->Allocate(replacementLength, &allocation) == JVMTI_ERROR_NONE && allocation) {
      std::memcpy(allocation, replacementBytes, static_cast<size_t>(replacementLength));
      *newLength = replacementLength;
      *newBytes = allocation;
      replacementApplied = true;
    }
  } else if (captureTarget && env->IsSameObject(redefining, captureTarget)) {
    if (capturedBytes.empty() || !capturedNameMatches || sameName) {
      capturedBytes.assign(bytes, bytes + length);
      capturedNameMatches = sameName;
    }
  }
}

jbyteArray JNICALL getClassBytes(JNIEnv* env, jclass, jclass target) {
  if (!tooling || !target) return nullptr;
  ExclusiveLock guard(operationLock);
  capturedBytes.clear();
  capturedNameMatches = false;
  captureTarget = static_cast<jclass>(env->NewGlobalRef(target));
  if (!captureTarget) return nullptr;
  tooling->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_CLASS_FILE_LOAD_HOOK, nullptr);
  jvmtiError error = tooling->RetransformClasses(1, &target);
  tooling->SetEventNotificationMode(JVMTI_DISABLE, JVMTI_EVENT_CLASS_FILE_LOAD_HOOK, nullptr);
  jbyteArray result{};
  if (error == JVMTI_ERROR_NONE && !capturedBytes.empty()) {
    result = env->NewByteArray(static_cast<jsize>(capturedBytes.size()));
    if (result) env->SetByteArrayRegion(result, 0, static_cast<jsize>(capturedBytes.size()),
      reinterpret_cast<const jbyte*>(capturedBytes.data()));
  } else toolingError(L"RetransformClasses", error, target);
  env->DeleteGlobalRef(captureTarget);
  captureTarget = nullptr;
  capturedBytes.clear();
  return result;
}

jint JNICALL retransformClass(JNIEnv* env, jclass, jclass target, jbyteArray bytes) {
  if (!tooling || !target || !bytes) return JVMTI_ERROR_INVALID_ENVIRONMENT;
  jbyte* data = env->GetByteArrayElements(bytes, nullptr);
  if (!data) return JVMTI_ERROR_OUT_OF_MEMORY;
  jint errorResult{};
  {
    ExclusiveLock guard(operationLock);
    replacementTarget = static_cast<jclass>(env->NewGlobalRef(target));
    replacementBytes = reinterpret_cast<const unsigned char*>(data);
    replacementLength = env->GetArrayLength(bytes);
    replacementApplied = false;
    if (!replacementTarget) errorResult = JVMTI_ERROR_OUT_OF_MEMORY;
    else {
      tooling->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_CLASS_FILE_LOAD_HOOK, nullptr);
      jvmtiError error = tooling->RetransformClasses(1, &target);
      tooling->SetEventNotificationMode(JVMTI_DISABLE, JVMTI_EVENT_CLASS_FILE_LOAD_HOOK, nullptr);
      if (error != JVMTI_ERROR_NONE) {
        toolingError(L"RetransformClasses(apply)", error, target);
        errorResult = error;
      } else if (!replacementApplied) {
        log(L"RetransformClasses(apply): load hook never matched the target; class unchanged");
        errorResult = -1;
      }
    }
    if (replacementTarget) env->DeleteGlobalRef(replacementTarget);
    replacementTarget = nullptr;
    replacementBytes = nullptr;
    replacementLength = 0;
    replacementApplied = false;
  }
  env->ReleaseByteArrayElements(bytes, data, JNI_ABORT);
  return errorResult;
}

void JNICALL javaLog(JNIEnv* env, jclass, jstring string) {
  if (!string) return;
  auto text = javaText(env, string);
  log(L"[java] %ls", text.c_str());
}

bool initializeTooling(JavaVM* vm) {
#ifndef _WIN32
  // NativeBridge keeps using this environment after the attach worker exits.
  // GetEnv creates another JVMTI environment, so reuse the working one on reattach.
  if (tooling) return true;
#endif
  if (vm->GetEnv(reinterpret_cast<void**>(&tooling), JVMTI_VERSION_1_2) != JNI_OK || !tooling) {
    tooling = nullptr;
    log(L"JVMTI 1.2 is unavailable");
    return false;
  }
  jvmtiCapabilities potential{}, required{};
  jvmtiError error = tooling->GetPotentialCapabilities(&potential);
  if (error != JVMTI_ERROR_NONE) { log(L"GetPotentialCapabilities failed: %d", error); return false; }
  if (!potential.can_retransform_classes) { log(L"JVM cannot retransform classes"); return false; }
  required.can_retransform_classes = 1;
  required.can_retransform_any_class = potential.can_retransform_any_class;
  required.can_redefine_classes = potential.can_redefine_classes;
  required.can_redefine_any_class = potential.can_redefine_any_class;
  error = tooling->AddCapabilities(&required);
  if (error != JVMTI_ERROR_NONE) { log(L"AddCapabilities failed: %d", error); return false; }
  jvmtiEventCallbacks callbacks{};
  callbacks.ClassFileLoadHook = classFileLoadHook;
  error = tooling->SetEventCallbacks(&callbacks, sizeof callbacks);
  if (error != JVMTI_ERROR_NONE) { log(L"SetEventCallbacks failed: %d", error); return false; }
  return true;
}

jclass loadedClass(JNIEnv* env, const char* wantedSignature) {
  jint count{};
  jclass* classes{};
  if (tooling->GetLoadedClasses(&count, &classes) != JVMTI_ERROR_NONE || !classes) return nullptr;
  jclass result{};
  for (jint index = 0; index < count; ++index) {
    if (!result) {
      char* signature{};
      if (tooling->GetClassSignature(classes[index], &signature, nullptr) == JVMTI_ERROR_NONE && signature) {
        if (!std::strcmp(signature, wantedSignature)) result = static_cast<jclass>(env->NewLocalRef(classes[index]));
        tooling->Deallocate(reinterpret_cast<unsigned char*>(signature));
      }
    }
    env->DeleteLocalRef(classes[index]);
  }
  tooling->Deallocate(reinterpret_cast<unsigned char*>(classes));
  return result;
}

// -1 means the field does not exist, 0 means not yet initialized, 1 means ready.
int singletonReady(JNIEnv* env, jclass type, const char* fieldName, const char* descriptor) {
  jfieldID field = env->GetStaticFieldID(type, fieldName, descriptor);
  if (!field) { if (env->ExceptionCheck()) env->ExceptionClear(); return -1; }
  jobject singleton = env->GetStaticObjectField(type, field);
  if (!singleton) return 0;
  env->DeleteLocalRef(singleton);
  return 1;
}

struct Runtime { const char* name{}; int resource{}; jclass minecraft{}; jobject loader{}; };
bool detectRuntime(JNIEnv* env, Runtime& runtime) {
  int status = 0;
  for (int attempt = 0; attempt < 1200; ++attempt) {
    if (env->PushLocalFrame(64) < 0) return false;
    jclass minecraft = loadedClass(env, "Lnet/minecraft/client/Minecraft;");
    if (minecraft) {
      status = singletonReady(env, minecraft, "theMinecraft", "Lnet/minecraft/client/Minecraft;");
      runtime.name = "named";
      runtime.resource = 101;
      if (status < 0) {
        status = singletonReady(env, minecraft, "field_71432_P", "Lnet/minecraft/client/Minecraft;");
        runtime.name = "srg";
        runtime.resource = 102;
        if (status < 0) log(L"net.minecraft.client.Minecraft has neither MCP nor SRG members; unsupported runtime");
      }
    } else {
      minecraft = loadedClass(env, "Lave;");
      if (minecraft) {
        status = singletonReady(env, minecraft, "S", "Lave;");
        runtime.name = "official";
        runtime.resource = 103;
        if (status < 0) log(L"class ave is not Minecraft 1.8.9; unsupported runtime");
      } else status = 0;
    }
    if (status != 0) {
      runtime.minecraft = static_cast<jclass>(env->PopLocalFrame(minecraft));
      break;
    }
    env->PopLocalFrame(nullptr);
    pauseWorker(100);
  }
  if (status != 1) {
    log(status == 0 ? L"Minecraft 1.8.9 did not finish starting within 2 minutes" : L"unsupported game runtime");
    return false;
  }
  if (tooling->GetClassLoader(runtime.minecraft, &runtime.loader) != JVMTI_ERROR_NONE) runtime.loader = nullptr;
  log(L"detected Minecraft 1.8.9 with %hs names", runtime.name);
  return true;
}

bool extractPayload(const Runtime& runtime, std::wstring& result) {
#ifdef _WIN32
  HRSRC resource = FindResourceW(moduleHandle, MAKEINTRESOURCEW(runtime.resource), RT_RCDATA);
  if (!resource) { log(L"embedded payload %d is missing", runtime.resource); return false; }
  DWORD size = SizeofResource(moduleHandle, resource);
  HGLOBAL loaded = LoadResource(moduleHandle, resource);
  const auto* bytes = loaded ? static_cast<const unsigned char*>(LockResource(loaded)) : nullptr;
  if (!bytes || size < 4 || bytes[0] != 'P' || bytes[1] != 'K') {
    log(L"embedded payload %d is invalid", runtime.resource);
    return false;
  }
  wchar_t dllPath[MAX_PATH]{};
  DWORD dllLength = GetModuleFileNameW(moduleHandle, dllPath, MAX_PATH);
  wchar_t* separator = dllLength && dllLength < MAX_PATH ? wcsrchr(dllPath, L'\\') : nullptr;
  if (!separator) return false;
  *separator = 0;
  std::wstring directory = dllPath;
  wchar_t fileName[128]{};
  _snwprintf_s(fileName, _TRUNCATE, L"slinky-%hs-%lu-%llu.jar", runtime.name,
    GetCurrentProcessId(), static_cast<unsigned long long>(GetTickCount64()));
  result = directory + L"\\" + fileName;
  HANDLE file = CreateFileW(result.c_str(), GENERIC_WRITE, FILE_SHARE_READ | FILE_SHARE_DELETE, nullptr,
    CREATE_NEW, FILE_ATTRIBUTE_TEMPORARY, nullptr);
  if (file == INVALID_HANDLE_VALUE) { log(L"cannot write %ls: %lu", result.c_str(), GetLastError()); return false; }
  DWORD written{};
  bool success = WriteFile(file, bytes, size, &written, nullptr) && written == size;
  CloseHandle(file);
  if (!success) { DeleteFileW(result.c_str()); log(L"writing %ls failed", result.c_str()); return false; }
  log(L"payload %hs written to %ls (%lu bytes)", runtime.name, result.c_str(), size);
  return true;
#else
  Dl_info info{};
  if (!dladdr(reinterpret_cast<void*>(&extractPayload), &info) || !info.dli_fname) {
    log(L"cannot locate the native agent library");
    return false;
  }
  try {
    auto source = std::filesystem::path(info.dli_fname).parent_path() /
      ("payload-" + std::to_string(runtime.resource) + ".jar");
    std::ifstream input(source, std::ios::binary);
    char header[4]{};
    input.read(header, sizeof header);
    if (input.gcount() != sizeof header || header[0] != 'P' || header[1] != 'K') {
      log(L"payload %d beside the agent is missing or invalid", runtime.resource);
      return false;
    }
    input.clear();
    input.seekg(0);
    std::string directory;
    { ExclusiveLock guard(logLock); directory = runDirectory; }
    std::string pattern = (std::filesystem::path(directory) /
      (std::string("slinky-") + runtime.name + "-XXXXXX.jar")).string();
    std::vector<char> name(pattern.begin(), pattern.end());
    name.push_back('\0');
    int descriptor = mkstemps(name.data(), 4);
    if (descriptor < 0) { log(L"cannot create a per-run payload jar"); return false; }
    bool success = true;
    char buffer[16384];
    while (input) {
      input.read(buffer, sizeof buffer);
      auto remaining = input.gcount();
      const char* cursor = buffer;
      while (remaining > 0) {
        auto written = ::write(descriptor, cursor, static_cast<size_t>(remaining));
        if (written < 0 && errno == EINTR) continue;
        if (written <= 0) { success = false; break; }
        remaining -= written;
        cursor += written;
      }
      if (!success) break;
    }
    if (input.bad()) success = false;
    if (::close(descriptor)) success = false;
    if (!success) {
      ::unlink(name.data());
      log(L"copying the payload jar failed");
      return false;
    }
    result = wide(name.data());
    log(L"payload %hs copied to %ls", runtime.name, result.c_str());
    return true;
  } catch (const std::exception& error) {
    log(L"extractPayload failed: %hs", error.what());
    return false;
  }
#endif
}

jbyteArray readStream(JNIEnv* env, jobject input) {
  jclass inputClass = env->FindClass("java/io/InputStream");
  jclass outputClass = env->FindClass("java/io/ByteArrayOutputStream");
  if (!inputClass || !outputClass) return nullptr;
  jmethodID read = env->GetMethodID(inputClass, "read", "([B)I");
  jmethodID close = env->GetMethodID(inputClass, "close", "()V");
  jmethodID constructor = env->GetMethodID(outputClass, "<init>", "()V");
  jmethodID write = env->GetMethodID(outputClass, "write", "([BII)V");
  jmethodID toBytes = env->GetMethodID(outputClass, "toByteArray", "()[B");
  if (!read || !close || !constructor || !write || !toBytes) return nullptr;
  jobject output = env->NewObject(outputClass, constructor);
  jbyteArray buffer = env->NewByteArray(16384);
  if (!output || !buffer) return nullptr;
  for (;;) {
    jint length = env->CallIntMethod(input, read, buffer);
    if (env->ExceptionCheck() || length < 0) break;
    env->CallVoidMethod(output, write, buffer, 0, length);
    if (env->ExceptionCheck()) break;
  }
  if (env->ExceptionCheck()) return nullptr;
  env->CallVoidMethod(input, close);
  jbyteArray result = static_cast<jbyteArray>(env->CallObjectMethod(output, toBytes));
  env->DeleteLocalRef(buffer);
  env->DeleteLocalRef(output);
  return result;
}

struct PayloadClass { std::string name; jbyteArray bytes{}; };

void releasePayloadClasses(JNIEnv* env, std::vector<PayloadClass>& classes) {
  for (auto& candidate : classes) if (candidate.bytes) env->DeleteGlobalRef(candidate.bytes);
  classes.clear();
}

bool readJarClasses(JNIEnv* env, const std::wstring& path, std::vector<PayloadClass>& pending) {
  jclass jarClass = env->FindClass("java/util/jar/JarFile");
  jclass entryClass = env->FindClass("java/util/zip/ZipEntry");
  jclass enumerationClass = env->FindClass("java/util/Enumeration");
  if (!jarClass || !entryClass || !enumerationClass) { exceptionLog(env, L"resolve JAR reader"); return false; }
  jmethodID constructor = env->GetMethodID(jarClass, "<init>", "(Ljava/lang/String;)V");
  jmethodID entries = env->GetMethodID(jarClass, "entries", "()Ljava/util/Enumeration;");
  jmethodID stream = env->GetMethodID(jarClass, "getInputStream", "(Ljava/util/zip/ZipEntry;)Ljava/io/InputStream;");
  jmethodID close = env->GetMethodID(jarClass, "close", "()V");
  jmethodID entryName = env->GetMethodID(entryClass, "getName", "()Ljava/lang/String;");
  jmethodID hasMore = env->GetMethodID(enumerationClass, "hasMoreElements", "()Z");
  jmethodID next = env->GetMethodID(enumerationClass, "nextElement", "()Ljava/lang/Object;");
  if (!constructor || !entries || !stream || !close || !entryName || !hasMore || !next) {
    exceptionLog(env, L"resolve JAR reader methods"); return false;
  }
  jstring jarPath = javaPath(env, path);
  jobject jar = env->NewObject(jarClass, constructor, jarPath);
  if (!jar) { exceptionLog(env, L"open payload jar"); return false; }
  jobject enumeration = env->CallObjectMethod(jar, entries);
  bool readOk = enumeration != nullptr && !env->ExceptionCheck();
  while (readOk && env->CallBooleanMethod(enumeration, hasMore)) {
    if (env->PushLocalFrame(32) < 0) { readOk = false; break; }
    jobject entry = env->CallObjectMethod(enumeration, next);
    jstring name = entry ? static_cast<jstring>(env->CallObjectMethod(entry, entryName)) : nullptr;
    const char* encoded = name ? env->GetStringUTFChars(name, nullptr) : nullptr;
    std::string className = encoded ? encoded : "";
    if (encoded) env->ReleaseStringUTFChars(name, encoded);
    if (className.compare(0, 11, "dev/slinky/") == 0 && className.size() > 6 &&
        className.compare(className.size() - 6, 6, ".class") == 0) {
      jobject input = env->CallObjectMethod(jar, stream, entry);
      jbyteArray data = input ? readStream(env, input) : nullptr;
      if (!data || env->ExceptionCheck()) readOk = false;
      else {
        className.resize(className.size() - 6);
        auto global = static_cast<jbyteArray>(env->NewGlobalRef(data));
        if (!global) readOk = false;
        else pending.push_back({std::move(className), global});
      }
    }
    if (env->ExceptionCheck()) readOk = false;
    env->PopLocalFrame(nullptr);
  }
  if (env->ExceptionCheck()) { exceptionLog(env, L"read payload classes"); readOk = false; }
  env->CallVoidMethod(jar, close);
  if (env->ExceptionCheck()) { exceptionLog(env, L"close payload jar"); readOk = false; }
  env->DeleteLocalRef(enumeration);
  env->DeleteLocalRef(jar);
  env->DeleteLocalRef(jarPath);
  env->DeleteLocalRef(jarClass);
  env->DeleteLocalRef(entryClass);
  env->DeleteLocalRef(enumerationClass);
  if (pending.empty()) { log(L"payload contains no dev.slinky classes"); readOk = false; }
  return readOk;
}

// Snapshot references are global: hundreds of classes must outlive local JNI frames.
using LoadedPayload = std::unordered_map<std::string, jclass>;
void releaseLoadedPayload(JNIEnv* env, LoadedPayload& classes) {
  for (auto& entry : classes) env->DeleteGlobalRef(entry.second);
  classes.clear();
}

bool loadedPayloadClasses(JNIEnv* env, jobject wantedLoader, LoadedPayload& result) {
  jint count{};
  jclass* classes{};
  jvmtiError error = tooling->GetLoadedClasses(&count, &classes);
  if (error != JVMTI_ERROR_NONE) { toolingError(L"GetLoadedClasses(payload)", error); return false; }
  bool ok = true;
  for (jint index = 0; index < count; ++index) {
    char* signature{};
    if (ok && tooling->GetClassSignature(classes[index], &signature, nullptr) == JVMTI_ERROR_NONE && signature) {
      std::string name(signature);
      if (name.compare(0, 12, "Ldev/slinky/") == 0 && name.back() == ';') {
        jobject loader{};
        if (tooling->GetClassLoader(classes[index], &loader) != JVMTI_ERROR_NONE) ok = false;
        else if (env->IsSameObject(loader, wantedLoader)) {
          jclass global = static_cast<jclass>(env->NewGlobalRef(classes[index]));
          if (!global) ok = false;
          else result.emplace(name.substr(1, name.size() - 2), global);
        }
        if (loader) env->DeleteLocalRef(loader);
      }
    }
    if (signature) tooling->Deallocate(reinterpret_cast<unsigned char*>(signature));
    env->DeleteLocalRef(classes[index]);
  }
  if (classes) tooling->Deallocate(reinterpret_cast<unsigned char*>(classes));
  return ok;
}

// FindClass can resolve through an obsolete JAR; findLoadedClass does not load anything.
jclass loaderDefinedClass(JNIEnv* env, jobject loader, const std::string& name) {
  jclass loaderType = env->FindClass("java/lang/ClassLoader");
  jmethodID find = loaderType ? env->GetMethodID(loaderType, "findLoadedClass", "(Ljava/lang/String;)Ljava/lang/Class;") : nullptr;
  std::string dotted = name;
  std::replace(dotted.begin(), dotted.end(), '/', '.');
  jstring text = env->NewStringUTF(dotted.c_str());
  jclass result = find && text ? static_cast<jclass>(env->CallObjectMethod(loader, find, text)) : nullptr;
  if (text) env->DeleteLocalRef(text);
  if (loaderType) env->DeleteLocalRef(loaderType);
  return result;
}

// Define every missing class from the new JAR so an older loader URL cannot supply it.
bool defineMissingClasses(JNIEnv* env, jobject loader, const std::vector<PayloadClass>& pending) {
  jclass missingDependency = env->FindClass("java/lang/NoClassDefFoundError");
  if (!missingDependency) { exceptionLog(env, L"resolve missing-dependency type"); return false; }
  std::vector<bool> done(pending.size());
  size_t remaining = pending.size();
  size_t definedCount = 0;
  bool readOk = true;
  bool progress = true;
  while (readOk && remaining && progress) {
    progress = false;
    for (size_t index = 0; index < pending.size(); ++index) {
      const auto& candidate = pending[index];
      if (done[index]) continue;
      jclass present = loaderDefinedClass(env, loader, candidate.name);
      if (env->ExceptionCheck()) { exceptionLog(env, L"findLoadedClass(payload)"); readOk = false; break; }
      if (present) {
        env->DeleteLocalRef(present);
        done[index] = true;
        --remaining;
        progress = true;
        continue;
      }
      jsize size = env->GetArrayLength(candidate.bytes);
      jbyte* data = env->GetByteArrayElements(candidate.bytes, nullptr);
      if (!data) { readOk = false; break; }
      jclass defined = env->DefineClass(candidate.name.c_str(), loader, data, size);
      env->ReleaseByteArrayElements(candidate.bytes, data, JNI_ABORT);
      bool retry = false;
      if (!defined) {
        jthrowable exception = env->ExceptionOccurred();
        env->ExceptionClear();
        retry = exception && missingDependency && env->IsInstanceOf(exception, missingDependency);
        if (!retry) {
          if (exception) env->Throw(exception);
          log(L"definition failed for payload class %hs", candidate.name.c_str());
          exceptionLog(env, L"DefineClass(payload)");
          readOk = false;
        }
        if (exception) env->DeleteLocalRef(exception);
      } else { env->DeleteLocalRef(defined); ++definedCount; }
      if (!readOk) break;
      if (retry) continue;
      done[index] = true;
      --remaining;
      progress = true;
    }
  }
  for (size_t index = 0; index < pending.size(); ++index)
    if (!done[index]) log(L"could not define payload class %hs", pending[index].name.c_str());
  env->DeleteLocalRef(missingDependency);
  log(L"defined %zu new payload classes in the game class loader; %zu unresolved", definedCount, remaining);
  return readOk && remaining == 0;
}

bool defineJarClasses(JNIEnv* env, jobject loader, const std::wstring& path) {
  std::vector<PayloadClass> classes;
  bool ok = readJarClasses(env, path, classes) && defineMissingClasses(env, loader, classes);
  releasePayloadClasses(env, classes);
  return ok;
}

// JVM 8 refuses modifier changes even when a constructor has no floating-point work.
// The early reconstructed build omitted ACC_STRICT on twelve such constructors.
// Preserve that single old modifier in the in-memory replacement, never in the JAR.
// Every other modifier difference fails the whole replacement before RedefineClasses.
bool compatibleMethodModifiers(jclass target, const std::string& className,
    jbyte* mutableBytes, jsize length, size_t& adaptedConstructors) {
  if (!mutableBytes || length < 10) return false;
  auto* bytes = reinterpret_cast<unsigned char*>(mutableBytes);
  size_t cursor = 0;
  const size_t size = static_cast<size_t>(length);
  auto available = [&](size_t count) { return cursor <= size && count <= size - cursor; };
  auto read16 = [&]() -> unsigned {
    unsigned result = (static_cast<unsigned>(bytes[cursor]) << 8) | bytes[cursor + 1];
    cursor += 2;
    return result;
  };
  auto read32 = [&]() -> size_t {
    size_t result = (static_cast<size_t>(bytes[cursor]) << 24) |
      (static_cast<size_t>(bytes[cursor + 1]) << 16) |
      (static_cast<size_t>(bytes[cursor + 2]) << 8) | bytes[cursor + 3];
    cursor += 4;
    return result;
  };
  auto skipAttributes = [&]() -> bool {
    if (!available(2)) return false;
    unsigned count = read16();
    for (unsigned index = 0; index < count; ++index) {
      if (!available(6)) return false;
      read16();
      size_t attributeLength = read32();
      if (!available(attributeLength)) return false;
      cursor += attributeLength;
    }
    return true;
  };
  if (bytes[0] != 0xCA || bytes[1] != 0xFE || bytes[2] != 0xBA || bytes[3] != 0xBE) return false;
  cursor = 8;
  unsigned constants = read16();
  if (constants < 2) return false;
  std::vector<std::string> strings(constants);
  for (unsigned index = 1; index < constants; ++index) {
    if (!available(1)) return false;
    unsigned tag = bytes[cursor++];
    size_t skip{};
    switch (tag) {
    case 1: {
      if (!available(2)) return false;
      size_t textLength = read16();
      if (!available(textLength)) return false;
      strings[index].assign(reinterpret_cast<const char*>(bytes + cursor), textLength);
      cursor += textLength;
      continue;
    }
    case 3: case 4: case 9: case 10: case 11: case 12: case 17: case 18: skip = 4; break;
    case 5: case 6: skip = 8; ++index; break;
    case 7: case 8: case 16: case 19: case 20: skip = 2; break;
    case 15: skip = 3; break;
    default: return false;
    }
    if (!available(skip)) return false;
    cursor += skip;
  }
  if (!available(8)) return false;
  cursor += 6; // access_flags, this_class, super_class
  size_t interfaces = read16();
  if (!available(interfaces * 2)) return false;
  cursor += interfaces * 2;
  if (!available(2)) return false;
  unsigned fields = read16();
  for (unsigned index = 0; index < fields; ++index) {
    if (!available(6)) return false;
    cursor += 6;
    if (!skipAttributes()) return false;
  }
  if (!available(2)) return false;
  unsigned methodCount = read16();
  struct MethodLocation { size_t flagsOffset; unsigned flags; std::string name; };
  std::unordered_map<std::string, MethodLocation> locations;
  for (unsigned index = 0; index < methodCount; ++index) {
    if (!available(6)) return false;
    size_t flagsOffset = cursor;
    unsigned flags = read16();
    unsigned nameIndex = read16();
    unsigned descriptorIndex = read16();
    if (!nameIndex || nameIndex >= strings.size() || !descriptorIndex || descriptorIndex >= strings.size() ||
        strings[nameIndex].empty() || strings[descriptorIndex].empty()) return false;
    std::string key = strings[nameIndex] + "\n" + strings[descriptorIndex];
    if (!locations.emplace(key, MethodLocation{flagsOffset, flags, strings[nameIndex]}).second) return false;
    if (!skipAttributes()) return false;
  }
  if (!skipAttributes() || cursor != size) return false;

  jint oldMethodCount{};
  jmethodID* oldMethods{};
  jvmtiError error = tooling->GetClassMethods(target, &oldMethodCount, &oldMethods);
  if (error == JVMTI_ERROR_CLASS_NOT_PREPARED) {
    // Fresh DefineClass entries can remain unprepared. The atomic JVM check still
    // validates their full schema; no modifiers are guessed or changed here.
    log(L"class %hs is not prepared; retaining fresh modifiers for JVM batch validation", className.c_str());
    return true;
  }
  if (error != JVMTI_ERROR_NONE) {
    toolingError(L"GetClassMethods(modifier compatibility)", error, target);
    return false;
  }
  bool ok = true;
  for (jint index = 0; index < oldMethodCount && ok; ++index) {
    char* name{};
    char* descriptor{};
    error = tooling->GetMethodName(oldMethods[index], &name, &descriptor, nullptr);
    jint modifiers{};
    if (error == JVMTI_ERROR_NONE) error = tooling->GetMethodModifiers(oldMethods[index], &modifiers);
    if (error != JVMTI_ERROR_NONE || !name || !descriptor) {
      toolingError(L"GetMethodModifiers(compatibility)", error, target);
      ok = false;
    } else {
      auto found = locations.find(std::string(name) + "\n" + descriptor);
      if (found == locations.end()) {
        log(L"same-session reload failed: replacement omits method %hs.%hs%hs",
          className.c_str(), name, descriptor);
        ok = false;
      } else {
        auto& method = found->second;
        unsigned oldFlags = static_cast<unsigned>(modifiers) & 0xFFFF;
        unsigned difference = oldFlags ^ method.flags;
        if (difference) {
          if (method.name == "<init>" && difference == 0x0800) {
            bytes[method.flagsOffset] = static_cast<unsigned char>(oldFlags >> 8);
            bytes[method.flagsOffset + 1] = static_cast<unsigned char>(oldFlags & 0xFF);
            ++adaptedConstructors;
            log(L"preserved existing ACC_STRICT constructor flag for %hs%hs (0x%04x)",
              className.c_str(), descriptor, oldFlags);
          } else {
            log(L"same-session reload failed: unsupported modifier change %hs.%hs%hs (0x%04x -> 0x%04x)",
              className.c_str(), name, descriptor, oldFlags, method.flags);
            ok = false;
          }
        }
        locations.erase(found);
      }
    }
    if (name) tooling->Deallocate(reinterpret_cast<unsigned char*>(name));
    if (descriptor) tooling->Deallocate(reinterpret_cast<unsigned char*>(descriptor));
  }
  if (oldMethods) tooling->Deallocate(reinterpret_cast<unsigned char*>(oldMethods));
  if (ok && !locations.empty()) {
    log(L"same-session reload failed: replacement adds methods to %hs", className.c_str());
    ok = false;
  }
  return ok;
}

bool replaceInactivePayload(JNIEnv* env, jobject loader, const std::wstring& path) {
  jvmtiCapabilities capabilities{};
  if (tooling->GetCapabilities(&capabilities) != JVMTI_ERROR_NONE || !capabilities.can_redefine_classes) {
    log(L"same-session reload failed: JVM class replacement is unavailable");
    return false;
  }
  std::vector<PayloadClass> classes;
  LoadedPayload loaded;
  bool ok = readJarClasses(env, path, classes) && defineMissingClasses(env, loader, classes) &&
    loadedPayloadClasses(env, loader, loaded);
  std::vector<jvmtiClassDefinition> definitions;
  std::vector<std::pair<jbyteArray, jbyte*>> pinned;
  size_t adaptedConstructors = 0;
  if (ok) for (const auto& candidate : classes) {
    auto found = loaded.find(candidate.name);
    if (found == loaded.end()) continue;
    jboolean modifiable{};
    jvmtiError error = tooling->IsModifiableClass(found->second, &modifiable);
    if (error != JVMTI_ERROR_NONE || !modifiable) {
      log(L"same-session reload failed: class %hs cannot be replaced", candidate.name.c_str());
      ok = false;
      break;
    }
    jbyte* bytes = env->GetByteArrayElements(candidate.bytes, nullptr);
    if (!bytes) { exceptionLog(env, L"pin replacement class bytes"); ok = false; break; }
    pinned.emplace_back(candidate.bytes, bytes);
    if (!compatibleMethodModifiers(found->second, candidate.name, bytes,
        env->GetArrayLength(candidate.bytes), adaptedConstructors)) {
      log(L"replacement method schema/modifier check failed for %hs", candidate.name.c_str());
      ok = false;
      break;
    }
    definitions.push_back({found->second, env->GetArrayLength(candidate.bytes),
      reinterpret_cast<const unsigned char*>(bytes)});
  }
  if (ok && definitions.empty()) { log(L"same-session reload failed: no matching payload classes"); ok = false; }
  if (ok) {
    ExclusiveLock guard(operationLock);
    log(L"constructor modifier compatibility: %zu ACC_STRICT flags preserved in memory", adaptedConstructors);
    // One atomic batch: JVMTI rejects incompatible fields/methods without changing any body.
    jvmtiError error = tooling->RedefineClasses(static_cast<jint>(definitions.size()), definitions.data());
    if (error != JVMTI_ERROR_NONE) {
      toolingError(L"atomic payload replacement (class schema must be compatible)", error);
      ok = false;
    } else log(L"replaced %zu payload classes atomically; Minecraft patches retained", definitions.size());
  }
  for (auto& entry : pinned) env->ReleaseByteArrayElements(entry.first, entry.second, JNI_ABORT);
  releaseLoadedPayload(env, loaded);
  releasePayloadClasses(env, classes);
  return ok;
}

bool addJarToLoader(JNIEnv* env, jobject loader, const std::wstring& path) {
  jclass fileClass = env->FindClass("java/io/File");
  jclass uriClass = env->FindClass("java/net/URI");
  if (!fileClass || !uriClass) { exceptionLog(env, L"create payload URL"); return false; }
  jmethodID constructor = env->GetMethodID(fileClass, "<init>", "(Ljava/lang/String;)V");
  jmethodID toUri = env->GetMethodID(fileClass, "toURI", "()Ljava/net/URI;");
  jmethodID toUrl = env->GetMethodID(uriClass, "toURL", "()Ljava/net/URL;");
  if (!constructor || !toUri || !toUrl) { exceptionLog(env, L"create payload URL"); return false; }
  jobject file = env->NewObject(fileClass, constructor, javaPath(env, path));
  jobject uri = file ? env->CallObjectMethod(file, toUri) : nullptr;
  jobject url = uri ? env->CallObjectMethod(uri, toUrl) : nullptr;
  if (!url || env->ExceptionCheck()) { exceptionLog(env, L"create payload URL"); return false; }
  if (!loader) { log(L"Minecraft was defined by the bootstrap loader; unsupported"); return false; }
  jclass loaderClass = env->GetObjectClass(loader);
  jmethodID addUrl = env->GetMethodID(loaderClass, "addURL", "(Ljava/net/URL;)V");
  if (addUrl) {
    env->CallVoidMethod(loader, addUrl, url);
    if (!env->ExceptionCheck()) { log(L"payload added to the game class loader (addURL)"); return true; }
    exceptionLog(env, L"addURL");
  } else if (env->ExceptionCheck()) env->ExceptionClear();
  jclass classLoader = env->FindClass("java/lang/ClassLoader");
  jmethodID systemLoader = classLoader ? env->GetStaticMethodID(classLoader, "getSystemClassLoader", "()Ljava/lang/ClassLoader;") : nullptr;
  jobject system = systemLoader ? env->CallStaticObjectMethod(classLoader, systemLoader) : nullptr;
  if (system && env->IsSameObject(system, loader)) {
    // JVMTI strings use modified UTF-8, including supplementary path characters.
    std::wstring searchPath = path;
#ifndef _WIN32
    // HotSpot's append-search path crosses both JNI and platform encodings. An
    // ASCII copy avoids its supplementary-character mismatch while the real
    // payload stays in the caller's Unicode log directory. Keep this private
    // copy for the JVM lifetime because classpath entries are opened lazily.
    char directory[] = "/tmp/slinky-classpath-XXXXXX";
    if (!mkdtemp(directory)) { log(L"cannot create a private classpath directory"); return false; }
    std::string alias = std::string(directory) + "/payload.jar";
    std::string payloadPath = utf8(path);
    std::error_code copyError;
    std::filesystem::copy_file(payloadPath, alias, std::filesystem::copy_options::none, copyError);
    if (!copyError) std::filesystem::permissions(alias,
      std::filesystem::perms::owner_read | std::filesystem::perms::owner_write,
      std::filesystem::perm_options::replace, copyError);
    if (copyError) {
      ::unlink(alias.c_str());
      ::rmdir(directory);
      log(L"cannot create the payload classpath copy: %hs", copyError.message().c_str());
      return false;
    }
    searchPath = wide(alias);
    log(L"system classpath copy: %ls", searchPath.c_str());
#endif
    jstring javaFile = javaPath(env, searchPath);
    const char* fileName = env->GetStringUTFChars(javaFile, nullptr);
    if (fileName) {
      jvmtiError error = tooling->AddToSystemClassLoaderSearch(fileName);
      env->ReleaseStringUTFChars(javaFile, fileName);
      if (error == JVMTI_ERROR_NONE) { log(L"payload appended to the system class loader search"); return true; }
    }
#ifndef _WIN32
    ::unlink(alias.c_str());
    ::rmdir(directory);
#endif
  } else if (env->ExceptionCheck()) env->ExceptionClear();
  return defineJarClasses(env, loader, path);
}

jclass loadPayloadClass(JNIEnv* env, jobject loader, const char* name) {
  jclass classLoader = env->FindClass("java/lang/ClassLoader");
  jmethodID loadClass = classLoader ? env->GetMethodID(classLoader, "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;") : nullptr;
  jstring javaName = env->NewStringUTF(name);
  jclass result = loadClass && javaName ? static_cast<jclass>(env->CallObjectMethod(loader, loadClass, javaName)) : nullptr;
  if (!result || env->ExceptionCheck()) { exceptionLog(env, L"load payload class"); return nullptr; }
  return result;
}

bool registerBridge(JNIEnv* env, jclass bridge) {
  JNINativeMethod methods[] = {
    {const_cast<char*>("getClassBytes"), const_cast<char*>("(Ljava/lang/Class;)[B"), reinterpret_cast<void*>(getClassBytes)},
    {const_cast<char*>("retransformClass"), const_cast<char*>("(Ljava/lang/Class;[B)I"), reinterpret_cast<void*>(retransformClass)},
    {const_cast<char*>("log"), const_cast<char*>("(Ljava/lang/String;)V"), reinterpret_cast<void*>(javaLog)}
  };
  if (env->RegisterNatives(bridge, methods, static_cast<jint>(std::size(methods))) == JNI_OK) return true;
  exceptionLog(env, L"RegisterNatives dev.slinky.inject.NativeBridge");
  return false;
}

// 0 = fresh, 1 = active, 2 = old classes remain but their manager is inactive.
// -1 = state could not be read; never replace an unknown live manager.
int payloadState(JNIEnv* env, jobject loader, bool& previousManager) {
  previousManager = false;
  if (!loader) { log(L"game class loader unavailable"); return -1; }
  jclass bootstrap = loaderDefinedClass(env, loader, "dev/slinky/inject/Bootstrap");
  if (env->ExceptionCheck()) { exceptionLog(env, L"inspect previous Bootstrap"); return -1; }
  bool existing = bootstrap != nullptr;
  if (bootstrap) env->DeleteLocalRef(bootstrap);
  jclass hooks = loaderDefinedClass(env, loader, "dev/slinky/client/hook/GameHooks");
  if (env->ExceptionCheck()) { exceptionLog(env, L"inspect previous GameHooks"); return -1; }
  if (!hooks) return existing ? 2 : 0;
  jfieldID modules = env->GetStaticFieldID(hooks, "modules", "Ldev/slinky/module/ModuleManager;");
  jobject manager = modules ? env->GetStaticObjectField(hooks, modules) : nullptr;
  env->DeleteLocalRef(hooks);
  if (env->ExceptionCheck() || !modules) { exceptionLog(env, L"inspect previous module manager"); return -1; }
  if (!manager) return 2;
  previousManager = true;
  jclass managerType = env->GetObjectClass(manager);
  jfieldID shutdown = managerType ? env->GetFieldID(managerType, "shutdown", "Ljava/util/concurrent/atomic/AtomicBoolean;") : nullptr;
  jobject flag = shutdown ? env->GetObjectField(manager, shutdown) : nullptr;
  jclass atomicType = env->FindClass("java/util/concurrent/atomic/AtomicBoolean");
  jmethodID get = atomicType ? env->GetMethodID(atomicType, "get", "()Z") : nullptr;
  bool ok = flag && get && !env->ExceptionCheck();
  jboolean inactive = ok ? env->CallBooleanMethod(flag, get) : JNI_FALSE;
  if (env->ExceptionCheck()) ok = false;
  if (!ok) exceptionLog(env, L"read previous shutdown state");
  if (atomicType) env->DeleteLocalRef(atomicType);
  if (flag) env->DeleteLocalRef(flag);
  if (managerType) env->DeleteLocalRef(managerType);
  env->DeleteLocalRef(manager);
  return ok ? (inactive ? 2 : 1) : -1;
}

bool prepareReloadProperties(JNIEnv* env, bool previousManager) {
  jclass system = env->FindClass("java/lang/System");
  jmethodID set = system ? env->GetStaticMethodID(system, "setProperty", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;") : nullptr;
  jmethodID clear = system ? env->GetStaticMethodID(system, "clearProperty", "(Ljava/lang/String;)Ljava/lang/String;") : nullptr;
  if (!set || !clear) { exceptionLog(env, L"resolve reload markers"); return false; }
  if (previousManager) {
    jstring key = env->NewStringUTF("slinky.patches.installed");
    jstring value = env->NewStringUTF("true");
    jobject old = env->CallStaticObjectMethod(system, set, key, value);
    if (old) env->DeleteLocalRef(old);
    env->DeleteLocalRef(key);
    env->DeleteLocalRef(value);
  }
  if (!env->ExceptionCheck()) {
    jstring key = env->NewStringUTF("slinky.injected");
    jobject old = env->CallStaticObjectMethod(system, clear, key);
    if (old) env->DeleteLocalRef(old);
    env->DeleteLocalRef(key);
  }
  env->DeleteLocalRef(system);
  if (env->ExceptionCheck()) { exceptionLog(env, L"prepare reload markers"); return false; }
  return true;
}

#ifdef _WIN32
DWORD WINAPI bootstrapWorker(void*) {
  log(L"SlinkyNative loaded into PID %lu", GetCurrentProcessId());
  HMODULE jvmModule{};
  for (int attempt = 0; attempt < 600 && !jvmModule; ++attempt) {
    jvmModule = GetModuleHandleW(L"jvm.dll");
    if (!jvmModule) pauseWorker(100);
  }
  using GetCreatedVMs = jint(JNICALL*)(JavaVM**, jsize, jsize*);
  auto getVMs = jvmModule ? reinterpret_cast<GetCreatedVMs>(GetProcAddress(jvmModule, "JNI_GetCreatedJavaVMs")) : nullptr;
  JavaVM* vm{};
#else
void bootstrapWorker(JavaVM* vm) {
  log(L"SlinkyNative loaded into PID %lu", static_cast<unsigned long>(getpid()));
#endif
  JNIEnv* env{};
#ifdef _WIN32
  bool bridgeRegistered = false;
#endif
  bool alreadyRunning = false;
  bool started = false;
  bool reloaded = false;
#ifdef _WIN32
  if (!getVMs) log(L"jvm.dll / JNI_GetCreatedJavaVMs not found; is this a Java process?");
  else {
    for (int attempt = 0; attempt < 600; ++attempt) {
      jsize count{};
      if (getVMs(&vm, 1, &count) == JNI_OK && vm && count > 0) break;
      vm = nullptr;
      pauseWorker(100);
    }
#else
  {
#endif
    if (!vm || vm->AttachCurrentThreadAsDaemon(reinterpret_cast<void**>(&env), nullptr) != JNI_OK || !env)
      log(L"could not attach to the JVM");
    else {
      Runtime runtime{};
      std::wstring jarPath;
      bool ready = initializeTooling(vm) && detectRuntime(env, runtime);
      bool previousManager = false;
      int previousState = ready ? payloadState(env, runtime.loader, previousManager) : -1;
      if (previousState == 1) {
        alreadyRunning = true;
        log(L"Slinky is already active; no classes or native methods were replaced");
      }
      reloaded = previousState == 2;
      if (ready && previousState >= 0 && !alreadyRunning && extractPayload(runtime, jarPath) &&
          (reloaded ? replaceInactivePayload(env, runtime.loader, jarPath) : addJarToLoader(env, runtime.loader, jarPath)) &&
          (!reloaded || prepareReloadProperties(env, previousManager))) {
        jclass threadClass = env->FindClass("java/lang/Thread");
        jmethodID current = threadClass ? env->GetStaticMethodID(threadClass, "currentThread", "()Ljava/lang/Thread;") : nullptr;
        jmethodID setLoader = threadClass ? env->GetMethodID(threadClass, "setContextClassLoader", "(Ljava/lang/ClassLoader;)V") : nullptr;
        jobject thread = current ? env->CallStaticObjectMethod(threadClass, current) : nullptr;
        if (thread && setLoader) env->CallVoidMethod(thread, setLoader, runtime.loader);
        if (env->ExceptionCheck()) env->ExceptionClear();
        jclass bridge = loadPayloadClass(env, runtime.loader, "dev.slinky.inject.NativeBridge");
        if (bridge && registerBridge(env, bridge)) {
#ifdef _WIN32
          bridgeRegistered = true;
          HMODULE pinned{};
          GetModuleHandleExW(GET_MODULE_HANDLE_EX_FLAG_PIN | GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS,
            reinterpret_cast<LPCWSTR>(&moduleHandle), &pinned);
#else
          bridgeEverRegistered = true;
#endif
          jclass bootstrap = loadPayloadClass(env, runtime.loader, "dev.slinky.inject.Bootstrap");
          jmethodID start = bootstrap ? env->GetStaticMethodID(bootstrap, "start", "(Ljava/lang/String;Ljava/lang/String;)V") : nullptr;
          if (bootstrap && start) {
            env->CallStaticVoidMethod(bootstrap, start, env->NewStringUTF(runtime.name), javaPath(env, jarPath));
            if (env->ExceptionCheck()) exceptionLog(env, L"Bootstrap.start");
            else {
              bool hasManager = false;
              started = payloadState(env, runtime.loader, hasManager) == 1;
              if (started) log(L"Slinky is running");
              else log(L"Bootstrap.start returned but no active module manager was found");
            }
          } else exceptionLog(env, L"resolve Bootstrap.start");
        }
      }
      if (runtime.minecraft) env->DeleteLocalRef(runtime.minecraft);
      if (runtime.loader) env->DeleteLocalRef(runtime.loader);
      vm->DetachCurrentThread();
    }
  }
  log(L"FINAL STATUS: %ls", alreadyRunning ? L"ALREADY_RUNNING" :
    (started ? (reloaded ? L"RELOADED" : L"RUNNING") : L"FAILED"));
#ifdef _WIN32
  if (!bridgeRegistered) {
    log(alreadyRunning ? L"unused bootstrap unloading" : L"bootstrap failed; unloading");
    // Reconstruction cleanup: disable and remove callbacks before unloading code.
    if (tooling) {
      tooling->SetEventNotificationMode(JVMTI_DISABLE, JVMTI_EVENT_CLASS_FILE_LOAD_HOOK, nullptr);
      jvmtiEventCallbacks empty{};
      tooling->SetEventCallbacks(&empty, sizeof empty);
      tooling->DisposeEnvironment();
      tooling = nullptr;
    }
    InterlockedExchange(&workerStarted, 0);
    FreeLibraryAndExitThread(moduleHandle, alreadyRunning ? 0 : 1);
  }
  return 0;
#else
  // The JVM retains this agent and NativeBridge methods may still call into it.
  // In particular, an ALREADY_RUNNING reattach must leave the original bridge alive.
  if (!bridgeEverRegistered && tooling) {
    ExclusiveLock guard(operationLock);
    tooling->SetEventNotificationMode(JVMTI_DISABLE, JVMTI_EVENT_CLASS_FILE_LOAD_HOOK, nullptr);
    jvmtiEventCallbacks empty{};
    tooling->SetEventCallbacks(&empty, sizeof empty);
    tooling->DisposeEnvironment();
    tooling = nullptr;
  }
  workerStarted.store(false, std::memory_order_release);
#endif
}
} // namespace

#ifdef _WIN32
BOOL WINAPI DllMain(HINSTANCE instance, DWORD reason, LPVOID) {
  if (reason == DLL_PROCESS_ATTACH) {
    moduleHandle = instance;
    DisableThreadLibraryCalls(instance);
    if (InterlockedCompareExchange(&workerStarted, 1, 0) == 0) {
      HANDLE thread = CreateThread(nullptr, 0, bootstrapWorker, nullptr, 0, nullptr);
      if (thread) CloseHandle(thread);
    }
  }
  return TRUE;
}
#else
// Options are exactly the absolute UTF-8 directory supplied by the console launcher.
extern "C" JNIEXPORT jint JNICALL Agent_OnAttach(JavaVM* vm, char* options, void*) {
  if (!vm || !options || !*options) return JNI_ERR;
  try {
    std::filesystem::path directory(options);
    if (!directory.is_absolute() || !std::filesystem::is_directory(directory) ||
        std::strchr(options, '\n') || std::strchr(options, '\r')) return JNI_ERR;
    // Validate UTF-8 before background JNI work; Java paths need true UTF-16 on Linux.
    wide(options);
    std::string directoryName = directory.string();
    bool expected = false;
    if (!workerStarted.compare_exchange_strong(expected, true, std::memory_order_acq_rel)) return JNI_ERR;
    {
      ExclusiveLock guard(logLock);
      runDirectory = std::move(directoryName);
    }
    try {
      std::thread([vm] {
        try { bootstrapWorker(vm); }
        catch (const std::exception& error) {
          log(L"native worker error: %hs", error.what());
          log(L"FINAL STATUS: FAILED");
          vm->DetachCurrentThread();
          workerStarted.store(false, std::memory_order_release);
        } catch (...) {
          log(L"native worker stopped unexpectedly");
          log(L"FINAL STATUS: FAILED");
          vm->DetachCurrentThread();
          workerStarted.store(false, std::memory_order_release);
        }
      }).detach();
    } catch (...) {
      workerStarted.store(false, std::memory_order_release);
      return JNI_ERR;
    }
    return JNI_OK;
  } catch (...) {
    return JNI_ERR;
  }
}
#endif
