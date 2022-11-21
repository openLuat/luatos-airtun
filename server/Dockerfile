FROM adoptopenjdk:11-jre-hotspot

RUN mkdir /opt/app

COPY target/airtun-1.0-SNAPSHOT.jar /opt/app/airtun.jar

WORKDIR /opt/airtun

CMD ["java", "-jar", "/opt/app/airtun.jar"]
