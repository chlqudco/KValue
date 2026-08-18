/*
 * app 모듈의 Android 빌드 버전, Compose 사용 여부, 의존성과 빌드 타입을 정의한다.
 * 개인 POC용 API 키는 Git에서 제외된 local.properties를 debug BuildConfig에만 주입한다.
 * release에서는 키를 항상 빈 값으로 만들어 클라이언트 배포물에 개인 비밀값이 들어가지 않게 한다.
 */
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Android Studio가 만드는 local.properties에서 로컬 전용 설정을 읽는다.
val localProperties = Properties().apply {
    val propertiesFile = rootProject.file("local.properties")
    if (propertiesFile.isFile) {
        propertiesFile.inputStream().use { load(it) }
    }
}

// BuildConfig의 String 리터럴이 깨지지 않도록 역슬래시와 큰따옴표를 이스케이프한다.
fun buildConfigString(value: String): String {
    val escaped = value.replace("\\", "\\\\").replace("\"", "\\\"")
    return "\"$escaped\""
}

android {
    // namespace는 생성 코드의 패키지이고 applicationId는 기기에 설치되는 앱의 고유 ID다.
    namespace = "com.chlqudco.kvalue"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.chlqudco.kvalue"
        minSdk = 31
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // 세 값이 모두 채워져야 StockRepositoryFactory가 실 API 모드를 선택한다.
            buildConfigField(
                "String",
                "KIS_APP_KEY",
                buildConfigString(localProperties.getProperty("KIS_APP_KEY", ""))
            )
            buildConfigField(
                "String",
                "KIS_APP_SECRET",
                buildConfigString(localProperties.getProperty("KIS_APP_SECRET", ""))
            )
            buildConfigField(
                "String",
                "OPEN_DART_API_KEY",
                buildConfigString(localProperties.getProperty("OPEN_DART_API_KEY", ""))
            )
        }
        release {
            // 공개 빌드에 개인 KIS Secret과 API 키를 포함하지 않는 의도적인 제한이다.
            isMinifyEnabled = false
            buildConfigField("String", "KIS_APP_KEY", "\"\"")
            buildConfigField("String", "KIS_APP_SECRET", "\"\"")
            buildConfigField("String", "OPEN_DART_API_KEY", "\"\"")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        // 앱 소스가 사용할 Java 언어/API 호환 수준이다. Gradle 실행 JDK 버전과는 별개다.
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        // BuildConfig 키 주입과 Jetpack Compose 코드 생성을 활성화한다.
        buildConfig = true
        compose = true
    }
}

dependencies {
    // BOM은 Compose와 OkHttp 계열 라이브러리가 서로 호환되는 버전을 사용하게 맞춘다.
    implementation(platform(libs.androidx.compose.bom))
    implementation(platform(libs.okhttp.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
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
