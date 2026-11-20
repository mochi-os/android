// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.api

import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.google.gson.TypeAdapter
import com.google.gson.TypeAdapterFactory
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.KProperty1
import kotlin.reflect.KType
import kotlin.reflect.KTypeParameter
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaField

/**
 * Holds what Gson reads into this app's models to the types they declare.
 * Gson fills a model by reflection: a JSON null is written into a non-null
 * field as it stands, and a class with no constructor that takes nothing is
 * made without running one, so every field the JSON leaves out is null too.
 * Either way the null surfaces later, at the first use, as a crash far from
 * the answer that carried it.
 *
 * After the read, a non-null property left null takes the class's default,
 * from its primary constructor; where the class gives it none, the read fails
 * with a [JsonParseException], so a bad answer is refused where it arrives.
 * Nullable properties, and every class outside `org.mochios`, are left as
 * read, and an answer that holds every non-null field is returned untouched.
 *
 * It needs each class's Kotlin metadata. A class without it - a Java class,
 * or one a shrinker stripped - and one kotlin-reflect cannot read are left
 * exactly as Gson reads them: the guard falls silent rather than failing a
 * parse it cannot judge.
 *
 * @param properties What Kotlin says of a class's properties; a stand-in
 *   lets a test make kotlin-reflect fail.
 */
internal class NullGuard(
    private val properties: (KClass<*>) -> Collection<KProperty1<out Any, *>> = { klass -> klass.memberProperties },
) : TypeAdapterFactory {

    /** Each model class's shape, worked out once; [NONE] where there is nothing to guard. */
    private val shapes = ConcurrentHashMap<Class<*>, Shape>()

    private companion object {
        private val NONE = Shape(emptyList(), emptyList(), null)
    }

    override fun <T> create(gson: Gson, type: TypeToken<T>): TypeAdapter<T>? {
        val raw = type.rawType
        if (!guarded(raw)) return null
        val delegate = gson.getDelegateAdapter(this, type)
        return object : TypeAdapter<T>() {
            override fun write(writer: JsonWriter, value: T?) = delegate.write(writer, value)

            override fun read(reader: JsonReader): T? {
                val value = delegate.read(reader) ?: return null
                try {
                    complete(raw, value)
                } catch (e: JsonParseException) {
                    throw e
                } catch (e: Throwable) {
                    // Reflection that fails here leaves the value as read.
                    if (e is VirtualMachineError) throw e
                }
                return value
            }
        }
    }

    /** Whether [raw] is one of this app's Kotlin classes that Gson may build. */
    private fun guarded(raw: Class<*>): Boolean =
        raw.name.startsWith("org.mochios.") &&
            raw.isAnnotationPresent(Metadata::class.java) &&
            !raw.isInterface && !raw.isEnum && !raw.isAnnotation && !raw.isArray &&
            !Modifier.isAbstract(raw.modifiers)

    /**
     * Fill each non-null field of [value] left null with the class's default,
     * or refuse [value] where the class has none for it.
     */
    private fun complete(raw: Class<*>, value: Any) {
        // A plain read first: the class is looked into only once a field it
        // declares is null, and most answers hold no null at all.
        val fields = references(raw)
        if (fields.none { field -> field.get(value) == null }) return
        val shape = shapes.getOrPut(raw) { shapeOf(raw) }
        val empty = shape.required.filter { field -> field.get(value) == null }
        if (empty.isEmpty()) return
        val defaults = shape.build(raw, value) ?: return
        for (field in empty) {
            val fallback = field.get(defaults)
                ?: throw JsonParseException("${raw.name}.${field.name} is null and has no default")
            field.set(value, fallback)
        }
    }

    /** The reference-typed instance fields of [raw] and its superclasses, made readable. */
    private val fieldLists = ConcurrentHashMap<Class<*>, List<Field>>()

    private fun references(raw: Class<*>): List<Field> = fieldLists.getOrPut(raw) {
        generateSequence(raw) { it.superclass }
            .takeWhile { it != Any::class.java }
            .flatMap { it.declaredFields.asSequence() }
            .filter { field -> !Modifier.isStatic(field.modifiers) && !field.type.isPrimitive }
            .onEach { field -> field.isAccessible = true }
            .toList()
    }

    /**
     * Which of [raw]'s fields must not be null, and how to build its
     * defaults. Nothing to guard where Kotlin cannot describe the class.
     */
    private fun shapeOf(raw: Class<*>): Shape = try {
        val klass: KClass<*> = raw.kotlin
        val properties = properties(klass).mapNotNull { property ->
            val field = property.javaField ?: return@mapNotNull null
            if (Modifier.isStatic(field.modifiers) || field.type.isPrimitive) return@mapNotNull null
            field.isAccessible = true
            property.name to Pair(field, required(property.returnType, field))
        }.toMap()
        val constructor = klass.primaryConstructor?.also { it.isAccessible = true }
        Shape(
            required = properties.values.filter { (_, required) -> required }.map { (field, _) -> field },
            parameters = constructor?.parameters.orEmpty().map { parameter -> parameter to properties[parameter.name]?.first },
            constructor = constructor,
        )
    } catch (e: Throwable) {
        // kotlin-reflect throws errors of its own for a class it cannot read;
        // such a class is left as Gson reads it.
        if (e is VirtualMachineError) throw e
        NONE
    }

    /**
     * Whether a property of [type] backed by [field] must hold a value. A type
     * parameter's nullability is the argument's, unknown here, so it is left;
     * a delegate's field holds the delegate, never null whatever it yields.
     */
    private fun required(type: KType, field: Field): Boolean =
        field.name.endsWith("\$delegate") || (!type.isMarkedNullable && type.classifier !is KTypeParameter)

    private class Shape(
        val required: List<Field>,
        /** Each constructor parameter with the field holding its value, null where it has none. */
        val parameters: List<Pair<KParameter, Field?>>,
        val constructor: KFunction<*>?,
    ) {
        /**
         * An instance built through the primary constructor from [value]'s
         * fields, leaving each one that is null to its default. Refused where a
         * null parameter has no default and may not be null, or the class's
         * own constructor refuses the values; null where reflection cannot
         * call the constructor, which leaves [value] as read.
         */
        fun build(raw: Class<*>, value: Any): Any? {
            val constructor = constructor
                ?: throw JsonParseException("${raw.name} has a null field and no constructor to default it")
            val arguments = mutableMapOf<KParameter, Any?>()
            for ((parameter, field) in parameters) {
                val given = field?.get(value)
                when {
                    given != null -> arguments[parameter] = given
                    parameter.isOptional -> Unit
                    parameter.type.isMarkedNullable -> arguments[parameter] = null
                    else -> throw JsonParseException("${raw.name}.${parameter.name} is null and has no default")
                }
            }
            return try {
                constructor.callBy(arguments)
            } catch (e: InvocationTargetException) {
                throw JsonParseException("${raw.name} refused its defaults", e.targetException)
            } catch (e: Throwable) {
                if (e is VirtualMachineError) throw e
                null
            }
        }
    }
}
