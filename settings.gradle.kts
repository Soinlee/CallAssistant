pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "CallerInfo"

include(":app")
include(":standOut")
include(":phone-number")
include(":config")
include(":seekbar-compat")
include(":color-picker")

project(":phone-number").projectDir = file("PhoneNumber/phone-number")
project(":config").projectDir = file("config/config")
project(":seekbar-compat").projectDir = file("SeekBarCompat/seekbar-compat")
project(":color-picker").projectDir = file("ColorPicker/color-picker")
