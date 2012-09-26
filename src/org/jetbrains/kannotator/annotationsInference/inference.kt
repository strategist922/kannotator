package org.jetbrains.kannotator.annotationsInference

import org.jetbrains.kannotator.controlFlow.ControlFlowGraph
import org.jetbrains.kannotator.controlFlowBuilder.STATE_BEFORE
import org.jetbrains.kannotator.controlFlow.Value
import org.jetbrains.kannotator.controlFlow.State
import org.objectweb.asm.Opcodes.*
import org.jetbrains.kannotator.nullability.NullabilityValueInfo
import org.jetbrains.kannotator.nullability.NullabilityValueInfo.*
import org.jetbrains.kannotator.controlFlow.Instruction
import org.jetbrains.kannotator.controlFlowBuilder.AsmInstructionMetadata
import org.jetbrains.kannotator.nullability.merge
import javax.jnlp.SingleInstanceService
import org.objectweb.asm.tree.*
import java.util.LinkedHashMap
import org.jetbrains.kannotator.controlFlowBuilder.TypedValue
import org.jetbrains.kannotator.controlFlow.ControlFlowEdge
import kotlin.test.assertNull
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import java.util.HashMap

fun inferAnnotations(graph: ControlFlowGraph) : List<NullabilityValueInfo> {
    val parametersValueInfo = hashMap<Value, NullabilityValueInfo>()
    val returnValueInfo = arrayList<NullabilityValueInfo>()
    for (instruction in graph.instructions) {
        analyzeInstruction(instruction, parametersValueInfo, returnValueInfo)
    }
    val result = arrayList<NullabilityValueInfo>(returnValueInfo.merge())
    result.addAll(parametersValueInfo.values())
    return result
}

fun analyzeInstruction(
        instruction: Instruction,
        parametersValueInfo: MutableMap<Value, NullabilityValueInfo>,
        returnValueInfo: MutableList<NullabilityValueInfo>)
{
    val state = instruction[STATE_BEFORE]
    if (state == null) return

    val nullabilityInfosForInstruction = computeNullabilityInfosForInstruction(instruction, state)

    val instructionMetadata = instruction.metadata
    if (instructionMetadata is AsmInstructionMetadata) {
        when (instructionMetadata.asmInstruction.getOpcode()) {
            ARETURN -> {
                val valueSet = state.stack[0]
                val nullabilityValueInfo = valueSet
                        .map { it -> getNullabilityInfo(nullabilityInfosForInstruction, it) }.merge()
                returnValueInfo.add(nullabilityValueInfo)
            }
            INVOKEVIRTUAL, INVOKEINTERFACE, INVOKEDYNAMIC -> {
                val valueSet = state.stack[0]
                for (value in valueSet) {
                    if (value.interesting) {
                        val info = getNullabilityInfo(nullabilityInfosForInstruction, value)
                        if (info == CONFLICT || info == NULL) {
                            parametersValueInfo[value] = CONFLICT
                        }
                        else if (info != NOT_NULL) { // TODO consider other cases
                            parametersValueInfo[value] = NOT_NULL
                        }
                    }
                }
            }
            else -> Unit.VALUE
        }
    }
}

val nullabilityInfosForEdges : MutableMap<ControlFlowEdge, Map<Value, NullabilityValueInfo>> = hashMap()

fun getNullabilityInfo(nullabilityInfos: Map<Value, NullabilityValueInfo>, value: Value) : NullabilityValueInfo {
    val initialInfo = getInitialNullabilityInfo(value)
    val currentInfo = nullabilityInfos[value]
    return if (currentInfo == null) initialInfo else currentInfo
}

fun computeNullabilityInfosForInstruction(instruction: Instruction, state: State<Unit>) : Map<Value, NullabilityValueInfo> {
    val result = hashMap<Value, NullabilityValueInfo>()

    // merge from incoming edges
    for (incomingEdge in instruction.incomingEdges) {
        val incomingEdgeMap: Map<Value, NullabilityValueInfo>? = nullabilityInfosForEdges[incomingEdge]
        if (incomingEdgeMap == null) continue
        for ((value, info) in incomingEdgeMap) {
            val currentInfo = result[value]
            result[value] = if (currentInfo == null) info else info merge currentInfo
        }

    }

    val instructionMetadata = instruction.metadata
    if (instructionMetadata is AsmInstructionMetadata) {
        val opcode: Int = instructionMetadata.asmInstruction.getOpcode()
        when (opcode) {
            IFNULL, IFNONNULL -> {
                // first outgoing edge is 'false', second is 'true'
                // this order is is provided by code in ASM's Analyzer
                val it = instruction.outgoingEdges.iterator()
                val (falseEdge, trueEdge) = Pair(it.next(), it.next())
                assertFalse(it.hasNext()) // should be exactly two edges!

                val (nullEdge, notNullEdge) = if (opcode == IFNULL)
                                              Pair(trueEdge, falseEdge)
                                              else Pair(falseEdge, trueEdge)

                val nullMap = HashMap(result)
                for (value in state.stack[0]) {
                    val wasInfo = result[value]
                    nullMap[value] = if (wasInfo == CONFLICT || wasInfo == NOT_NULL) CONFLICT else NULL
                }
                assertNull(nullabilityInfosForEdges[nullEdge])
                nullabilityInfosForEdges[nullEdge] = nullMap

                val notNullMap = HashMap(result)
                for (value in state.stack[0]) {
                    val wasInfo = result[value]
                    notNullMap[value] = if (wasInfo == CONFLICT || wasInfo == NULL) CONFLICT else NOT_NULL
                }
                assertNull(nullabilityInfosForEdges[notNullEdge])
                nullabilityInfosForEdges[notNullEdge] = notNullMap
            }
            else -> {
                // propagate to outgoing edges as is
                for (outgoingEdge in instruction.outgoingEdges) {
                    assertNull(nullabilityInfosForEdges[outgoingEdge])
                    nullabilityInfosForEdges[outgoingEdge] = result
                }
            }
        }
    }
    else {
        // propagate to outgoing edges as is
        for (outgoingEdge in instruction.outgoingEdges) {
            assertNull(nullabilityInfosForEdges[outgoingEdge])
            nullabilityInfosForEdges[outgoingEdge] = result
        }
    }

    return result
}

fun getInitialNullabilityInfo(value: Value): NullabilityValueInfo {
    val typedValue = value as TypedValue
    val createdAtInsn = typedValue.createdAtInsn

    // TODO this is a hack, must create value info depending on current command type
    if (value == org.jetbrains.kannotator.controlFlowBuilder.NULL_VALUE) {
        return NULL
    }
    if (createdAtInsn == null) return UNKNOWN // todo parameter
    return when (createdAtInsn.getOpcode()) {
        NEW -> NOT_NULL
        ACONST_NULL -> NULL
        else -> UNKNOWN
    }
}