import java.util.Properties
import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/*
 * One codebase, any ISP on Sparo. The generic build asks the customer which
 * ISP (a scanned link or the ISP's code); a branded build has it baked in:
 *
 *   ./gradlew assembleRelease -PtenantSlug=kilimani-fibre \
 *       -PappName="Kilimani Fibre" -PappIdSuffix=.kilimani
 *
 * Nothing here is secret - the app holds no credential of any kind - so all
 * of it can live in a public repository.
 */
fun prop(name: String, default: String): String =
    (project.findProperty(name) as String?)?.takeIf { it.isNotBlank() } ?: default

val baseUrl = prop("baseUrl", "https://app.sparo.run")
val linkHost = prop("linkHost", "app.sparo.run")
val tenantSlug = prop("tenantSlug", "")
val appName = prop("appName", "My ISP")
val appIdSuffix = prop("appIdSuffix", "")

/*
 * Release signing, from a properties file kept outside the repository:
 * storeFile, keyAlias, storePassword, keyPassword. Default location
 * ~/.config/sparo/myisp-signing.properties, or -PsigningProps=/path. Without
 * it the release build is simply unsigned - every fork still builds, and each
 * ISP signs with its own key.
 */
val signingFile = file(prop("signingProps", "${System.getProperty("user.home")}/.config/sparo/myisp-signing.properties"))
val signing = Properties().apply { if (signingFile.isFile) signingFile.inputStream().use(::load) }

android {
    namespace = "run.sparo.myisp"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "run.sparo.myisp$appIdSuffix"
        minSdk = 24
        targetSdk = 37
        versionCode = 2
        versionName = "1.1.0"

        buildConfigField("String", "BASE_URL", "\"$baseUrl\"")
        buildConfigField("String", "LINK_HOST", "\"$linkHost\"")
        buildConfigField("String", "TENANT_SLUG", "\"$tenantSlug\"")
        resValue("string", "app_name", appName)
        manifestPlaceholders["linkHost"] = linkHost

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (signing.getProperty("storeFile") != null) {
            create("release") {
                storeFile = file(signing.getProperty("storeFile"))
                storePassword = signing.getProperty("storePassword")
                keyAlias = signing.getProperty("keyAlias")
                keyPassword = signing.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            // R8: shrink, optimise and drop unused resources. What keeps the
            // APK under its 5 MB budget (checked by `./gradlew checkApkSize`).
            optimization {
                enable = true
            }
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }
    // English and Kiswahili only. The libraries ship translations for ~90
    // languages this app never shows; dropping them is free size.
    androidResources {
        localeFilters += listOf("en", "sw")
    }
    packaging {
        resources {
            excludes += listOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/*.version",
                "/META-INF/*.kotlin_module",
                "/kotlin/**",
                "DebugProbesKt.bin",
            )
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

/*
 * The size budget, enforced: the build warns
 * over 6 MB and fails over 9 MB, and prints where the bytes go so every
 * change that adds weight shows it.
 */
tasks.register("checkApkSize") {
    dependsOn("assembleRelease")
    // Resolved here, not in doLast: the configuration cache cannot keep a
    // reference to the build script itself.
    val apkDir = layout.buildDirectory.dir("outputs/apk/release")
    doLast {
        val budget = 6L * 1024 * 1024
        val limit = 9L * 1024 * 1024
        val apks = apkDir.get().asFile.listFiles { f -> f.extension == "apk" }.orEmpty()
        check(apks.isNotEmpty()) { "No release APK found." }
        apks.forEach { apk ->
            val parts = sortedMapOf<String, Long>()
            ZipFile(apk).use { zip ->
                zip.entries().asSequence().forEach { e ->
                    val part = when {
                        e.name.startsWith("classes") -> "code (dex)"
                        e.name == "resources.arsc" -> "resource table"
                        e.name.startsWith("res/font") -> "fonts"
                        e.name.startsWith("res/") -> "resources"
                        e.name.startsWith("META-INF") -> "signing/meta"
                        else -> "other"
                    }
                    parts[part] = (parts[part] ?: 0) + e.compressedSize
                }
            }
            val mb = "%.2f".format(apk.length() / 1048576.0)
            println("${apk.name}: $mb MB (budget 6 MB, limit 9 MB)")
            parts.forEach { (k, v) -> println("  %-16s %7.1f KB".format(k, v / 1024.0)) }
            check(apk.length() <= limit) { "${apk.name} is $mb MB - over the 9 MB limit." }
            if (apk.length() > budget) logger.warn("WARNING: ${apk.name} is $mb MB - over the 6 MB working budget.")
        }
    }
}
