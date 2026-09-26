import org.gradle.api.file.RelativePath

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val signingStorePath = providers.gradleProperty("emma.signingStoreFile")
    .orElse(providers.environmentVariable("EMMA_SIGNING_STORE_FILE"))
    .orNull
val signingStorePassword = providers.gradleProperty("emma.signingStorePassword")
    .orElse(providers.environmentVariable("EMMA_SIGNING_STORE_PASSWORD"))
    .orNull
val signingKeyAlias = providers.gradleProperty("emma.signingKeyAlias")
    .orElse(providers.environmentVariable("EMMA_SIGNING_KEY_ALIAS"))
    .orNull
val signingKeyPassword = providers.gradleProperty("emma.signingKeyPassword")
    .orElse(providers.environmentVariable("EMMA_SIGNING_KEY_PASSWORD"))
    .orNull

val signingInputs = listOf(
    signingStorePath,
    signingStorePassword,
    signingKeyAlias,
    signingKeyPassword,
)
val hasAnyCustomSigningInput = signingInputs.any { !it.isNullOrBlank() }
val hasAllCustomSigningInputs = signingInputs.all { !it.isNullOrBlank() }

require(!hasAnyCustomSigningInput || hasAllCustomSigningInputs) {
    "Emma signing is only partially configured. Provide all four signing values or none."
}

val fullOrtAar by configurations.creating
val extractedFullOrtJni = layout.buildDirectory.dir("generated/full-ort-jni")
val extractFullOrtJni = tasks.register<Sync>("extractFullOrtJni") {
    from({
        zipTree(fullOrtAar.singleFile)
    }) {
        include(
            "jni/arm64-v8a/libonnxruntime.so",
            "jni/arm64-v8a/libonnxruntime4j_jni.so",
            "jni/armeabi-v7a/libonnxruntime.so",
            "jni/armeabi-v7a/libonnxruntime4j_jni.so",
        )
        eachFile {
            val segments = relativePath.segments
            val abi = segments[1]
            relativePath = RelativePath(true, abi, name)
        }
        includeEmptyDirs = false
    }
    into(extractedFullOrtJni)
}

android {
    namespace = "com.eltnegcellist.emma"
    compileSdk = 37
    compileSdkMinor = 1

    defaultConfig {
        applicationId = "com.eltnegcellist.emma"
        minSdk = 28
        targetSdk = 36
        versionCode = 78
        versionName = "1.7.0"

        // Emma is distributed for physical Android devices.
        // Keep both 64-bit and legacy 32-bit ARM, but omit x86/x86_64 emulator/PC ABIs.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    val prototypeSigningConfig = if (hasAllCustomSigningInputs) {
        signingConfigs.create("prototype") {
            storeFile = file(signingStorePath!!)
            storePassword = signingStorePassword!!
            keyAlias = signingKeyAlias!!
            keyPassword = signingKeyPassword!!
        }
    } else {
        null
    }

    buildTypes {
        getByName("debug") {
            if (prototypeSigningConfig != null) {
                signingConfig = prototypeSigningConfig
            }
        }
    }

    sourceSets {
        getByName("main").jniLibs.srcDir(extractedFullOrtJni)
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            pickFirsts += "**/libonnxruntime.so"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.08.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    compileOnly("com.microsoft.onnxruntime:onnxruntime-android:1.23.2")
    add(fullOrtAar.name, "com.microsoft.onnxruntime:onnxruntime-android:1.23.2@aar")
    implementation("ai.moonshine:moonshine-voice:0.1.5")

    implementation("com.google.ai.edge.litertlm:litertlm-android:0.16.0")
    implementation("androidx.documentfile:documentfile:1.1.0")

    testImplementation("junit:junit:4.13.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
}


tasks.matching { it.name.endsWith("NativeLibs") }.configureEach {
    dependsOn(extractFullOrtJni)
}
