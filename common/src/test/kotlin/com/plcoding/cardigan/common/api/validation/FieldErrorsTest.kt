package com.plcoding.cardigan.common.api.validation

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import com.plcoding.cardigan.common.api.ProblemFieldError
import com.plcoding.cardigan.common.domain.exception.FieldErrorCode
import jakarta.validation.Valid
import jakarta.validation.Validation
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FieldErrorsTest {
    private val factory = Validation.buildDefaultValidatorFactory()
    private val validator = factory.validator

    @AfterAll
    fun close() = factory.close()

    data class Item(
        @Required
        @ValidText(min = 1, max = 3)
        val name: String?,
    )

    data class Request(
        @Required
        @ValidText(max = 5)
        @ValidUuid
        val id: String?,
        @ValidText(min = 2, max = 10)
        val title: String? = null,
        val tags: List<@ValidUuid String>? = null,
        @field:Valid
        val items: List<Item>? = null,
    )

    private fun errorsOf(request: Request) = fieldErrorsOf(validator.validate(request))

    @Test
    fun `short non-UUID value is INVALID_FORMAT`() {
        assertThat(errorsOf(Request(id = "a"))).containsExactly(ProblemFieldError("id", FieldErrorCode.INVALID_FORMAT))
    }

    @Test
    fun `null required field is only REQUIRED`() {
        assertThat(errorsOf(Request(id = null))).containsExactly(ProblemFieldError("id", FieldErrorCode.REQUIRED))
    }

    @Test
    fun `TOO_LONG wins over INVALID_FORMAT on one field`() {
        assertThat(errorsOf(Request(id = "not-a-uuid"))).containsExactly(
            ProblemFieldError("id", FieldErrorCode.TOO_LONG, max = 5),
        )
    }

    @Test
    fun `fields are ordered by path and carry only their own limit`() {
        assertThat(errorsOf(Request(id = null, title = "x"))).containsExactly(
            ProblemFieldError("id", FieldErrorCode.REQUIRED),
            ProblemFieldError("title", FieldErrorCode.TOO_SHORT, min = 2),
        )
    }

    @Test
    fun `list items and nested objects get JSON paths`() {
        val errors =
            errorsOf(
                Request(
                    id = "abc",
                    tags = listOf("0b6a1f43-58c2-4d0e-9a35-3f1c9e2b7d10", "nope"),
                    items = listOf(Item("ok"), Item("toolong"), Item(null)),
                ),
            )

        assertThat(errors).containsExactly(
            ProblemFieldError("id", FieldErrorCode.INVALID_FORMAT),
            ProblemFieldError("items[1].name", FieldErrorCode.TOO_LONG, max = 3),
            ProblemFieldError("items[2].name", FieldErrorCode.REQUIRED),
            ProblemFieldError("tags[1]", FieldErrorCode.INVALID_FORMAT),
        )
    }

    @Test
    fun `no violations give no field errors`() {
        assertThat(fieldErrorsOf(emptyList())).isEmpty()
    }
}
