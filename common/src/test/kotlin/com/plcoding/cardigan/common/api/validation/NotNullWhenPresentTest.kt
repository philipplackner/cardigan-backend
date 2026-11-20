package com.plcoding.cardigan.common.api.validation

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import com.plcoding.cardigan.common.api.ProblemFieldError
import com.plcoding.cardigan.common.domain.exception.FieldErrorCode
import jakarta.validation.Validation
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NotNullWhenPresentTest {

    private val factory = Validation.buildDefaultValidatorFactory()
    private val validator = factory.validator
    private val jsonMapper = JsonMapper.builder()
        .addModule(KotlinModule.Builder().build())
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build()

    @AfterAll
    fun close() = factory.close()

    class UpdateThingRequest : PatchRequest() {

        @ValidText(min = 1, max = 5)
        var name: String? = null
            set(value) {
                field = sent("name", value)
            }

        @ValidEnum(Color::class)
        var color: String? = null
            set(value) {
                field = sent("color", value)
            }
    }

    enum class Color { RED }

    private fun errorsOf(json: String): List<ProblemFieldError> =
        fieldErrorsOf(validator.validate(jsonMapper.readValue(json, UpdateThingRequest::class.java)))

    @Test
    fun `absent properties pass`() {
        assertThat(errorsOf("{}")).isEmpty()
    }

    @Test
    fun `property sent as null is REQUIRED at its path`() {
        assertThat(errorsOf("""{"name":null}""")).containsExactly(ProblemFieldError("name", FieldErrorCode.REQUIRED))
    }

    @Test
    fun `every property sent as null is reported, absent ones are not`() {
        assertThat(errorsOf("""{"name":null,"color":null}""")).containsExactly(
            ProblemFieldError("color", FieldErrorCode.REQUIRED),
            ProblemFieldError("name", FieldErrorCode.REQUIRED),
        )
        assertThat(errorsOf("""{"color":null}""")).containsExactly(ProblemFieldError("color", FieldErrorCode.REQUIRED))
    }

    @Test
    fun `sent value is checked by its own constraints`() {
        assertThat(errorsOf("""{"name":"toolong","color":"BLUE"}""")).containsExactly(
            ProblemFieldError("color", FieldErrorCode.INVALID_VALUE),
            ProblemFieldError("name", FieldErrorCode.TOO_LONG, max = 5),
        )
    }

    @Test
    fun `valid sent value passes and is kept`() {
        val request = jsonMapper.readValue("""{"name":"ok"}""", UpdateThingRequest::class.java)

        assertThat(fieldErrorsOf(validator.validate(request))).isEmpty()
        assertThat(request.name).isEqualTo("ok")
        assertThat(request.color).isEqualTo(null)
    }

    @Test
    fun `null next to a broken value reports both fields`() {
        assertThat(errorsOf("""{"name":null,"color":"BLUE"}""")).containsExactly(
            ProblemFieldError("color", FieldErrorCode.INVALID_VALUE),
            ProblemFieldError("name", FieldErrorCode.REQUIRED),
        )
    }

    @Test
    fun `unknown property sent as null is ignored`() {
        assertThat(errorsOf("""{"other":null}""")).isEmpty()
    }

    @Test
    fun `duplicate key counts with its last value`() {
        assertThat(errorsOf("""{"name":null,"name":"ok"}""")).isEmpty()
        assertThat(errorsOf("""{"name":"ok","name":null}""")).containsExactly(
            ProblemFieldError("name", FieldErrorCode.REQUIRED),
        )
    }

    @Test
    fun `property set to null in code is REQUIRED too`() {
        val request = UpdateThingRequest().apply { name = null }

        assertThat(fieldErrorsOf(validator.validate(request))).containsExactly(
            ProblemFieldError("name", FieldErrorCode.REQUIRED),
        )
    }
}
