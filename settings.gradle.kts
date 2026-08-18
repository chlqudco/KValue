// Gradle이 빌드 스크립트 플러그인을 찾을 저장소와 프로젝트에 포함할 모듈을 정의한다.
// 일반 라이브러리 저장소 설정과 플러그인 저장소 설정은 해석 시점이 달라 별도 블록으로 관리한다.
pluginManagement {
    repositories {
        google {
            // Google 저장소에서는 Android·Google·AndroidX 그룹만 찾도록 범위를 좁힌다.
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
// Foojay resolver가 현재 플랫폼에 맞는 Java toolchain을 찾을 수 있게 한다.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
// 각 모듈이 임의 저장소를 추가하지 못하게 하고 루트의 저장소 목록을 단일 기준으로 사용한다.
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

// Gradle 프로젝트 이름과 실제 빌드에 참여하는 단일 Android 앱 모듈이다.
rootProject.name = "KValue"
include(":app")
