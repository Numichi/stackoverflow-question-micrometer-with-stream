@file:Suppress("ReactiveStreamsUnusedPublisher")

package com.example.demo

import io.micrometer.context.ContextSnapshot
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationRegistry
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor
import java.util.function.Supplier
import java.util.function.Function
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.reactor.ReactorContext
import kotlinx.coroutines.reactor.mono
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.messaging.Message
import org.springframework.messaging.support.MessageBuilder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks

data class Data(val data: String)

@SpringBootApplication
class DemoApplication {

    @Bean
    fun streamData(): Sinks.Many<Data> {
        return Sinks.many().unicast().onBackpressureBuffer()
    }

    @Bean
    fun mqSend(stream: Sinks.Many<Data>): Supplier<Flux<Message<Data>>> = Supplier {
        stream.asFlux().map {
            MessageBuilder.withPayload(it).build()
        }
    }

    @Bean
    fun mqReceive(): Function<Flux<Message<Data>>, Mono<Void>> {
        return Function { flux ->
            flux.doOnNext { println(it) }.then()
        }
    }
}

fun main(args: Array<String>) {
    runApplication<DemoApplication>(*args)
}

@RestController
class TestController(
    private val observationRegistry: ObservationRegistry,
    private val stream: Sinks.Many<Data>, // @Bean streamData
) {

    @GetMapping("/")
    fun foo() = mono {
        runObserved("foo", observationRegistry) {
            stream.tryEmitNext(Data("foo"))
        }
    }
}

suspend inline fun <T : Any> observeCtx(crossinline block: suspend () -> T?): T? {
    return ContextSnapshot.setThreadLocalsFrom(coroutineContext[ReactorContext]!!.context, ObservationThreadLocalAccessor.KEY).use {
        return@use block()
    }
}

suspend fun <T : Any> runObserved(name: String, observationRegistry: ObservationRegistry, block: suspend () -> T?): T? {
    return observeCtx {
        val observation = Observation.start(name, observationRegistry)
        val result = runCatching { block() }

        result.exceptionOrNull()?.also { exception ->
            observation.error(exception)
        }

        observation.stop()

        return@observeCtx result.getOrThrow()
    }
}