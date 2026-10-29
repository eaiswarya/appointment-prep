plugins {
  java
  id("org.springframework.boot") version "3.5.16"
  id("io.spring.dependency-management") version "1.1.7"
  id("com.diffplug.spotless") version "8.10.4"
}

group = "com.appointmentprep"

version = "0.0.1-SNAPSHOT"

java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }

repositories { mavenCentral() }

dependencies {
  implementation("org.springframework.boot:spring-boot-starter-actuator")
  implementation("org.springframework.boot:spring-boot-starter-web")
  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

spotless {
  java {
    googleJavaFormat()
    removeUnusedImports()
    trimTrailingWhitespace()
    endWithNewline()
  }
  kotlinGradle { ktfmt() }
}

tasks.withType<Test> { useJUnitPlatform() }
