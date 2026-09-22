package ru.itmo.highload_ml;

import org.springframework.boot.SpringApplication;

public class TestHighloadMlApplication {

	public static void main(String[] args) {
		SpringApplication.from(HighloadMlApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
