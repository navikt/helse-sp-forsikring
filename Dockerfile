FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-25 AS base

ENV TZ="Europe/Oslo"
ENV JDK_JAVA_OPTIONS='-XX:MaxRAMPercentage=75'

WORKDIR /app

FROM base AS opprydding-dev

COPY opprydding-dev/build/install/app/ /app/
ENTRYPOINT ["java", "-cp", "/app/lib/*", "no.nav.helse.sykepenger.forsikring.opprydding_dev.AppKt"]
CMD []

FROM base AS sp-forsikring

COPY sp-forsikring/build/install/app/ /app/
ENTRYPOINT ["java", "-cp", "/app/lib/*", "no.nav.helse.sykepenger.forsikring.AppKt"]
CMD []
