package org.jetbrains.kannotator.annotationsInference.mutability


val mutableInterfaces: Map<String, List<String>> = hashMap(
        "java.util.Collection" to arrayList("add", "remove", "addAll", "removeAll", "retainAll", "clear"),
        "java.util.List" to arrayList("add", "remove", "addAll", "removeAll", "retainAll", "clear", "set", "add", "remove"),
        "java.util.Set" to arrayList("add", "remove", "AddAll", "removeAll", "retainAll", "clear"),
        "java.util.Map" to arrayList("put", "remove", "putAll", "clear"),
        "java.util.Map\$Entry" to arrayList("setValue"),
        "java.util.Iterator" to arrayList("remove")
)

val propagatingMutability: Map<String, List<String>> = hashMap(
        "java.util.Collection" to arrayList("iterator"),
        "java.util.List" to arrayList("iterator", "listIterator"),
        "java.util.Set" to arrayList("iterator"),
        "java.util.Map" to arrayList("keySet", "values", "entrySet")
)