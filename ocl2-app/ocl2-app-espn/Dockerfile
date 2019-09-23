FROM openjdk:8-jdk-alpine
EXPOSE 8080
ADD /target/ocl2.jar ocl2.jar
ENTRYPOINT [ \
"java", \
"-jar", \
"ocl2.jar" \
]