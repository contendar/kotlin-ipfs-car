# kotlin-ipfs-car

A lightweight Kotlin Android library for generating **IPFS CAR files** and computing **CIDv1 (0x202 / SHA-256 / raw)** streams without `ipfs-car` or Node.js.  
Includes a sample Jetpack Compose app demonstrating **file picker → CAR → Storacha upload**.

## Features
- 📦 Stream-based CAR writer (no loading full file into RAM)
- 🔐 Deterministic CIDv1 calculation compatible with `ipfs-car`
- 🖼 Supports images, video, audio, JSON/text & any binary content
- 📡 Storacha "upload intent" compatible (two-step upload)
- 🟦 100% Kotlin, Android-only, no external IPFS binaries required

---

## Installation (Gradle Kotlin DSL - JitPack)

In **settings.gradle.kts**:

`dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://jitpack.io")
  }
}`

In **app/build.gradle.kts**:

`dependencies {
 implementation("com.github.contendar:kotlin-ipfs-car:v1.0.1")
}`

## Usage (CAR + CID + Storacha Upload)
`val carResult = CarBuilder.createCarFromInputStream(
    fileName = "example.png",
    input = contentResolver.openInputStream(uri)!!
)`

`println("CAR CID: ${carResult.cid}")`
`println("CAR size: ${carResult.sizeBytes}")`

// Storacha step 1: create upload intent
val intent = StorachaUtils.createUploadIntent(
    cid = carResult.cid,
    size = carResult.sizeBytes
)

// Storacha step 2: upload CAR stream
StorachaUtils.uploadCarToStoracha(
    uploadUrl = intent.uploadUrl,
    input = carResult.carInputStream
)

## Sample App Included
The repository contains a Jetpack Compose sample demonstrating:

1️⃣ Pick file from gallery
2️⃣ Convert to CAR
3️⃣ Upload to Storacha
4️⃣ Display CID + URLs

Module: sample-compose-app

## Build Requirements

Component	Version
Android Gradle Plugin	8.2+
Kotlin	1.9+
Min SDK	24
Compose BOM	2024+
JDK	17+

## License

MIT © 2025 Zain Ali / contendar
