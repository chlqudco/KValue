// 루트 빌드 파일이다. 실제 Android 설정은 app 모듈에 두고 여기서는 사용할 플러그인 버전만 공유한다.
// apply false는 루트 프로젝트에는 플러그인을 적용하지 않고 하위 모듈이 필요할 때 적용하도록 지연한다.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
