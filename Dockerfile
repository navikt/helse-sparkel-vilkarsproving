FROM navikt/java:11
# java11 på grunn av cxf

COPY build/libs/*.jar ./

