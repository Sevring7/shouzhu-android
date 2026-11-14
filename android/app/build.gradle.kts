plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.shouzhu.focus"
    compileSdk = 34

    defaultConfig {
        applicationId = "app.shouzhu.focus"
        minSdk = 26
        targetSdk = 34
        versionCode = 4
        versionName = "1.3"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (file("shouzhu.jks").exists()) signingConfig = signingConfigs.getByName("stable")
        }
        debug {
            // 用固定签名，保证后续版本能覆盖安装
            if (file("shouzhu.jks").exists()) signingConfig = signingConfigs.getByName("stable")
        }
    }

    signingConfigs {
        create("stable") {
            val ks = file("shouzhu.jks")
            if (ks.exists()) {
                storeFile = ks
                storePassword = System.getenv("KEYSTORE_PASS") ?: "shouzhu123"
                keyAlias = System.getenv("KEY_ALIAS") ?: "shouzhu"
                keyPassword = System.getenv("KEY_PASS") ?: "shouzhu123"
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = false
    }
}

/**
 * 把 Web 端的静态资源同步进 assets/www。
 * 改完 ../index.html 之后跑一次 assembleDebug 就会自动带上最新版。
 */
// 用单文件版（dist/index.html，CSS/JS 已全部内联）作为 WebView 的页面
val syncWebAssets by tasks.registering(Copy::class) {
    from(rootProject.file("../dist/index.html")) {
        rename { "index.html" }
    }
    into(layout.projectDirectory.dir("src/main/assets/www"))
}
tasks.named("preBuild") { dependsOn(syncWebAssets) }

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.webkit:webkit:1.11.0")
}
