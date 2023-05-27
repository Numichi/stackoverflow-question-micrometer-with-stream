## Question
I configured a basic Spring Boot with Micrometer and Spring Stream (with RabbitMQ). I would like to configure that traceId can be passed into another service through Spring Stream.

I understand it loses trace info when I use tryNextEmit. So it will generate a new. I also understand, RabbitMQ messages get a "traceparent" header with "00-xxxxxx-xxx-01" new value.

So my question: How can I connect both traces that Spring Stream uses older trace information and one record appear in Zipkin?

## PoC
### Start
1. Start dockers
```bash
$ docker run -p 15672:15672 -p 5672:5672 bitnami/rabbitmq

$ docker run -p 9411:9411 openzipkin/zipkin
```
2. Start project
3. Send request
```http request
GET http://localhost:8080/
```
### Result
![](zipkin.png)
![](zipkin_http.png)
![](zipkin_rabbit.png)

