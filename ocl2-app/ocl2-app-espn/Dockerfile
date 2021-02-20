FROM openjdk:11-jre-slim
EXPOSE 8080
ADD /target/ocl2.jar ocl2.jar
ENTRYPOINT [ \
"java", \
"-jar", \
"ocl2.jar" \
]