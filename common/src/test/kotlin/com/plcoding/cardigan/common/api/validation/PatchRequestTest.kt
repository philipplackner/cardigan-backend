package com.plcoding.cardigan.common.api.validation

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.plcoding.cardigan.common.api.ProblemFieldError
import com.plcoding.cardigan.common.domain.exception.FieldErrorCode
import jakarta.validation.Valid
import jakarta.validation.Validation
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import tools.jackson.module.kotlin.jacksonObjectMapper

enum class PatchColorDto { RED, GREEN }

class PatchThingRequest : PatchRequest() {

    @ValidText(min = 1, max = 5)
    var name: String? = null
        set(value) {
            field = sent("name", value)
        }

    @ValidEnum(PatchColorDto::class)
    var color: String? = null
        set(value) {
            field = sent("color", value)
        }
}

data class PatchParentRequest(
    @field:Valid
    val thing: PatchThingRequest?,
)

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PatchRequestTest {

    private val factory = Validation.buildDefaultValidatorFactory()
    private val validator = factory.validator
    private val jsonMapper = jacksonObjectMapper()

    @AfterAll
    fun close() = factory.close()

    private fun errorsOf(request: Any) = fieldErrorsOf(validator.validate(request))

    private fun read(json: String): PatchThingRequest = jsonMapper.readValue(json, PatchThingRequest::class.java)

    @Test
    fun `absent properties pass`() {
        assertThat(errorsOf(PatchThingRequest())).isEmpty()
    }

    @Test
    fun `property sent as null is REQUIRED at its path`() {
        val request = PatchThingRequest().apply { name = null }

        assertThat(errorsOf(request)).containsExactly(ProblemFieldError("name", FieldErrorCode.REQUIRED))
    }

    @Test
    fun `every property sent as null is reported, ordered by field`() {
        val request = PatchThingRequest().apply {
            name = null
            color = null
        }

        assertThat(errorsOf(request)).containsExactly(
            ProblemFieldError("color", FieldErrorCode.REQUIRED),
            ProblemFieldError("name", FieldErrorCode.REQUIRED),
        )
    }

    @Test
    fun `sent value is checked by its own constraints`() {
        val request = PatchThingRequest().apply {
            name = "toolong"
            color = "BLUE"
        }

        assertThat(errorsOf(request)).containsExactly(
            ProblemFieldError("color", FieldErrorCode.INVALID_VALUE),
            ProblemFieldError("name", FieldErrorCode.TOO_LONG, max = 5),
        )
    }

    @Test
    fun `valid sent values pass`() {
        val request = PatchThingRequest().apply {
            name = "Bug"
            color = "RED"
        }

        assertThat(errorsOf(request)).isEmpty()
    }

    @Test
    fun `null property and broken property are reported together`() {
        val request = PatchThingRequest().apply {
            name = null
            color = "BLUE"
        }

        assertThat(errorsOf(request)).containsExactly(
            ProblemFieldError("color", FieldErrorCode.INVALID_VALUE),
            ProblemFieldError("name", FieldErrorCode.REQUIRED),
        )
    }

    @Test
    fun `value sent after a null clears the null`() {
        val request = PatchThingRequest().apply {
            name = null
            name = "Bug"
        }

        assertThat(request.nullProperties()).isEmpty()
        assertThat(errorsOf(request)).isEmpty()
    }

    @Test
    fun `nested patch request reports the full path`() {
        val request = PatchParentRequest(PatchThingRequest().apply { name = null })

        assertThat(errorsOf(request)).containsExactly(ProblemFieldError("thing.name", FieldErrorCode.REQUIRED))
    }

    @Test
    fun `JSON null is recorded and an absent property is not`() {
        assertThat(read("""{"name":null}""").nullProperties()).isEqualTo(setOf("name"))
        assertThat(read("""{}""").nullProperties()).isEmpty()
        assertThat(read("""{"name":null,"color":null}""").nullProperties()).isEqualTo(setOf("name", "color"))
    }

    @Test
    fun `JSON value is read and not recorded`() {
        val request = read("""{"name":"Bug"}""")

        assertThat(request.name).isEqualTo("Bug")
        assertThat(request.color).isNull()
        assertThat(request.nullProperties()).isEmpty()
    }

    @Test
    fun `base type adds no JSON property`() {
        val request = PatchThingRequest().apply { name = null }

        val json = jsonMapper.readValue(jsonMapper.writeValueAsString(request), Map::class.java)

        assertThat(json.keys).isEqualTo(setOf<Any?>("name", "color"))
    }
}
