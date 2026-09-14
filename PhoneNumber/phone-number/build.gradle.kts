plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

ext {
    set("bintrayRepo", "maven")
    set("bintrayName", "phone-number")

    set("publishedGroupId", "org.xdty.phone.number")
    set("libraryName", "PhoneNumber")
    set("artifact", "phone-number")

    set("libraryDescription", "A library to get phone number location and other info.")

    set("siteUrl", "https://github.com/xdtianyu/PhoneNumber")
    set("gitUrl", "https://github.com/xdtianyu/PhoneNumber")

    set("libraryVersionCode", 78)
    set("libraryVersion", "0.7.18")

    set("developerId", "xdtianyu")
    set("developerName", "xdtianyu")
    set("developerEmail", "xdtianyu@gmail.com")

    set("licenseName", "The Apache Software License, Version 2.0")
    set("licenseUrl", "http://www.apache.org/licenses/LICENSE-2.0.txt")
    set("allLicenses", listOf("Apache-2.0"))
}

android {
    namespace = "org.xdty.phone.number"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    api(fileTree("libs") { include("*.jar") })
    api(libs.kotlin.stdlib)

    api(libs.gson)
    api(libs.libphonenumber)
    api(libs.geocoder)
    api(libs.carrier)
    api(libs.rxjava)
    api(libs.rxandroid)

    testImplementation(libs.junit.module)
    testImplementation(libs.mockito.core.module)

    androidTestImplementation(libs.androidx.annotation.module)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules.module)

    androidTestImplementation(libs.androidx.test.ext.junit.module)
    androidTestImplementation(libs.androidx.test.ext.truth)
    androidTestImplementation(libs.truth)

    androidTestImplementation(libs.hamcrest.library)
    androidTestImplementation(libs.androidx.test.espresso.core.module)
    androidTestImplementation(libs.androidx.test.uiautomator.module)
}

tasks.withType<Javadoc> {
    isEnabled = false
}
