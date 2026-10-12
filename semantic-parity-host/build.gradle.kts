plugins {
    id("org.jetbrains.kotlin.jvm")
    application
}

kotlin {
    jvmToolchain(17)
}

sourceSets {
    main {
        kotlin.srcDir("../app/src/main/java")
        kotlin.include("com/eltnegcellist/emma/ai/RuriSemanticTokenizer.kt")
        kotlin.include("com/eltnegcellist/emma/ai/SemanticParityHost.kt")
    }
}

dependencies {
    implementation("com.microsoft.onnxruntime:onnxruntime:1.23.2")
    implementation("org.json:json:20240303")
}

application {
    mainClass.set("com.eltnegcellist.emma.ai.SemanticParityHostKt")
}
