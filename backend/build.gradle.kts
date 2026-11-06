import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone
import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spotless)
    alias(libs.plugins.errorprone)
}

group = "com.paralumo"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

// Mockito (from spring-boot-starter-test) attaches as a -javaagent instead of loading itself at
// runtime, which JDK 21+ warns about.
val mockitoAgent = configurations.create("mockitoAgent")

dependencies {
    implementation(platform(SpringBootPlugin.BOM_COORDINATES))
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.security.oauth2.jose)
    implementation(libs.springdoc.webmvc.ui)
    implementation(libs.jspecify)
    runtimeOnly(libs.flyway.postgresql)
    runtimeOnly(libs.postgresql)

    errorprone(libs.errorprone.core)
    errorprone(libs.nullaway)

    testImplementation(platform(SpringBootPlugin.BOM_COORDINATES))
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.spring.security.test)
    testRuntimeOnly(libs.junit.platform.launcher)

    mockitoAgent(platform(SpringBootPlugin.BOM_COORDINATES))
    mockitoAgent(libs.mockito.core) { isTransitive = false }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    // Compiler warnings are errors (backend/AGENTS.md). -parameters keeps parameter names for Spring.
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror", "-parameters"))
    options.errorprone {
        disableWarningsInGeneratedCode = true
        check("NullAway", CheckSeverity.ERROR)
        option("NullAway:AnnotatedPackages", "com.paralumo")
        option("NullAway:JSpecifyMode", "true")
    }
}

val integrationTag = "integration"

tasks.test {
    description = "Runs unit tests (everything not tagged @Tag(\"integration\"))."
    useJUnitPlatform { excludeTags(integrationTag) }
}

val integrationTest = tasks.register<Test>("integrationTest") {
    description = "Runs integration tests against Postgres (TEST_DATABASE_URL). Needs `docker compose up -d`."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags(integrationTag) }
    shouldRunAfter(tasks.test)
}

val openApiFile = layout.projectDirectory.file("../docs/api/openapi.json")

// Exports the spec by running the OpenAPI contract test in write mode. Without the flag the same
// test fails when docs/api/openapi.json differs from what the controllers produce (drift check).
tasks.register<Test>("exportOpenApi") {
    description = "Writes ../docs/api/openapi.json from the running application's springdoc output."
    group = "documentation"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags(integrationTag) }
    filter { includeTestsMatching("com.paralumo.api.OpenApiContractTest") }
    systemProperty("openapi.export", "true")
    outputs.upToDateWhen { false }
}

tasks.withType<Test>().configureEach {
    // Mockito's inline mock maker appends to the bootstrap classpath, which makes class data
    // sharing unusable; -Xshare:off says so up front instead of the JVM printing a warning.
    jvmArgs("-javaagent:${mockitoAgent.asPath}", "-Xshare:off")
    systemProperty("openapi.file", openApiFile.asFile.absolutePath)
    testLogging { events("failed") }
}

tasks.check {
    dependsOn(integrationTest)
}

spotless {
    java {
        googleJavaFormat(libs.versions.google.java.format.get())
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts")
        trimTrailingWhitespace()
        endWithNewline()
    }
}
