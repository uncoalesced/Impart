// Engineered by uncoalesced
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.uncoalesced.impart"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.uncoalesced.impart"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        // Swaps in the Hilt test application (T17).
        testInstrumentationRunner = "com.uncoalesced.impart.HiltTestRunner"
    }

    signingConfigs {
        create("debugConfig") {
            storeFile = file("${rootDir}/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        // Release signing is driven entirely by env vars injected on the
        // deployment branch (deployment.yml). Absent env = unsigned release,
        // so local and main-gate builds are unaffected.
        create("release") {
            val ksPath = System.getenv("RELEASE_KEYSTORE_PATH")
            if (ksPath != null) {
                storeFile = file(ksPath)
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
            // minSdk 26 verifies v2; v3 adds key rotation. v1 (JAR signing) is weaker and
            // unnecessary, so it is off (T11).
            enableV1Signing = false
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debugConfig")
        }
        release {
            // R8 on (T10): smaller APK, fewer symbols for an examiner to map (A2-6). The
            // keep rules live in proguard-rules.pro; CI builds this variant on every PR and
            // keeps mapping.txt as an artifact.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (System.getenv("RELEASE_KEYSTORE_PATH") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// TERMS.md, PRIVACY.md and LICENSE live at the repository root, where GitHub shows them and
// store listings can link to them. This copies them into each variant's assets under legal/
// at build time, so the app always ships the same text with no second copy in src/.
abstract class CopyLegalDocuments : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val documents: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val root = outputDir.get().asFile
        root.deleteRecursively()
        val target = root.resolve("legal").apply { mkdirs() }
        documents.files.forEach { source ->
            require(source.isFile) { "Legal document missing: ${source.path}" }
            source.copyTo(target.resolve(source.name), overwrite = true)
        }
    }
}

androidComponents {
    onVariants { variant ->
        val copyTask = tasks.register<CopyLegalDocuments>(
            "copy${variant.name.replaceFirstChar { it.uppercase() }}LegalDocuments"
        ) {
            documents.from(
                rootProject.file("LICENSE"),
                rootProject.file("TERMS.md"),
                rootProject.file("PRIVACY.md")
            )
        }
        variant.sources.assets?.addGeneratedSourceDirectory(copyTask, CopyLegalDocuments::outputDir)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // Security
    implementation(libs.tink.android)
    implementation(libs.androidx.security.crypto)
    implementation(libs.sqlcipher.android)

    // CameraX for the preview and frames; ZXing decodes the QR on the device (T21).
    implementation(libs.zxing.core)
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)

    // JVM unit tests. org.json ships a real JSONObject so the codecs run without an emulator.
    testImplementation(libs.junit)
    testImplementation(libs.org.json)

    // Instrumented tests (T17): Compose UI tests on an emulator, with a fake RelayManager.
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
}
