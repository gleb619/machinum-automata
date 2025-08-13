FROM gradle:8-jdk21 as build

RUN --mount=type=bind,source=./.gradle-cache,target=/root/.gradle

WORKDIR /machinum-automata

COPY build.gradle settings.gradle gradlew ./
COPY app/build.gradle ./app/
COPY engine/build.gradle ./engine/
COPY models/build.gradle ./models/
COPY gradle ./gradle

RUN ./gradlew dependencies --build-cache || echo "Failed to download dependencies, but continuing build."

COPY . ./

RUN ./gradlew build -x test && \
    ./gradlew shadowJar

FROM eclipse-temurin:21-jdk

WORKDIR /machinum-automata

COPY conf conf
COPY ./app/src/main/resources/web ./web
COPY --from=build /machinum-automata/app/build/libs/app-1.0.0-all.jar app.jar

# Hotfix for SeleniumUtils.java and lost manifest(which starts version 2.45.0)
RUN mkdir -p META-INF && \
    cat > META-INF/MANIFEST.MF <<EOF
Manifest-Version: 1.0
Implementation-Title: selenium-api
Implementation-Version: 4.15.0
Implementation-Vendor: Selenium
Bundle-Name: selenium-api
Bundle-Version: 4.15.0
Bundle-Vendor: Selenium
Selenium-Version: 4.15.0
Created-By: Maven Archiver
Build-Jdk-Spec: 21
EOF

ENV CLASSPATH="/machinum-automata:/machinum-automata/*" \
    APP_STATIC_RESOURCES="/machinum-automata/web"

EXPOSE 8076

CMD ["java", "-cp", "/machinum-automata/app.jar:/machinum-automata", "-jar", "app.jar"]
