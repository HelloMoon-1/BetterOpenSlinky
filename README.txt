# BetterOpenSlink

**Minecraft 1.8.9 client source for Windows x64 and Linux x86_64.**

> **声明 / Disclaimer**
>
> 这个版本基于开源的 **Slinky**、**RavenB4** 以及 **Driplite** 开发，截图由 GPT 意淫而来，不保证一些功能能正常使用以及适配。
>
> This version is based on the open-source **Slinky**, **RavenB4**, and **Driplite**. The screenshots are imagined/fabricated by GPT, and we do not guarantee that some features will work properly or be compatible.

---

## 目录 / Table of Contents

- [自动构建 / Automatic Builds](#自动构建--automatic-builds)
- [下载产物内容 / Download Artifact Contents](#下载产物内容--download-artifact-contents)
- [构建环境要求 / Build Requirements](#构建环境要求--build-requirements)
- [运行环境要求 / Runtime Requirements](#运行环境要求--runtime-requirements)
- [重点提醒 / Important Note](#重点提醒--important-note)

---

## 自动构建 / Automatic Builds

它用 **GitHub Actions** 自动构建，工作流名叫 **Build Slinky**。
It uses **GitHub Actions** for automatic building, with a workflow named **Build Slinky**.

- 只要你往这个仓库推送代码，就会自动为两个平台各构建一次。
  Whenever you push to this repository, it will automatically build once for each of the two platforms.
- 构建完成后，去对应的 workflow run 里下载对应平台的 artifact（构建产物）。
  After the build completes, download the artifact for the corresponding platform from the matching workflow run.

---

## 下载产物内容 / Download Artifact Contents

- **Windows**：包含 `Slinky.exe`、`README.txt`、`BUILD-INFO.json`
  **Windows**: contains `Slinky.exe`, `README.txt`, `BUILD-INFO.json`
- **Linux**：包含启动器、本地库（native library）和运行所需的 payload JAR 包
  **Linux**: contains the launcher, native library, and required payload JARs

---

## 构建环境要求 / Build Requirements

- JDK 17
- Python 3.12
- CMake 3.24 或更高 / CMake 3.24 or higher
- C++ 工具链 / C++ toolchain

具体命令 / Commands:

**Windows:**

```powershell
./build.ps1 -RestoreDependencies -Generator 'Visual Studio 17 2022'
