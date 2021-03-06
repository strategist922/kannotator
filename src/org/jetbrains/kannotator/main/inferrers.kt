package org.jetbrains.kannotator.main

import java.util.HashMap
import org.jetbrains.kannotator.annotationsInference.mutability.MutabilityAnnotation
import org.jetbrains.kannotator.annotationsInference.mutability.buildMutabilityAnnotations
import org.jetbrains.kannotator.annotationsInference.mutability.classNamesToMutabilityAnnotation
import org.jetbrains.kannotator.annotationsInference.nullability
import org.jetbrains.kannotator.annotationsInference.nullability.*
import org.jetbrains.kannotator.controlFlow.ControlFlowGraph
import org.jetbrains.kannotator.declarations.*
import org.jetbrains.kannotator.index.DeclarationIndex
import org.jetbrains.kannotator.index.FieldDependencyInfo
import org.jetbrains.kannotator.annotationsInference.mutability.MutabiltyLattice
import org.jetbrains.kannotator.annotationsInference.propagation.*

class NullabilityInferrer: AnnotationInferrer<NullabilityAnnotation> {
    private val methodToFieldNullabilityInfo = HashMap<Method, Map<Field, NullabilityValueInfo>>()

    override fun resolveAnnotation(classNames: Set<String>) = classNamesToNullabilityAnnotation(classNames)

    override fun inferAnnotationsFromFieldValue(field: Field): Annotations<NullabilityAnnotation> {
        val result = AnnotationsImpl<NullabilityAnnotation>()
        result.setIfNotNull(getFieldTypePosition(field), nullability.inferAnnotationsFromFieldValue(field: Field))
        return result
    }

    override fun inferAnnotationsFromMethod(
            method: Method,
            cfGraph: ControlFlowGraph,
            fieldDependencyInfoProvider: (Field) -> FieldDependencyInfo,
            declarationIndex: DeclarationIndex,
            annotations: Annotations<NullabilityAnnotation>): Annotations<NullabilityAnnotation> {
        val inferResult = buildMethodNullabilityAnnotations(
                method,
                cfGraph, fieldDependencyInfoProvider,
                declarationIndex,
                annotations,
                { m -> methodToFieldNullabilityInfo[m] })

        methodToFieldNullabilityInfo[method] = inferResult.writtenFieldValueInfos

        return inferResult.inferredAnnotations
    }


    override fun subsumes(
            position: AnnotationPosition,
            parentValue: NullabilityAnnotation,
            childValue: NullabilityAnnotation): Boolean {
        return NullabiltyLattice.subsumes(position.relativePosition, parentValue, childValue)
    }
}

public val MUTABILITY_INFERRER_OBJECT: AnnotationInferrer<MutabilityAnnotation> = MUTABILITY_INFERRER

object MUTABILITY_INFERRER: AnnotationInferrer<MutabilityAnnotation> {
    override fun resolveAnnotation(classNames: Set<String>) =
            classNamesToMutabilityAnnotation(classNames)

    override fun inferAnnotationsFromFieldValue(field: Field): Annotations<MutabilityAnnotation> =
            AnnotationsImpl<MutabilityAnnotation>()

    override fun inferAnnotationsFromMethod(
            method: Method,
            cfGraph: ControlFlowGraph,
            fieldDependencyInfoProvider: (Field) -> FieldDependencyInfo,
            declarationIndex: DeclarationIndex,
            annotations: Annotations<MutabilityAnnotation>
    ): Annotations<MutabilityAnnotation> {
        return buildMutabilityAnnotations(cfGraph, PositionsForMethod(method), declarationIndex, annotations)
    }


    override fun subsumes(position: AnnotationPosition, parentValue: MutabilityAnnotation, childValue: MutabilityAnnotation): Boolean {
        return MutabiltyLattice.subsumes(position.relativePosition, parentValue, childValue)
    }
}