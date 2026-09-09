plugins {
	java
	id("org.springframework.boot") version "4.1.1"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.umesh"
version = "0.0.1-SNAPSHOT"
description = "Hotel Booking Platform"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-h2console")
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	// Phase 10 (design doc 17): OpenAPI/Swagger UI. The 3.x line is springdoc's Spring Boot 4
	// line - 2.x targets Boot 3 and does not resolve here. Unlike resilience4j-spring-boot4
	// (absent from Maven Central entirely, see 16.2), this one genuinely exists, so the
	// hand-written stand-in that phase needed is not required here.
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1")
	compileOnly("org.projectlombok:lombok")
	runtimeOnly("com.h2database:h2")
	// MySQL reference profile only (design doc 13.1, application-mysql.yml) - not active by
	// default. runtimeOnly, not implementation: the profile must be runnable if opted into,
	// never compilable-against, so nothing in main code can accidentally depend on it.
	runtimeOnly("com.mysql:mysql-connector-j")
	annotationProcessor("org.projectlombok:lombok")
	testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
	testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testCompileOnly("org.projectlombok:lombok")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	testAnnotationProcessor("org.projectlombok:lombok")

	// --- Phase 0 §1.3 additions ---
	testImplementation("org.assertj:assertj-core")
	testImplementation("org.awaitility:awaitility")
}

tasks.withType<JavaCompile> {
	// -Xlint:all minus "processing": Lombok's annotation processor triggers a spurious
	// "no processor claimed annotation" warning on @SpringBootApplication; unrelated to our code.
	options.compilerArgs.addAll(listOf("-Xlint:all,-processing", "-Werror", "-parameters"))
}

tasks.withType<Test> {
	useJUnitPlatform()
	testLogging {
		events("passed", "skipped", "failed")
	}
}
