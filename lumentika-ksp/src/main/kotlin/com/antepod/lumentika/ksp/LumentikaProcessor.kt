package com.antepod.lumentika.ksp

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*

public class LumentikaProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        LumentikaProcessor(environment.codeGenerator, environment.logger)
}

private class LumentikaProcessor(private val code: CodeGenerator, private val logger: KSPLogger) :
    SymbolProcessor {
    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols =
            resolver
                .getSymbolsWithAnnotation("com.antepod.lumentika.component.UIComponent")
                .filterIsInstance<KSClassDeclaration>()
        symbols.forEach(::generate)
        return emptyList()
    }

    private fun generate(type: KSClassDeclaration) {
        val pkg = type.packageName.asString()
        val name = type.simpleName.asString()
        val file = code.createNewFile(Dependencies(false, type.containingFile!!), pkg, "${name}Dsl")
        val factory = name.replaceFirstChar { it.lowercase() }
        file.writer().use { out ->
            out.appendLine("package $pkg")
                .appendLine()
                .appendLine("import com.antepod.lumentika.runtime.*")
                .appendLine("import com.antepod.lumentika.component.*")
                .appendLine()
                .appendLine(
                    "@UiDsl class ${name}Builder internal constructor(val component: $name)"
                )
                .appendLine(
                    "fun UiScope.$factory(block: ${name}Builder.() -> Unit = {}): Element {"
                )
                .appendLine("  val component = $name()")
                .appendLine("  ${name}Builder(component).apply(block)")
                .appendLine("  return component.mount(this)")
                .appendLine("}")
        }
    }
}
