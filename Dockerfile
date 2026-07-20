FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-25

ENV TZ="Europe/Oslo"
ENV JDK_JAVA_OPTIONS='-XX:MaxRAMPercentage=75'

WORKDIR /app

COPY build/install/app/ /app/
ENTRYPOINT ["java", "-cp", "/app/lib/*", "@/app/bin/main.args"]
CMD []
