plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
}

fun runCommand(vararg commands: String): String {
    return try {
        val process = ProcessBuilder(*commands)
            .directory(project.rootDir)
            .redirectErrorStream(true)
            .start()
        process.inputStream.bufferedReader().readText().trim()
    } catch (e: Exception) {
        // ignore errors (e.g. not a git repo)
        ""
    }
}

val gitSha = runCommand("git", "rev-parse", "--short", "HEAD")

// 正式发布版本：10.0.1（固定版本号，不依赖 git tag）
val appVersionName = "10.0.1"
val appVersionCode = 1

android {
    namespace = "com.github.soinlee.callassistant"
    compileSdk = libs.versions.compileSdk.get().toInt()

    buildFeatures {
        buildConfig = true
        aidl = true
    }

    defaultConfig {
        applicationId = "com.github.soinlee.callassistant"

        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()

        versionCode = appVersionCode
        versionName = appVersionName

        buildConfigField("String", "GIT_SHA", "\"${gitSha}\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables {
            useSupportLibrary = true
        }

        multiDexEnabled = true
    }

    signingConfigs {
        create("release")
    }

    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            testProguardFile("proguard-test-rules.pro")
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (signingConfigs.getByName("release").storeFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    packaging {
        resources {
            excludes += listOf(
                "META-INF/maven/com.google.guava/guava/pom.properties",
                "META-INF/maven/com.google.guava/guava/pom.xml",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/license.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/notice.txt",
                "META-INF/ASL2.0"
            )
        }
    }

    // The app switches locale at runtime (Utils.changeLang), so disable splitting
    // the App Bundle by language to avoid the AppBundleLocaleChanges lint issue.
    bundle {
        language {
            enableSplit = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    testNamespace = "com.github.soinlee.callassistant"
}

dependencies {
    implementation(fileTree("libs") { include("*.jar") })
    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.uiautomator)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.espresso.intents)
    androidTestImplementation(libs.androidx.test.espresso.idling.resource)
    androidTestImplementation(libs.androidx.test.espresso.contrib) {
        exclude(group = "com.android.support", module = "appcompat-v7")
        exclude(group = "com.android.support", module = "support-v4")
        exclude(group = "com.android.support", module = "design")
        exclude(module = "recyclerview-v7")
    }
    androidTestImplementation(libs.hamcrest.integration)

    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.livedata)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    implementation(libs.androidx.multidex)

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.cardview)
    implementation(libs.androidx.legacy.support.v4)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.google.guava)
    implementation(libs.androidx.material)

    // Flexible/flexbox layout
    implementation(libs.google.flexbox)
    // FlowLayout (tags in bottom sheet)
    implementation(libs.apmem.layouts) {
        exclude(group = "com.android.support")
    }
    // Circle image view
    implementation(libs.circleimageview)

    // Stetho (debug inspector)
    debugImplementation(libs.stetho)

    implementation(libs.dagger)
    kapt(libs.dagger.compiler)

    implementation(libs.material.dialogs.core)

    // requerydb
    implementation(libs.requery)
    implementation(libs.requery.android)
    // requery 1.5.1 处理器在 JDK9+ 需要 javax.annotation.Generated (JSR-250)，否则 NoClassDefFoundError
    kapt(libs.requery.processor)
    kapt(libs.jsr305.generated)

    implementation(project(":phone-number"))
    implementation(project(":standOut"))
    implementation(project(":seekbar-compat"))
    implementation(project(":color-picker"))
}

apply(from = "../signing.gradle")
apply(from = "../manifest.gradle")

// The Google Services plugin requires a google-services.json file. Only apply it (and hence
// only wire up the processGoogleServices task) when that file is actually present so the
// project still builds without Firebase configuration.
val googleServicesFile = rootProject.file("app/google-services.json")
if (googleServicesFile.exists()) {
    apply(plugin = "com.google.gms.google-services")
}
