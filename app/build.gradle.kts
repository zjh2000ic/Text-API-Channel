plugins {
    id("com.android.application")
}

android {
    namespace = "io.github.zjh2000ic.textapichannel"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.github.zjh2000ic.textapichannel"
        minSdk = 24
        targetSdk = 34
        versionCode = 8
        versionName = "0.1.8"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    androidResources {
        // 字体等静态资源不压缩，WebView 直接从 assets 读取
        noCompress += listOf("woff", "woff2", "ttf", "otf")
    }

    packaging {
        resources.excludes += setOf("META-INF/*.kotlin_module", "META-INF/LICENSE*", "META-INF/NOTICE*")
    }
}

dependencies {
    // WebViewAssetLoader：用 https 源提供 assets 里的静态文件，
    // 这样 localStorage / IndexedDB / getUserMedia 才有安全上下文
    implementation("androidx.webkit:webkit:1.11.0")
    implementation("androidx.core:core:1.13.1")
}
