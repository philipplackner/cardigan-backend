package com.plcoding.cardigan.common.api

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import java.net.URI

/**
 * Serializes [org.springframework.http.ProblemDetail] for the app's `JsonMapper`, which
 * [ProblemResponseWriter] uses outside Spring MVC: properties flattened, `instance` never written.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder("type", "title", "status", "detail")
internal abstract class ProblemDetailMixin {

    @JsonIgnore
    abstract fun getInstance(): URI?

    @JsonAnyGetter
    abstract fun getProperties(): Map<String, Any?>?

    @JsonAnySetter
    abstract fun setProperty(name: String, value: Any?)
}
