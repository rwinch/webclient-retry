package com.example.demo;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.reactivestreams.Publisher;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalTime;
import java.util.function.Function;

public class DemoApplicationTests {

	WebClient rest;

	MockWebServer server = new MockWebServer();

	@Before
	public void setup() {
		this.rest = WebClient.builder()
			.baseUrl(this.server.url("/").toString())
			.build();
	}

	@Test
	public void go() {
		this.server.enqueue(new MockResponse().setHeader("Location", this.server.url("/").toString()));
		this.server.enqueue(new MockResponse().setResponseCode(500));
		this.server.enqueue(new MockResponse().setBody("{}"));

		String result =
			this.rest.get()
				.exchange()
				.map(r -> r.headers().asHttpHeaders().getLocation())
				.flatMap(location -> {
					System.out.println("here");
					return this.rest.get().uri(location).retrieve().bodyToMono(String.class)
							.retryWhen(retry(4));
				})
				.block();

		System.out.println("here ================= " + result);
	}

	// http://projectreactor.io/docs/core/release/reference/#faq.exponentialBackoff
	private Function<Flux<Throwable>, Publisher<?>> retry(int times) {
		return companion -> companion
				.doOnNext(s -> System.out.println(s + " at " + LocalTime.now()))
				.zipWith(Flux.range(1, times), (error, index) -> {
					if (index < times) return index;
					else throw Exceptions.propagate(error);
				})
				.flatMap(index -> Mono.delay(Duration.ofMillis(index * 100)))
				.doOnNext(s -> System.out.println("retried at " + LocalTime.now()));
	}
}
