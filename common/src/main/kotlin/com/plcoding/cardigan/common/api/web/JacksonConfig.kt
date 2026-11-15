package com.plcoding.cardigan.common.api.web

import com.plcoding.cardigan.common.api.ProblemDetailMixin
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.ProblemDetail
import tools.jackson.core.JsonGenerator
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.MapperFeature
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.ValueSerializer
import tools.jackson.databind.cfg.CoercionAction
import tools.jackson.databind.cfg.CoercionInputShape
import tools.jackson.databind.module.SimpleModule
import tools.jackson.databind.type.LogicalType
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Configuration(proxyBeanMethods = false)
class JacksonConfig {

    @Bean
    fun cardiganJsonMapperCustomizer() = JsonMapperBuilderCustomizer { builder ->
        builder
            .addMixIn(ProblemDetail::class.java, ProblemDetailMixin::class.java)
            .addModule(SimpleModule("cardigan-instant").addSerializer(Instant::class.java, InstantSerializer))
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .withCoercionConfig(LogicalType.Textual) { config ->
                config.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                config.setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                config.setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail)
            }
            .withCoercionConfigDefaults { config ->
                config.setCoercion(CoercionInputShape.EmptyString, CoercionAction.Fail)
            }
    }

    /** Always writes milliseconds, so whole seconds come out as `.000Z`. */
    private object InstantSerializer : ValueSerializer<Instant>() {

        private val formatter = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

        override fun serialize(value: Instant, gen: JsonGenerator, ctxt: SerializationContext) {
            gen.writeString(formatter.format(value))
        }
    }
}
