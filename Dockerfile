FROM navikt/java:12
# java11 på grunn av cxf

COPY build/libs/*.jar ./

