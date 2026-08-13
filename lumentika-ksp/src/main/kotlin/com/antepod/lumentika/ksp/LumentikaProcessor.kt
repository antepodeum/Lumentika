package com.antepod.lumentika.ksp

import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.Nullability
import com.google.devtools.ksp.symbol.Variance
import com.google.devtools.ksp.validate

/** KSP entry point that generates typed argument factories for `@UIComponent` classes. */
public class LumentikaProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        LumentikaProcessor(environment.codeGenerator, environment.logger)
}

private class LumentikaProcessor(private val code: CodeGenerator, private val logger: KSPLogger) :
    SymbolProcessor {
    private val generated = mutableSetOf<String>()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols =
            resolver
                .getSymbolsWithAnnotation("com.antepod.lumentika.component.UIComponent")
                .filterIsInstance<KSClassDeclaration>()
        val deferred = symbols.filterNot(KSClassDeclaration::validate).toList()
        symbols.filter(KSClassDeclaration::validate).forEach(::generate)
        return deferred
    }

    private fun generate(type: KSClassDeclaration) {
        val pkg = type.packageName.asString()
        val name = type.simpleName.asString()
        val qualifiedName = type.qualifiedName?.asString() ?: return
        if (!generated.add(qualifiedName)) return
        if (type.classKind != ClassKind.CLASS || Modifier.ABSTRACT in type.modifiers) {
            logger.error("@UIComponent requires a concrete class", type)
            return
        }
        if (type.parentDeclaration != null) {
            logger.error("@UIComponent must be a top-level class", type)
            return
        }
        if (type.typeParameters.isNotEmpty()) {
            logger.error("@UIComponent cannot declare type parameters", type)
            return
        }
        if (!type.isComponentSubclass()) {
            logger.error("@UIComponent class must extend Component", type)
            return
        }
        val constructor = type.primaryConstructor
        if (
            constructor != null &&
                (constructor.parameters.any { !it.hasDefault } ||
                    Modifier.PRIVATE in constructor.modifiers ||
                    Modifier.PROTECTED in constructor.modifiers)
        ) {
            logger.error("@UIComponent requires an accessible zero-argument constructor", type)
            return
        }
        val source = type.containingFile
        if (source == null) {
            logger.error("@UIComponent must be declared in source", type)
            return
        }
        val declarations =
            type
                .getDeclaredProperties()
                .filter {
                    Modifier.PRIVATE !in it.modifiers &&
                        Modifier.PROTECTED !in it.modifiers &&
                        Modifier.INTERNAL !in it.modifiers
                }
                .mapNotNull(::componentDeclaration)
                .toList()
        val slots = declarations.filter { it.kind.isSlot }
        val canonicalSlot = slots.firstOrNull { it.name == "content" } ?: slots.firstOrNull()
        val className = name.identifier()
        val parameters = buildList {
            add("instance: $className = $className()")
            declarations.filterNot { it.kind.isSlot }.forEach { add(it.parameter()) }
            slots.filterNot { it == canonicalSlot }.forEach { add(it.parameter()) }
            canonicalSlot?.let { add(it.parameter()) }
        }
        val file = code.createNewFile(Dependencies(false, source), pkg, "${name}Dsl")
        val factoryName = name.replaceFirstChar { it.lowercase() }.identifier()
        file.writer().use { out ->
            out.appendLine("package $pkg")
                .appendLine()
                .appendLine("import com.antepod.lumentika.component.*")
                .appendLine("import com.antepod.lumentika.reactive.*")
                .appendLine("import com.antepod.lumentika.runtime.*")
                .appendLine()
                .appendLine("/** Creates, configures, and persistently mounts a [$className]. */")
            if (parameters.isEmpty()) {
                out.appendLine("public fun UiScope.$factoryName(): Element {")
            } else {
                out.appendLine("public fun UiScope.$factoryName(")
                parameters.forEach { out.appendLine("    $it,") }
                out.appendLine("): Element {")
            }
            declarations.filterNot { it.kind.isSlot }.forEach { it.writeConfiguration(out) }
            slots.filterNot { it == canonicalSlot }.forEach { it.writeConfiguration(out) }
            canonicalSlot?.writeConfiguration(out)
            out.appendLine("    return instance.mount(this)")
            out.appendLine("}")
        }
    }

    private fun KSClassDeclaration.isComponentSubclass(): Boolean = superTypes.any { superType ->
        val declaration = superType.resolve().declaration as? KSClassDeclaration
        declaration?.qualifiedName?.asString() == "com.antepod.lumentika.component.Component" ||
            declaration?.isComponentSubclass() == true
    }

    private fun componentDeclaration(property: KSPropertyDeclaration): ComponentDeclaration? {
        val resolved = property.type.resolve()
        if (resolved.isError) {
            logger.error("Cannot resolve component declaration type", property)
            return null
        }
        val kind = resolved.declaration.qualifiedName?.asString() ?: return null
        val declarationKind =
            DeclarationKind.entries.firstOrNull { it.qualifiedName == kind } ?: return null
        val valueType =
            if (declarationKind.hasTypeArgument) {
                resolved.arguments.singleOrNull()?.type?.resolve()?.render()
                    ?: run {
                        logger.error(
                            "Component declaration must have one concrete type argument",
                            property,
                        )
                        return null
                    }
            } else null
        return ComponentDeclaration(property.simpleName.asString(), declarationKind, valueType)
    }

    private fun KSType.render(): String {
        val declarationName =
            declaration.qualifiedName?.asString()
                ?: (declaration as? KSTypeParameter)?.name?.asString()
                ?: declaration.simpleName.asString()
        val renderedArguments =
            arguments
                .takeIf { it.isNotEmpty() }
                ?.joinToString(prefix = "<", postfix = ">") { argument ->
                    val type = argument.type?.resolve()?.render() ?: "*"
                    when (argument.variance) {
                        Variance.COVARIANT -> "out $type"
                        Variance.CONTRAVARIANT -> "in $type"
                        else -> type
                    }
                }
                .orEmpty()
        return declarationName +
            renderedArguments +
            if (nullability == Nullability.NULLABLE) "?" else ""
    }
}

