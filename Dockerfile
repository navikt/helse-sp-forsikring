FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-26

ENV TZ="Europe/Oslo"
ENV JDK_JAVA_OPTIONS='-XX:MaxRAMPercentage=75'

WORKDIR /app

COPY sp-forsikring/build/install/app/ /app/
ENTRYPOINT ["java", "-cp", "/app/lib/*", "no.nav.helse.sykepenger.forsikring.AppKt"]
CMD []
