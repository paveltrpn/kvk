package graph

import render.*

/**
 * StateGroup sits on top of scene subgraph, connects direct to locale object
 * and holds pipeline, shader set and other that bind on every traversal begin.
 */
open class StateGroup : Group {
    lateinit var program: FlatshadeProgram

    constructor() : super() {

    }

    constructor(program: FlatshadeProgram) : super() {
        this.program = program
    }

    override fun accept(visitor: Visitor): Unit {
        visitor.apply(this)
    }
}
