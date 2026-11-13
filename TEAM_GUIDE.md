# 🚀 MIMO Optical Communication System — Team Quickstart & Workflow Guide

Welcome to the MIMO Optical Communication team! This guide explains how we work together, test code, and keep the project stable without needing to memorize complex Git commands.

---

## 📁 Repository Structure
```
MIMO-Based-Optical-Wireless-Communication-System/
├── pc/          # Python Transmitter (PyQt6, OpenGL, OpenCV)
├── android/     # Android Receiver (Kotlin, CameraX, OpenCV)
├── AGENTS.md    # AI Safety & Workflow Rules
└── README.md    # System Architecture & Specifications
```

* **Python PC App:** Open in VS Code / Antigravity and run:
  ```powershell
  python -m pc.main
  ```
* **Android Receiver App:** Open the `android/` folder in **Android Studio**. It will automatically detect Gradle and sync.

---

## 🛡️ The 2 Sacred Team Rules

1. **Anyone can work on any part:** Everyone on the team is a developer. You can improve the PC transmitter, Android receiver, algorithms, or UI.
2. **The 2-Tester Approval Rule:** **NEVER push directly to `main`.** 
   * Before any code merges into `main`, **at least TWO team members must test it and approve it**.
   * One tester verifies that automated tests pass.
   * One tester verifies physical hardware: pointing an Android phone at the PC screen to verify real file transfer.

---

## 🔄 Daily Workflow (Step-by-Step)

### Step 1: Start a New Task
Never work directly on `main`. Tell Antigravity or run:
```powershell
git checkout main
git pull
git checkout -b feature/your-task-name
```
*(Example: `feature/rgb-color-modulation` or `bugfix/camera-focus`)*

### Step 2: Write & Test Your Code Locally
Before asking teammates to test, run the automated tests on your machine:
* **PC Tests:**
  ```powershell
  python -m pytest pc/tests
  ```
* **Android Tests:**
  ```powershell
  cd android
  .\gradlew.bat testDebugUnitTest
  ```
All tests must be **100% green**.

### Step 3: Save Your Progress & Push
```powershell
git add .
git commit -m "FEAT: Added your feature description"
git push -u origin feature/your-task-name
```

### Step 4: Open a Pull Request & Request 2 Testers
1. Go to GitHub and open a **Pull Request (PR)** from `feature/your-task-name` to `main`.
2. Post the PR link in the team group.
3. **Tester 1:** Pulls your branch, runs unit tests, and verifies the code logic $\to$ Leaves a review: **"Approve (Unit Tests Passed)"**.
4. **Tester 2:** Builds the Android APK, runs the PC app, points camera at monitor, tests a full file transfer $\to$ Leaves a review: **"Approve (Physical Optical Link Verified)"**.
5. Once **both approvals** are in, the PR is merged into `main`! 🎉

---

## 🆘 Need Help or Something Broke?
* Ask Antigravity: *"Check my branch and make sure my work is saved."*
* Never run `git reset --hard` or `git clean -fd`.
* If an optical link behaves erratically, check ambient lighting and screen glare first!

