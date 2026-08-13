package com.antepod.lumentika.ksp

import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.validate

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
        val file = code.createNewFile(Dependencies(false, type.containingFile!!), pkg, "${name}Dsl")
        val factory = name.replaceFirstChar { it.lowercase() }
        file.writer().use { out ->
            out.appendLine("package $pkg")
                .appendLine()
                .appendLine("import com.antepod.lumentika.runtime.*")
                .appendLine("import com.antepod.lumentika.component.*")
                .appendLine("import com.antepod.lumentika.reactive.*")
                .appendLine()
                .appendLine("@UiDsl")
                .appendLine(
                    "public class ${name}Builder internal constructor(public val component: $name) {"
                )
            declarations.forEach { declaration -> declaration.writeTo(out) }
            out.appendLine("}")
                .appendLine()
                .appendLine(
                    "public fun UiScope.$factory(block: ${name}Builder.() -> Unit = {}): Element {"
                )
                .appendLine("    val component = $name()")
                .appendLine("    ${name}Builder(component).apply(block)")
                .appendLine("    return component.mount(this)")
                .appendLine("}")
        }
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

private enum class DeclarationKind(val qualifiedName: String, val hasTypeArgument: Boolean = true) {
    PROP("com.antepod.lumentika.component.Prop"),
    BINDING("com.antepod.lumentika.component.Binding"),
    EVENT("com.antepod.lumentika.component.Event"),
    SLOT("com.antepod.lumentika.component.Slot", false),
    SLOT_LIST("com.antepod.lumentika.component.SlotList", false),
}

private data class ComponentDeclaration(
    val name: String,
    val kind: DeclarationKind,
    val valueType: String?,
) {
    fun writeTo(out: Appendable) {
        when (kind) {
            DeclarationKind.PROP,
            DeclarationKind.BINDING -> writeValue(out)
            DeclarationKind.EVENT -> writeEvent(out)
            DeclarationKind.SLOT,
            DeclarationKind.SLOT_LIST -> writeSlot(out)
        }
    }

    private fun writeValue(out: Appendable) {
        val type = requireNotNull(valueType)
        out.appendLine("    public var $name: $type")
        out.appendLine("        get() = component.$name.value")
        out.appendLine("        set(value) { component.$name.set(value) }")
        out.appendLine()
        out.appendLine("    public fun $name(source: Readable<$type>) {")
        out.appendLine("        component.$name.source(source, component.componentScope)")
        out.appendLine("    }")
        out.appendLine()
        out.appendLine("    public fun $name(block: () -> $type) {")
        out.appendLine("        component.$name.source(component.componentScope, block)")
        out.appendLine("    }")
        if (kind == DeclarationKind.BINDING) {
            val bindName = "bind" + name.replaceFirstChar { it.uppercase() }
            out.appendLine()
            out.appendLine("    public fun $bindName(source: Mutable<$type>) {")
            out.appendLine("        component.$name.bind(source, component.componentScope)")
            out.appendLine("    }")
        }
        out.appendLine()
    }

    private fun writeEvent(out: Appendable) {
        val type = requireNotNull(valueType)
        val eventName = "on" + name.replaceFirstChar { it.uppercase() }
        out.appendLine("    public fun $eventName(listener: ($type) -> Unit) {")
        out.appendLine("        val handle = component.$name.listen(listener)")
        out.appendLine("        withComponentScope(component.componentScope) {")
        out.appendLine("            onCleanup { handle.close() }")
        out.appendLine("        }")
        out.appendLine("    }")
        out.appendLine()
    }

    private fun writeSlot(out: Appendable) {
        out.appendLine("    public fun $name(content: UiScope.() -> Unit) {")
        out.appendLine("        component.$name.configure(content)")
        out.appendLine("    }")
        out.appendLine()
    }
}
