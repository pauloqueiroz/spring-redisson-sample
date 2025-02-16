# spring-redisson-sample

Project to practice integration with redis using redisson implementing a CRUD of users in cache.

## Requirements
- Java 17
- Maven
- Docker(to run redis)

## How to build
Just clone this repo, navigate to the main folder and execute the following maven build command:
```bash
mvn clean package
```
## Executing localy
The easiest way to run this project locally is executing redis with docker using this command bellow:
```bash
docker run -d --name redis-server -p 6379:6379 redis:latest
```
after the container successfully started, you could run the following command to run redis-cli in the container and be able to consult your cache performing redis commands:
```bash
docker exec -it redis-server redis-cli
```
Now you can just execute the class RedissonSampleApplication.java located in the br.com.example.sample package on your prefered IDE as Java Application that you can test it.
