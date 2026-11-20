package com.plcoding.cardigan.common.api.openapi

import com.fasterxml.jackson.annotation.JsonInclude
import com.plcoding.cardigan.common.api.validation.Required
import com.plcoding.cardigan.common.api.validation.ValidEnum
import com.plcoding.cardigan.common.api.validation.ValidRange
import com.plcoding.cardigan.common.api.validation.ValidUuid
import com.plcoding.cardigan.common.api.web.API_BASE_PATH
import com.plcoding.cardigan.common.api.web.PublicRouteRegistry
import com.plcoding.cardigan.common.domain.exception.ErrorCode
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.media.ArraySchema
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.IntegerSchema
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.media.ObjectSchema
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.media.StringSchema
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.security.SecurityRequirement
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springframework.beans.factory.ObjectProvider
import org.springframework.http.HttpMethod
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping
import java.lang.reflect.AnnotatedParameterizedType
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType
import java.math.BigDecimal
import kotlin.reflect.full.primaryConstructor

/**
 * Makes the generated document match the backend's real behavior: error responses per operation,
 * the bearer requirement on protected routes, and request DTO schemas whose required list comes from
 * `@Required` instead of Kotlin nullability.
 */
class CardiganOpenApiCustomizer(
    private val handlerMapping: ObjectProvider<RequestMappingHandlerMapping>,
    private val publicRoutes: PublicRouteRegistry,
) : OpenApiCustomizer {

    override fun customise(openApi: OpenAPI) {
        val handlers = handlersByRoute()
        openApi.components.addSchemas(PROBLEM_SCHEMA, problemSchema())
        openApi.components.addSchemas(FIELD_ERROR_SCHEMA, fieldErrorSchema())

        openApi.paths.orEmpty().forEach { (path, item) ->
            item.readOperationsMap().forEach { (method, operation) ->
                customizeOperation(path, method, operation, handlers["$method $path"])
            }
        }

        val schemas = openApi.components.schemas.orEmpty()
        val requestClasses = handlers.values
            .flatMap { handler -> handler.methodParameters.filter { it.hasParameterAnnotation(RequestBody::class.java) } }
            .flatMap { appClasses(it.genericParameterType) }
            .toSet()
        val responseClasses = handlers.values
            .flatMap { appClasses(it.returnType.genericParameterType) }
            .toSet() - requestClasses
        requestClasses.forEach { type -> schemas[type.simpleName]?.let { customizeRequestSchema(type, it) } }
        responseClasses.forEach { type -> schemas[type.simpleName]?.let { customizeResponseSchema(type, it) } }

        openApi.tags = openApi.tags?.sortedBy { it.name }
    }

    private fun handlersByRoute(): Map<String, HandlerMethod> = buildMap {
        handlerMapping.getObject().handlerMethods.forEach { (info, handler) ->
            info.patternValues.forEach { pattern ->
                info.methodsCondition.methods.forEach { method -> put("${method.name} $pattern", handler) }
            }
        }
    }

    private fun customizeOperation(path: String, method: PathItem.HttpMethod, operation: Operation, handler: HandlerMethod?) {
        val errors = sortedMapOf<Int, MutableSet<ErrorCode>>()
        fun add(code: ErrorCode) = errors.getOrPut(code.status) { linkedSetOf() }.add(code)

        add(ErrorCode.INTERNAL_ERROR)
        val isProtected = (path == API_BASE_PATH || path.startsWith("$API_BASE_PATH/")) &&
            publicRoutes.routes.none { it.method == HttpMethod.valueOf(method.name) && it.path == path }
        if (isProtected) {
            add(ErrorCode.UNAUTHENTICATED)
            operation.security = listOf(SecurityRequirement().addList(OpenApiConfig.BEARER_AUTH))
        }
        if (operation.requestBody != null) {
            add(ErrorCode.MALFORMED_REQUEST)
            add(ErrorCode.PAYLOAD_TOO_LARGE)
            add(ErrorCode.UNSUPPORTED_MEDIA_TYPE)
        }
        handler?.method?.getAnnotationsByType(ApiErrors::class.java)?.forEach { apiErrors ->
            apiErrors.codes.forEach { code ->
                check(code.status == apiErrors.status) { "$code has status ${code.status}, not ${apiErrors.status}" }
                add(code)
            }
        }

        errors.forEach { (status, codes) ->
            operation.responses.addApiResponse(
                status.toString(),
                ApiResponse()
                    .description("Error codes: " + codes.sortedBy { it.ordinal }.joinToString { it.name })
                    .content(
                        Content().addMediaType(
                            org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            MediaType().schema(Schema<Any>().`$ref`(PROBLEM_SCHEMA)),
                        ),
                    ),
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun customizeRequestSchema(requestClass: Class<*>, schema: Schema<*>) {
        val fields = requestClass.declaredFields.filterNot { Modifier.isStatic(it.modifiers) }.associateBy { it.name }
        schema.required = fields.values.filter { it.isAnnotationPresent(Required::class.java) }.map { it.name }.ifEmpty { null }
        schema.properties.orEmpty().toMap().forEach { (name, property) ->
            // swagger-core wraps a reference to a named enum in allOf. Clients get the plain reference.
            val reference = property.allOf?.singleOrNull()?.`$ref`
            if (reference != null) {
                schema.properties[name] = Schema<Any>().`$ref`(reference)
                return@forEach
            }
            property.nullable = null
            val field = fields[name] ?: return@forEach
            applyConstraintFormats(field, property as Schema<Any>)
            if (property is ArraySchema) {
                val itemAnnotations = (field.annotatedType as? AnnotatedParameterizedType)
                    ?.annotatedActualTypeArguments?.firstOrNull()
                if (itemAnnotations?.isAnnotationPresent(ValidUuid::class.java) == true) {
                    property.items.format = "uuid"
                }
            }
        }
    }

    /**
     * Response properties are required unless their Kotlin type is nullable. A nullable property is
     * `nullable` in the document, except one that `@JsonInclude(NON_NULL)` leaves out instead.
     */
    private fun customizeResponseSchema(responseClass: Class<*>, schema: Schema<*>) {
        val parameters = responseClass.kotlin.primaryConstructor?.parameters ?: return
        schema.required = parameters.filterNot { it.type.isMarkedNullable }.mapNotNull { it.name }.ifEmpty { null }
        // A property that is left out when it has no value is never sent as null.
        responseClass.declaredFields
            .filter { it.getAnnotation(JsonInclude::class.java)?.value == JsonInclude.Include.NON_NULL }
            .forEach { field -> schema.properties?.get(field.name)?.nullable = null }
    }

    private fun applyConstraintFormats(field: Field, property: Schema<Any>) {
        if (field.isAnnotationPresent(ValidUuid::class.java)) property.format = "uuid"
        field.getAnnotation(ValidEnum::class.java)?.let { validEnum ->
            property.enum = validEnum.value.java.enumConstants.map { it.name }
        }
        field.getAnnotation(ValidRange::class.java)?.let { range ->
            property.minimum = BigDecimal.valueOf(range.min)
            property.maximum = BigDecimal.valueOf(range.max)
        }
    }

    /**
     * The app classes in [root] and every app class nested in them through fields or type arguments,
     * such as list items or a `ResponseEntity` body.
     */
    private fun appClasses(root: Type): Set<Class<*>> {
        val found = linkedSetOf<Class<*>>()
        fun visit(type: Type) {
            when (type) {
                is ParameterizedType -> {
                    visit(type.rawType)
                    type.actualTypeArguments.forEach(::visit)
                }
                is WildcardType -> type.upperBounds.forEach(::visit)
                is Class<*> -> {
                    if (!type.name.startsWith(APP_PACKAGE) || type.isEnum || !found.add(type)) return
                    type.declaredFields.filterNot { Modifier.isStatic(it.modifiers) }.forEach { visit(it.genericType) }
                }
            }
        }
        visit(root)
        return found
    }

    private fun problemSchema(): Schema<*> = ObjectSchema()
        .addProperty("type", StringSchema())
        .addProperty("title", StringSchema())
        .addProperty("status", IntegerSchema().format("int32"))
        .addProperty("detail", StringSchema())
        .addProperty("code", StringSchema())
        .addProperty("fieldErrors", ArraySchema().items(Schema<Any>().`$ref`(FIELD_ERROR_SCHEMA)))
        .required(listOf("type", "title", "status", "detail", "code", "fieldErrors"))

    private fun fieldErrorSchema(): Schema<*> = ObjectSchema()
        .addProperty("field", StringSchema())
        .addProperty("code", StringSchema())
        .addProperty("min", IntegerSchema().format("int64"))
        .addProperty("max", IntegerSchema().format("int64"))
        .required(listOf("field", "code"))

    private companion object {
        const val PROBLEM_SCHEMA = "Problem"
        const val FIELD_ERROR_SCHEMA = "FieldError"
        const val APP_PACKAGE = "com.plcoding.cardigan."
    }
}