private enum class DeclarationKind(
    val qualifiedName: String,
    val hasTypeArgument: Boolean = true,
) {
    PROP("com.antepod.lumentika.component.Prop"),
    BINDING("com.antepod.lumentika.component.Binding"),
    EVENT("com.antepod.lumentika.component.Event"),
    SLOT("com.antepod.lumentika.component.Slot", false),
    SLOT_LIST("com.antepod.lumentika.component.SlotList", false);

    val isSlot: Boolean
        get() = this == SLOT || this == SLOT_LIST
}

private data class ComponentDeclaration(
    val name: String,
    val kind: DeclarationKind,
    val valueType: String?,
) {
    private val identifier: String
        get() = name.identifier()

    private val eventName: String
        get() = ("on" + name.replaceFirstChar { it.uppercase() }).identifier()

    fun parameter(): String =
        when (kind) {
            DeclarationKind.PROP,
            DeclarationKind.BINDING ->
                "$identifier: ComponentInput<${requireNotNull(valueType)}> = ComponentInput.Omitted"
            DeclarationKind.EVENT ->
                "$eventName: (${eventListenerType(requireNotNull(valueType))})? = null"
            DeclarationKind.SLOT,
            DeclarationKind.SLOT_LIST -> "$identifier: UiScope.() -> Unit = {}"
        }

    fun writeConfiguration(out: Appendable) {
        when (kind) {
            DeclarationKind.PROP,
            DeclarationKind.BINDING ->
                out.appendLine(
                    "    $identifier.applyTo(instance.$identifier, instance.componentScope)"
                )
            DeclarationKind.EVENT -> writeEventConfiguration(out)
            DeclarationKind.SLOT,
            DeclarationKind.SLOT_LIST ->
                out.appendLine("    instance.$identifier.configure($identifier)")
        }
    }

    private fun writeEventConfiguration(out: Appendable) {
        val type = requireNotNull(valueType)
        out.appendLine("    $eventName?.let { listener ->")
        if (type == "kotlin.Unit") {
            out.appendLine("        val handle = instance.$identifier.listen { listener() }")
        } else {
            out.appendLine("        val handle = instance.$identifier.listen(listener)")
        }
        out.appendLine("        withComponentScope(instance.componentScope) {")
        out.appendLine("            onCleanup { handle.close() }")
        out.appendLine("        }")
        out.appendLine("    }")
    }

    private fun eventListenerType(type: String): String =
        if (type == "kotlin.Unit") "() -> kotlin.Unit" else "($type) -> kotlin.Unit"
}

private fun String.identifier(): String = "`$this`"
