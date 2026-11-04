package com.plcoding.cardigan

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class CardiganApplication

fun main(args: Array<String>) {
    runApplication<CardiganApplication>(*args)
}
