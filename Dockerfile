FROM openjdk:8-jre

VOLUME ["/hygieia/logs"]

RUN mkdir /hygieia/config

EXPOSE 8081

ENV PROP_FILE /hygieia/config/application.properties
ENV PROJ_JAR api-audit.jar

WORKDIR /hygieia

COPY target/*.jar /hygieia/
COPY docker/properties-builder.sh /hygieia/

CMD ./properties-builder.sh &&\
  java -Djava.security.egd=file:/dev/./urandom -jar $PROJ_JAR --spring.config.location=$PROP_FILE
