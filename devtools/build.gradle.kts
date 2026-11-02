buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.24")
    }
}

apply(plugin = "org.jetbrains.kotlin.jvm")
apply(plugin = "application")

dependencies {
    "implementation"(project(":core"))
    "implementation"("com.google.code.gson:gson:2.10.1")
}

extensions.configure<org.gradle.api.plugins.JavaPluginExtension> {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

extensions.configure<org.gradle.api.plugins.JavaApplication> {
    mainClass.set("com.tvtranslate.devtools.MainKt")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "17"
}

tasks.withType<JavaExec>().configureEach {
    jvmArgs("-Dfile.encoding=UTF-8")
}
