import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

abstract class StripBundledOnnxRuntime : TransformAction<TransformParameters.None> {
    @get:InputArtifact
    abstract val inputArtifact: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val input = inputArtifact.get().asFile
        val output = outputs.file(input.nameWithoutExtension + "-without-onnxruntime.aar")

        ZipFile(input).use { zip ->
            ZipOutputStream(output.outputStream().buffered()).use { out ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (
                        !entry.isDirectory &&
                        entry.name.matches(Regex("""jni/[^/]+/libonnxruntime\.so"""))
                    ) {
                        continue
                    }

                    val copy = ZipEntry(entry.name)
                    if (entry.time >= 0L) copy.time = entry.time
                    out.putNextEntry(copy)
                    if (!entry.isDirectory) {
                        zip.getInputStream(entry).use { source -> source.copyTo(out) }
                    }
                    out.closeEntry()
                }
            }
        }
    }
}

val moonshineRaw = configurations.create("moonshineRaw") {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}

dependencies.registerTransform(StripBundledOnnxRuntime::class.java) {
    from.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "aar")
    to.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "moonshine-aar-without-ort")
}

val strippedMoonshine = moonshineRaw.incoming.artifactView {
    attributes.attribute(
        ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE,
        "moonshine-aar-without-ort",
    )
}.files


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

android {
    namespace = "com.eltnegcellist.emma"
    compileSdk = 37
    compileSdkMinor = 1

    defaultConfig {
        applicationId = "com.eltnegcellist.emma"
        minSdk = 28
        targetSdk = 36
        versionCode = 108
        versionName = "1.9.25"

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
            // Keep the selected full ORT byte-identical to Microsoft's AAR so
            // CI can prove Moonshine's reduced build was not packaged.
            keepDebugSymbols += "**/libonnxruntime.so"
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
    // Kitten uses the full Microsoft ONNX Runtime operator build.
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.23.2")

    // Moonshine bundles its own reduced libonnxruntime.so. Resolve its AAR
    // through a transform that removes only that duplicate native library so
    // Moonshine and Kitten share the verified full ORT above.
    add(moonshineRaw.name, "ai.moonshine:moonshine-voice:0.1.5")
    implementation(files(strippedMoonshine))

    implementation("com.google.ai.edge.litertlm:litertlm-android:0.16.0")
    implementation("androidx.documentfile:documentfile:1.1.0")
    implementation("androidx.work:work-runtime-ktx:2.12.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation("junit:junit:4.13.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
